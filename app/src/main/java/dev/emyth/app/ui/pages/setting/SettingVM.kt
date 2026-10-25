/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.emyth.app.data.datastore.Settings
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.ai.mcp.McpManager

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun addCustomTheme(theme: dev.emyth.app.ui.theme.CustomTheme) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(customThemes = settings.customThemes + theme)
            }
        }
    }

    fun updateCustomTheme(theme: dev.emyth.app.ui.theme.CustomTheme) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    customThemes = settings.customThemes.map {
                        if (it.id == theme.id) theme else it
                    }
                )
            }
        }
    }

    fun deleteCustomTheme(themeId: String) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                val ns = settings.copy(customThemes = settings.customThemes.filter { it.id != themeId })
                if (settings.themeId == themeId) ns.copy(themeId = dev.emyth.app.ui.theme.PresetThemes[0].id) else ns
            }
        }
    }
}
