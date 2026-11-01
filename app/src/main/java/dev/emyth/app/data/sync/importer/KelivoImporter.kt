/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.sync.importer

import android.database.sqlite.SQLiteDatabase
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
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
 * Kelivo 新版备份（kelivo-backup v2/v3）：
 * - `database/kelivo.db`（drift SQLite）：只存聊天记录（conversation_rows / message_rows / message_part_rows）
 * - `settings.json`：业务实体（助手/供应商/记忆/世界书/MCP 等），字段名带 `_v1` 后缀，值为 JSON 字符串：
 *   - `assistants_v1`：助手列表 JSON 字符串
 *   - `provider_configs_v1`：{providerId: config} JSON 字符串
 *   - `assistant_memories_v1`：记忆列表 JSON 字符串
 * - `avatars/ images/ upload/`：图片等文件，avatar/background 用绝对路径引用
 */
object KelivoImporter {

    fun import(file: File, assistantId: Uuid): KelivoImportPayload {
        val dbFile = extractEntry(file) { it.endsWith("kelivo.db") }
        val settingsJson = extractEntryText(file) { it.endsWith("settings.json") }
        val imageFiles = extractImages(file)

        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            val (providers, assistants, memories) = parseSettings(settingsJson, imageFiles)
            KelivoImportPayload(
                conversations = importConversations(db, assistantId),
                providers = providers,
                assistants = assistants,
                memories = memories,
            )
        } finally {
            db.close()
            dbFile.delete()
            imageFiles.values.forEach { it.delete() }
        }
    }

    // ---- settings.json（业务实体） ----

    private fun parseSettings(
        settingsJson: String?,
        imageFiles: Map<String, File>,
    ): Triple<List<ProviderSetting>, List<Assistant>, List<String>> {
        if (settingsJson.isNullOrBlank()) {
            return Triple(emptyList(), emptyList(), emptyList())
        }
        val root = runCatching { JsonInstant.parseToJsonElement(settingsJson).jsonObject }
            .getOrNull() ?: return Triple(emptyList(), emptyList(), emptyList())

        val providers = parseProviders(nestedJson(root["provider_configs_v1"]))
        val assistants = parseAssistants(nestedJson(root["assistants_v1"]), imageFiles)
        val memories = parseMemories(nestedJson(root["assistant_memories_v1"]))
        return Triple(providers, assistants, memories)
    }

    /** Kelivo 的 `_v1` 字段值是 JSON 字符串，这里解析成 JsonElement。 */
    private fun nestedJson(element: JsonElement?): JsonElement? {
        val primitive = element as? JsonPrimitive ?: return element
        val str = primitive.contentOrNull ?: return element
        return runCatching { JsonInstant.parseToJsonElement(str) }.getOrElse { element }
    }

    private fun parseProviders(element: kotlinx.serialization.json.JsonElement?): List<ProviderSetting> {
        val obj = element as? JsonObject ?: return emptyList()
        val providers = mutableListOf<ProviderSetting>()
        obj.forEach { (id, value) ->
            val cfg = value as? JsonObject ?: return@forEach
            val name = cfg["name"]?.jsonPrimitive?.contentOrNull ?: id
            val apiKey = cfg["apiKey"]?.jsonPrimitive?.contentOrNull ?: ""
            val baseUrl = cfg["baseUrl"]?.jsonPrimitive?.contentOrNull ?: ""
            val type = cfg["providerType"]?.jsonPrimitive?.contentOrNull ?: "openai"
            if (apiKey.isBlank() && baseUrl.isBlank()) return@forEach
            val provider = when (type) {
                "claude", "anthropic" -> ProviderSetting.Claude(name = name, baseUrl = baseUrl, apiKey = apiKey)
                "google", "gemini" -> ProviderSetting.Google(name = name, baseUrl = baseUrl, apiKey = apiKey)
                else -> ProviderSetting.OpenAI(name = name, baseUrl = baseUrl, apiKey = apiKey)
            }
            providers.add(provider)
        }
        return providers
    }

    private fun parseAssistants(element: kotlinx.serialization.json.JsonElement?, imageFiles: Map<String, File>): List<Assistant> {
        val array = element as? JsonArray ?: return emptyList()
        val assistants = mutableListOf<Assistant>()
        array.forEach { value ->
            val obj = value as? JsonObject ?: return@forEach
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val avatarStr = obj["avatar"]?.jsonPrimitive?.contentOrNull
            val systemPrompt = obj["systemPrompt"]?.jsonPrimitive?.contentOrNull ?: ""
            val background = obj["background"]?.jsonPrimitive?.contentOrNull
            assistants.add(
                Assistant(
                    name = name,
                    avatar = parseAvatar(avatarStr, imageFiles),
                    systemPrompt = systemPrompt,
                    background = background?.let { resolveImageUrl(it, imageFiles) },
                ),
            )
        }
        return assistants
    }

    private fun parseMemories(element: kotlinx.serialization.json.JsonElement?): List<String> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { value ->
            val obj = value as? JsonObject ?: return@mapNotNull null
            obj["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }.distinct()
    }

    private fun parseAvatar(avatarStr: String?, imageFiles: Map<String, File>): Avatar {
        if (avatarStr.isNullOrBlank()) return Avatar.Dummy
        // 短文本视为 emoji
        if (avatarStr.length <= 8 && !avatarStr.startsWith("data:") && !avatarStr.startsWith("http") && !avatarStr.contains('/')) {
            return Avatar.Emoji(avatarStr)
        }
        return Avatar.Image(resolveImageUrl(avatarStr, imageFiles))
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

    // ---- 聊天记录（database/kelivo.db） ----

    private fun importConversations(db: SQLiteDatabase, assistantId: Uuid): List<Conversation> {
        val partsByMessage = mutableMapOf<String, MutableList<UIMessagePart>>()
        runCatching {
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
        }

        val messagesByConversation = mutableMapOf<String, MutableList<UIMessage>>()
        runCatching {
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
        }

        val conversations = mutableListOf<Conversation>()
        runCatching {
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

    private fun extractEntryText(zipFile: File, predicate: (String) -> Boolean): String? {
        val out = File.createTempFile("kelivo_settings_", ".json")
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
