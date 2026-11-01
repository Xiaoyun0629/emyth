/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.sync.importer

import android.database.sqlite.SQLiteDatabase
import me.rerere.ai.ui.UIMessage
import dev.emyth.app.data.model.Conversation
import dev.emyth.app.data.model.MessageNode
import dev.emyth.app.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlin.uuid.Uuid

/**
 * 从 RikkaHub 原版的备份 zip 导入聊天记录。
 *
 * RikkaHub 的备份是 zip，内部包含 Room 管理的 SQLite（`rikka_hub.db`）+ `settings.json` 等。
 * 这里只迁移聊天记录（会话 + 消息节点），不迁移 provider 密钥、记忆等。
 *
 * 关键事实：
 * - `message_node` 表结构与本项目（Emyth）完全一致，`messages` 列存的是 `List<UIMessage>` 的 JSON；
 * - `ConversationEntity` 表比 Emyth 多 4 个字段（mode_injection_ids / lorebook_ids / workspace_cwd / config），
 *   其余列一致；导入时只取需要的列。
 * - 因为两边都源自同一个 Message 模型，`messages` JSON 可直接用 JsonInstant 反序列化（ignoreUnknownKeys=true）。
 */
object RikkahubImporter {

    fun import(file: File, assistantId: Uuid): RikkahubImportPayload {
        val dbFile = extractDatabase(file)
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            RikkahubImportPayload(importConversations(db, assistantId))
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    private fun extractDatabase(zipFile: File): File {
        val out = File.createTempFile("rikkahub_import_", ".db")
        ZipInputStream(zipFile.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith("rikka_hub.db")) {
                    FileOutputStream(out).use { fos -> zin.copyTo(fos) }
                    break
                }
                entry = zin.nextEntry
            }
        }
        if (out.length() == 0L) {
            out.delete()
            throw IllegalArgumentException("备份里没找到 rikka_hub.db，请确认这是 RikkaHub 的备份")
        }
        return out
    }

    private fun importConversations(db: SQLiteDatabase, assistantId: Uuid): List<Conversation> {
        // 1. 读所有 message_node，按 conversation_id 分组，反序列化 messages
        val nodesByConversation = mutableMapOf<String, MutableList<MessageNode>>()
        db.rawQuery(
            "SELECT id, conversation_id, node_index, messages, select_index FROM message_node ORDER BY conversation_id, node_index",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val nodeId = cursor.getString(0)
                val conversationId = cursor.getString(1)
                val messagesJson = cursor.getString(3)
                val selectIndex = cursor.getInt(4)

                val messages = runCatching {
                    JsonInstant.decodeFromString<List<UIMessage>>(messagesJson)
                }.getOrElse { emptyList() }

                if (messages.isEmpty()) continue

                nodesByConversation
                    .getOrPut(conversationId) { mutableListOf() }
                    .add(
                        MessageNode(
                            id = runCatching { Uuid.parse(nodeId) }.getOrElse { Uuid.random() },
                            messages = messages,
                            selectIndex = selectIndex.coerceIn(0, messages.size - 1),
                        ),
                    )
            }
        }

        // 2. 读会话元数据，拼装 Conversation
        val conversations = mutableListOf<Conversation>()
        db.rawQuery(
            "SELECT id, title, create_at, update_at, suggestions, is_pinned, custom_system_prompt, folder_id FROM ConversationEntity ORDER BY create_at",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val title = cursor.getString(1)
                val createAt = cursor.getLong(2)
                val updateAt = cursor.getLong(3)
                val suggestionsJson = cursor.getString(4)
                val isPinned = cursor.getInt(5) == 1
                val customSystemPrompt = cursor.getString(6)
                val folderId = cursor.getString(7)

                val nodes = nodesByConversation[id] ?: emptyList()
                if (nodes.isEmpty()) continue

                val suggestions = runCatching {
                    JsonInstant.decodeFromString<List<String>>(suggestionsJson)
                }.getOrElse { emptyList() }

                conversations.add(
                    Conversation(
                        id = runCatching { Uuid.parse(id) }.getOrElse { Uuid.random() },
                        assistantId = assistantId,
                        title = title,
                        messageNodes = nodes,
                        chatSuggestions = suggestions,
                        isPinned = isPinned,
                        createAt = Instant.ofEpochMilli(createAt),
                        updateAt = Instant.ofEpochMilli(updateAt),
                        customSystemPrompt = customSystemPrompt.takeIf { it.isNotBlank() },
                        folderId = folderId.takeIf { it.isNotBlank() }
                            ?.let { runCatching { Uuid.parse(it) }.getOrNull() },
                    ),
                )
            }
        }
        return conversations
    }
}

data class RikkahubImportPayload(
    val conversations: List<Conversation>,
)
