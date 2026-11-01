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
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import dev.emyth.app.data.model.Assistant
import dev.emyth.app.data.model.Avatar
import dev.emyth.app.data.model.Conversation
import dev.emyth.app.data.model.MessageNode
import dev.emyth.app.utils.JsonInstant
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.time.Instant as KotlinInstant
import kotlin.uuid.Uuid

/**
 * 从 Kelivo 的备份 zip 导入聊天记录 + 供应商 + 助手（头像/壁纸）+ 记忆。
 *
 * Kelivo 备份（kelivo-backup v3）：manifest.json + settings.json + database/kelivo.db（drift SQLite）+ avatars/images 等目录。
 * - provider_rows：供应商（payload JSON，providerType=openai/claude/google，含 apiKey/baseUrl）
 * - assistant_rows：助手（payload JSON，含 name/avatar(String?)/systemPrompt/background）
 * - assistant_memory_rows / memory_entry_rows：记忆
 * - conversation_rows / message_rows / message_part_rows：聊天记录
 */
object KelivoImporter {

    fun import(file: File, assistantId: Uuid): KelivoImportPayload {
        val dbFile = extractEntry(file) { it.endsWith("kelivo.db") }
        val imageFiles = extractImages(file)

        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            KelivoImportPayload(
                conversations = importConversations(db, assistantId),
                providers = importProviders(db),
                assistants = importAssistants(db, imageFiles),
                memories = importMemories(db),
            )
        } finally {
            db.close()
            dbFile.delete()
            imageFiles.values.forEach { it.delete() }
        }
    }

    // ---- 供应商 ----

    private fun importProviders(db: SQLiteDatabase): List<ProviderSetting> {
        val providers = mutableListOf<ProviderSetting>()
        db.rawQuery("SELECT payload FROM provider_rows", null).use { cursor ->
            while (cursor.moveToNext()) {
                val payload = cursor.getString(0)
                val obj = runCatching { JsonInstant.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val apiKey = obj["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
                val baseUrl = obj["baseUrl"]?.jsonPrimitive?.contentOrNull ?: ""
                val type = obj["providerType"]?.jsonPrimitive?.contentOrNull ?: "openai"
                if (apiKey.isBlank() && baseUrl.isBlank()) continue
                val provider = when (type) {
                    "claude", "anthropic" -> ProviderSetting.Claude(
                        name = name,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                    )
                    "google", "gemini" -> ProviderSetting.Google(
                        name = name,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                    )
                    else -> ProviderSetting.OpenAI(
                        name = name,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                    )
                }
                providers.add(provider)
            }
        }
        return providers
    }

    // ---- 助手（头像/壁纸） ----

    private fun importAssistants(db: SQLiteDatabase, imageFiles: Map<String, File>): List<Assistant> {
        val assistants = mutableListOf<Assistant>()
        db.rawQuery("SELECT payload FROM assistant_rows ORDER BY sort_order", null).use { cursor ->
            while (cursor.moveToNext()) {
                val payload = cursor.getString(0)
                val obj = runCatching { JsonInstant.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val avatarStr = obj["avatar"]?.jsonPrimitive?.contentOrNull
                val systemPrompt = obj["systemPrompt"]?.jsonPrimitive?.contentOrNull ?: ""
                val background = obj["background"]?.jsonPrimitive?.contentOrNull

                val avatar = parseAvatar(avatarStr, imageFiles)
                val resolvedBackground = background?.let { resolveImageUrl(it, imageFiles) }

                assistants.add(
                    Assistant(
                        name = name,
                        avatar = avatar,
                        systemPrompt = systemPrompt,
                        background = resolvedBackground,
                    ),
                )
            }
        }
        return assistants
    }

    private fun parseAvatar(avatarStr: String?, imageFiles: Map<String, File>): Avatar {
        if (avatarStr.isNullOrBlank()) return Avatar.Dummy
        // 可能是 emoji（首字母/emoji 头像）
        if (avatarStr.length <= 8 && !avatarStr.startsWith("data:") && !avatarStr.startsWith("http") && !avatarStr.contains('/')) {
            return Avatar.Emoji(avatarStr)
        }
        val url = resolveImageUrl(avatarStr, imageFiles)
        return Avatar.Image(url)
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
        // assistant_memory_rows.payload 是 {id, assistantId, content}
        db.rawQuery("SELECT payload FROM assistant_memory_rows", null).use { cursor ->
            while (cursor.moveToNext()) {
                val payload = cursor.getString(0)
                val obj = runCatching { JsonInstant.parseToJsonElement(payload).jsonObject }.getOrNull()
                val content = obj?.get("content")?.jsonPrimitive?.contentOrNull
                if (!content.isNullOrBlank()) memories.add(content)
            }
        }
        // memory_entry_rows.content 是直接文本
        db.rawQuery("SELECT content FROM memory_entry_rows WHERE status = 'active'", null).use { cursor ->
            while (cursor.moveToNext()) {
                val content = cursor.getString(0)
                if (content.isNotBlank()) memories.add(content)
            }
        }
        return memories.distinct()
    }

    // ---- 聊天记录 ----

    private fun importConversations(db: SQLiteDatabase, assistantId: Uuid): List<Conversation> {
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

    // ---- 工具 ----

    private fun extractEntry(zipFile: File, predicate: (String) -> Boolean): File {
        val out = File.createTempFile("kelivo_import_", ".db")
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
            throw IllegalArgumentException("备份里没找到 kelivo.db，请确认这是 Kelivo 的备份")
        }
        return out
    }

    private fun extractImages(zipFile: File): Map<String, File> {
        val images = mutableMapOf<String, File>()
        ZipInputStream(zipFile.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && name.isImageEntry()) {
                    val fileName = name.substringAfterLast('/')
                    val tmp = File.createTempFile("kelivo_img_", fileName.substringAfterLast('.').let { ".$it" })
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
    val providers: List<ProviderSetting>,
    val assistants: List<Assistant>,
    val memories: List<String>,
)
