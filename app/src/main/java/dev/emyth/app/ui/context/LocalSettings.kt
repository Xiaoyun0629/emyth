/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import dev.emyth.app.data.ai.mcp.McpServerConfig
import dev.emyth.app.data.datastore.DisplaySetting
import dev.emyth.app.data.datastore.Settings
import dev.emyth.app.data.model.Assistant
import dev.emyth.app.data.model.QuickMessage

val LocalSettings = compositionLocalOf<Settings> {
    error("No SettingsStore provided")
}

val LocalDisplaySettings = compositionLocalOf<DisplaySetting> {
    error("LocalDisplaySettings not provided")
}

val LocalProviders = compositionLocalOf<List<ProviderSetting>> {
    error("LocalProviders not provided")
}

val LocalMcpServers = compositionLocalOf<List<McpServerConfig>> {
    error("LocalMcpServers not provided")
}

val LocalQuickMessages = compositionLocalOf<List<QuickMessage>> {
    error("LocalQuickMessages not provided")
}

val LocalCurrentAssistant = compositionLocalOf<Assistant> {
    error("LocalCurrentAssistant not provided")
}

val LocalCurrentChatModel = compositionLocalOf<Model?> {
    error("LocalCurrentChatModel not provided")
}
