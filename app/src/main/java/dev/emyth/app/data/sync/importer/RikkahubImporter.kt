/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.sync.importer

import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import dev.emyth.app.data.model.Assistant
import dev.emyth.app.data.model.Avatar
import dev.emyth.app.data.model.Conversation
import dev.emyth.app.data.model.MessageNode
import dev.emyth.app.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipInputStream
import kotlin.uuid.Uuid

/**
 * 从 RikkaHub 原版的备份 zip 导入聊天记录 + 供应商 + 助手（头像/壁纸）+ 记忆。
 *
 * RikkaHub 的备份是 zip，内部：`rikka_hub.db`（Room SQLite）+ `settings.json`（Settings JSON）+ `upload/` 等目录。
 * 因为 RikkaHub 与 Emyth 同源：
 * - `message_node` 表结构一致，`messages` 列是 `List<UIMessage>` JSON；
 * - `settings.json` 是完整 Settings JSON，`providers` / `assistants` 字段类型相同（ProviderSetting / Assistant）；
 * - 头像壁纸图片在 `upload/` 目录，avatar/background 用 Uri 字符串引用。
 */
object RikkahubImporter {

    fun import(file: File, assistantId: Uuid): RikkahubImportPayload {
        val dbFile = extractEntry(file) { it.endsWith("rikka_hub.db") }
        val settingsJson = extractEntryText(file) { it.endsWith("settings.json") }
        val imageFiles = extractImages(file)

        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            val (providers, assistants) = parseSettings(settingsJson, imageFiles)
            RikkahubImportPayload(
                conversations = importConversations(db, assistantId),
                providers = providers,
                assistants = assistants,
                memories = importMemories(db),
            )
        } finally {
            db.close()
            dbFile.delete()
            imageFiles.values.forEach { it.delete() }
        }
    }

    // ---- settings.json ----

    private fun parseSettings(
        settingsJson: String?,
        imageFiles: Map<String, File>,
    ): Pair<List<ProviderSetting>, List<Assistant>> {
        if (settingsJson.isNullOrBlank()) return emptyList<ProviderSetting>() to emptyList<Assistant>()
        val root = runCatching { JsonInstant.parseToJsonElement(settingsJson).jsonObject }
            .getOrNull() ?: return emptyList<ProviderSetting>() to emptyList<Assistant>()

        val providers = root["providers"]?.let { element ->
            runCatching { JsonInstant.decodeFromString<List<ProviderSetting>>(element.toString()) }
                .getOrElse { emptyList() }
        } ?: emptyList()

        val assistants = root["assistants"]?.let { element ->
            runCatching { JsonInstant.decodeFromString<List<Assistant>>(element.toString()) }
                .getOrElse { emptyList() }
                ?.map { resolveAssistantImages(it, imageFiles) }
        } ?: emptyList()

        return providers to assistants
    }

    /** 把 assistant 的 avatar / background 里指向本地文件的引用，就地解析成 data URI。 */
    private fun resolveAssistantImages(assistant: Assistant, imageFiles: Map<String, File>): Assistant {
        val newAvatar = when (val avatar = assistant.avatar) {
            is Avatar.Image -> avatar.copy(url = resolveImageUrl(avatar.url, imageFiles))
            else -> avatar
        }
        val newBackground = assistant.background?.let { resolveImageUrl(it, imageFiles) }
        return assistant.copy(avatar = newAvatar, background = newBackground)
    }

    private fun resolveImageUrl(url: String, imageFiles: Map<String, File>): String {
        if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) return url
        val fileName = url.substringAfterLast('/').substringBefore('?')
        val file = imageFiles[fileName] ?: return url
        return runCatching {
            val bytes = file.readBytes()
            "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)
        }.getOrElse { url }
    }

    // ---- 记忆 ----

    private fun importMemories(db: SQLiteDatabase): List<String> {
        val memories = mutableListOf<String>()
        db.rawQuery("SELECT content FROM MemoryEntity", null).use { cursor ->
            while (cursor.moveToNext()) {
                val content = cursor.getString(0)
                if (content.isNotBlank()) memories.add(content)
            }
        }
        return memories
    }

    // ---- 聊天记录（同前） ----

    private fun importConversations(db: SQLiteDatabase, assistantId: Uuid): List<Conversation> {
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

    // ---- zip 解压工具 ----

    private fun extractEntry(zipFile: File, predicate: (String) -> Boolean): File {
        val out = File.createTempFile("rikkahub_import_", ".db")
        ZipInputStream(zipFile.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && predicate(entry.name)) {
                    FileOutputStream(out).use { fos -> zin.copyTo(fos) }
                    break
                }
                entry = zin.nextEntry
            }
        }
        if (out.length() == 0L) {
            out.delete()
            throw IllegalArgumentException("备份里没找到对应文件，请确认这是 RikkaHub 的备份")
        }
        return out
    }

    private fun extractEntryText(zipFile: File, predicate: (String) -> Boolean): String? {
        val out = File.createTempFile("rikkahub_settings_", ".json")
        ZipInputStream(zipFile.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && predicate(entry.name)) {
                    FileOutputStream(out).use { fos -> zin.copyTo(fos) }
                    break
                }
                entry = zin.nextEntry
            }
        }
        return if (out.length() == 0L) {
            out.delete()
            null
        } else {
            val text = out.readText()
            out.delete()
            text
        }
    }

    /** 提取备份里的图片文件（upload/avatars/images 等目录），按文件名索引。 */
    private fun extractImages(zipFile: File): Map<String, File> {
        val images = mutableMapOf<String, File>()
        ZipInputStream(zipFile.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && name.isImageEntry()) {
                    val fileName = name.substringAfterLast('/')
                    val tmp = File.createTempFile("rikkahub_img_", fileName.substringAfterLast('.').let { ".$it" })
                    FileOutputStream(tmp).use { fos -> zin.copyTo(fos) }
                    images[fileName] = tmp
                }
                entry = zin.nextEntry
            }
        }
        return images
    }

    private fun String.isImageEntry(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") || lower.endsWith(".gif")
    }
}

data class RikkahubImportPayload(
    val conversations: List<Conversation>,
    val providers: List<ProviderSetting>,
    val assistants: List<Assistant>,
    val memories: List<String>,
)
