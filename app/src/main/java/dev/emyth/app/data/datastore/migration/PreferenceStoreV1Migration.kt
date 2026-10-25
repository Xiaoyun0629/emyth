/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.utils.JsonInstant

class PreferenceStoreV1Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 1
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        // 清理老的没有设置@SerialName的字段
        prefs[SettingsStore.MCP_SERVERS] = migrateMcpServersJson(prefs[SettingsStore.MCP_SERVERS] ?: "[]")

        // 更新版本
        prefs[SettingsStore.VERSION] = 1

        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

internal fun migrateMcpServersJson(json: String): String {
    val element = JsonInstant.parseToJsonElement(json).jsonArray.map { element ->
        val jsonObj = element.jsonObject.toMutableMap()
        val type = jsonObj["type"]?.jsonPrimitive?.content ?: ""
        when (type) {
            "dev.emyth.app.data.mcp.McpServerConfig.SseTransportServer" -> {
                jsonObj["type"] = JsonPrimitive("sse")
            }

            "dev.emyth.app.data.mcp.McpServerConfig.StreamableHTTPServer" -> {
                jsonObj["type"] = JsonPrimitive("streamable_http")
            }
        }
        JsonObject(jsonObj)
    }
    return JsonInstant.encodeToString(element)
}
