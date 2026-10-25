/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import dev.emyth.app.ui.theme.presets.AutumnThemePreset
import dev.emyth.app.ui.theme.presets.BlackThemePreset
import dev.emyth.app.ui.theme.presets.ClaudeThemePreset
import dev.emyth.app.ui.theme.presets.OceanThemePreset
import dev.emyth.app.ui.theme.presets.SakuraThemePreset
import dev.emyth.app.ui.theme.presets.SpringThemePreset
import dev.emyth.app.ui.theme.presets.custom.PearlTideThemePreset

data class PresetTheme(
    val id: String,
    val name: @Composable () -> Unit,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
) {
    fun getColorScheme(dark: Boolean): ColorScheme {
        return if (dark) standardDark else standardLight
    }
}

val PresetThemes by lazy {
    listOf(
        SakuraThemePreset,
        OceanThemePreset,
        PearlTideThemePreset,
        SpringThemePreset,
        AutumnThemePreset,
        BlackThemePreset,
        ClaudeThemePreset,
    )
}

fun findPresetTheme(id: String): PresetTheme {
    return PresetThemes.find { it.id == id } ?: SakuraThemePreset
}
fun findThemeById(id: String, customThemes: List<CustomTheme>): PresetTheme? {
    PresetThemes.find { it.id == id }?.let { return it }
    val custom = customThemes.find { it.id == id } ?: return null
    return PresetTheme(
        id = custom.id,
        name = { androidx.compose.material3.Text(custom.name) },
        standardLight = custom.generateColorScheme(dark = false),
        standardDark = custom.generateColorScheme(dark = true),
    )
}
