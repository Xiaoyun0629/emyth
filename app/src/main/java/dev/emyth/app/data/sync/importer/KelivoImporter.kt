/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.sync.importer

import android.database.sqlite.SQLiteDatabase
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import dev.emyth.app.data.model.Conversation
import dev.emyth.app.data.model.MessageNode
import dev.emyth.app.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.time.Instant as KotlinInstant
import kotlin.uuid.Uuid

/**
 * 从 Kelivo 的备份 zip 导入聊天记录。
 *
 * Kelivo 的备份是 zip，格式标识 `kelivo-backup`，内部结构：
 * - `manifest.json`（清单）
 * - `settings.json`（业务设置）
 * - `database/kelivo.db`（drift 管理的 SQLite，核心）
 * - 可选文件根：upload/ images/ avatars/ fonts/ skills/ workspaces/ sessions/
 *
 * 数据模型（drift，表名/列名均为 snake_case）：
 * - `conversation_rows`：会话
 * - `message_rows`：消息（一行 = 一条消息的元信息）
 * - `message_part_rows`：消息分片（一行 = 一个 part，按 ordinal 排序），
 *   kind ∈ text / reasoning / tool_call / image / file / assistant_round_end / provider_auth_error
 *
 * 与 Emyth 的差异：kelivo 的消息是「MessageRows 一行 + 多个 MessagePartRows 分片」，
 * 需要组装成 Emyth 的 `UIMessage.parts`。时间戳用微秒（MicrosecondDateTimeConverter）。
 */
object KelivoImporter {

    fun import(file: File, assistantId: Uuid): KelivoImportPayload {
        val dbFile = extractDatabase(file)
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            KelivoImportPayload(importConversations(db, assistantId))
        } finally {
            db.close()
            dbFile.delete()
        }
    }

    private fun extractDatabase(zipFile: File): File {
        val out = File.createTempFile("kelivo_import_", ".db")
        ZipInputStream(zipFile.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith("kelivo.db")) {
                    FileOutputStream(out).use { fos -> zin.copyTo(fos) }
                    break
                }
                entry = zin.nextEntry
            }
        }
        if (out.length() == 0L) {
            out.delete()
            throw IllegalArgumentException("备份里没找到 kelivo.db，请确认这是 Kelivo 的备份")
        }
        return out
    }

    private fun importConversations(db: SQLiteDatabase, assistantId: Uuid): List<Conversation> {
        // 1. 读所有 part，按 revision_id（消息 id）分组，按 ordinal 排序
        val partsByMessage = mutableMapOf<String, MutableList<UIMessagePart>>()
        db.rawQuery(
            "SELECT revision_id, ordinal, kind, payload FROM message_part_rows ORDER BY revision_id, ordinal",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val revisionId = cursor.getString(0)
                val kind = cursor.getString(2)
                val payload = cursor.getString(3)
                val part = parsePart(kind, payload) ?: continue
                partsByMessage.getOrPut(revisionId) { mutableListOf() }.add(part)
            }
        }

        // 2. 读所有消息，组装 UIMessage，按 conversation_id 分组
        val messagesByConversation = mutableMapOf<String, MutableList<UIMessage>>()
        db.rawQuery(
            "SELECT id, conversation_id, role, timestamp, total_tokens, prompt_tokens, completion_tokens FROM message_rows ORDER BY conversation_id, message_order",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val conversationId = cursor.getString(1)
                val role = cursor.getString(2).toMessageRole() ?: continue
                val timestampMicros = cursor.getLong(3)
                val parts = partsByMessage[id] ?: emptyList()
                if (parts.isEmpty() && role != MessageRole.SYSTEM) continue

                val totalTokens = cursor.getLong(4)
                val promptTokens = cursor.getLong(5)
                val completionTokens = cursor.getLong(6)
                val usage = if (totalTokens > 0 || promptTokens > 0 || completionTokens > 0) {
                    TokenUsage(
                        promptTokens = promptTokens.toInt(),
                        completionTokens = completionTokens.toInt(),
                        totalTokens = totalTokens.toInt(),
                    )
                } else null

                messagesByConversation.getOrPut(conversationId) { mutableListOf() }.add(
                    UIMessage(
                        id = stableUuid("kelivo:message:$id"),
                        role = role,
                        parts = parts,
                        createdAt = microsToLocalDateTime(timestampMicros),
                        usage = usage,
                    ),
                )
            }
        }

        // 3. 读会话，拼装 Conversation
        val conversations = mutableListOf<Conversation>()
        db.rawQuery(
            "SELECT id, title, created_at, updated_at, is_pinned FROM conversation_rows ORDER BY created_at",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val title = cursor.getString(1)
                val createdAtMicros = cursor.getLong(2)
                val updatedAtMicros = cursor.getLong(3)
                val isPinned = cursor.getInt(4) == 1

                val messages = messagesByConversation[id] ?: emptyList()
                if (messages.isEmpty()) continue

                // 每条消息一个 MessageNode（与 ChatboxImporter 的处理一致）
                val nodes = messages.mapIndexed { index, message ->
                    MessageNode(
                        id = stableUuid("kelivo:node:$id:$index"),
                        messages = listOf(message),
                        selectIndex = 0,
                    )
                }

                conversations.add(
                    Conversation(
                        id = stableUuid("kelivo:session:$id"),
                        assistantId = assistantId,
                        title = title.ifBlank { "导入的对话" },
                        messageNodes = nodes,
                        createAt = Instant.ofEpochMilli(createdAtMicros / 1000),
                        updateAt = Instant.ofEpochMilli(updatedAtMicros / 1000),
                    ),
                )
            }
        }
        return conversations
    }

    private fun parsePart(kind: String, payload: String): UIMessagePart? = when (kind) {
        "text" -> payload.takeIf { it.isNotBlank() }?.let { UIMessagePart.Text(it) }
        "reasoning" -> payload.takeIf { it.isNotBlank() }?.let { UIMessagePart.Reasoning(it) }
        "tool_call" -> parseToolPart(payload)
        "image" -> parseImagePart(payload)
        "file" -> parseFilePart(payload)
        // assistant_round_end / provider_auth_error / unknown 直接忽略
        else -> null
    }

    private fun parseToolPart(payload: String): UIMessagePart.Tool? {
        val obj = runCatching { JsonInstant.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val toolName = obj["name"]?.jsonPrimitive?.contentOrNull
            ?: obj["toolName"]?.jsonPrimitive?.contentOrNull ?: ""
        val toolCallId = obj["id"]?.jsonPrimitive?.contentOrNull
            ?: obj["toolCallId"]?.jsonPrimitive?.contentOrNull ?: ""
        if (toolName.isBlank() && toolCallId.isBlank()) return null
        return UIMessagePart.Tool(
            toolCallId = toolCallId,
            toolName = toolName,
            input = payload,
            output = emptyList(),
        )
    }

    private fun parseImagePart(payload: String): UIMessagePart.Image? {
        val obj = runCatching { JsonInstant.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: return null
        if (uri.isBlank()) return null
        return UIMessagePart.Image(url = uri)
    }

    private fun parseFilePart(payload: String): UIMessagePart.Document? {
        val obj = runCatching { JsonInstant.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "file"
        val mime = obj["mime"]?.jsonPrimitive?.contentOrNull ?: "text/*"
        if (uri.isBlank()) return null
        return UIMessagePart.Document(url = uri, fileName = name, mime = mime)
    }

    private fun String.toMessageRole(): MessageRole? = when (this) {
        "system" -> MessageRole.SYSTEM
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        "tool" -> MessageRole.TOOL
        else -> null
    }

    private fun microsToLocalDateTime(micros: Long) =
        KotlinInstant.fromEpochMilliseconds(micros / 1000)
            .toLocalDateTime(TimeZone.currentSystemDefault())

    private fun stableUuid(value: String): Uuid =
        Uuid.parse(UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString())
}

data class KelivoImportPayload(
    val conversations: List<Conversation>,
)
