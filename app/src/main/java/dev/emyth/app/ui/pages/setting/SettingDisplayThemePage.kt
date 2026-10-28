/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import dev.emyth.app.data.datastore.DisplayMaterialMode
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.emyth.app.R
import dev.emyth.app.Screen
import dev.emyth.app.data.datastore.DisplaySetting
import dev.emyth.app.ui.components.nav.BackButton
import dev.emyth.app.ui.components.ui.CardGroup
import dev.emyth.app.ui.context.LocalNavController
import dev.emyth.app.ui.hooks.rememberAmoledDarkMode
import dev.emyth.app.ui.pages.setting.components.PresetThemeButtonGroup
import dev.emyth.app.ui.theme.CustomColors
import dev.emyth.app.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingDisplayThemePage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var amoledDarkMode by rememberAmoledDarkMode()
    val navController = LocalNavController.current

    fun updateDisplaySetting(setting: DisplaySetting) {
        displaySetting = setting
        vm.updateSettings(settings.copy(displaySetting = setting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("主题外观") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_page_theme_setting),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                    )
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = 4.dp,
                                    bottomEnd = 4.dp
                                )
                            ),
                        headlineContent = { Text(stringResource(R.string.setting_page_dynamic_color)) },
                        supportingContent = { Text(stringResource(R.string.setting_page_dynamic_color_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = { vm.updateSettings(settings.copy(dynamicColor = it)) },
                            )
                        },
                        colors = CustomColors.listItemColors,
                    )
                    // Custom theme management entry
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { navController.navigate(Screen.SettingTheme) },
                        headlineContent = { Text("自定义主题管理") },
                        supportingContent = { Text("HCT 色彩算法自定义主题") },
                        colors = CustomColors.listItemColors,
                    )
                    if (!settings.dynamicColor) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceBright)
                        ) {
                            PresetThemeButtonGroup(
                                themeId = settings.themeId,
                                modifier = Modifier.fillMaxWidth(),
                                onChangeTheme = { vm.updateSettings(settings.copy(themeId = it)) }
                            )
                        }
                    }
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 20.dp,
                                    bottomEnd = 20.dp
                                )
                            ),
                        headlineContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_display_page_amoled_dark_mode_desc)) },
                        trailingContent = {
                            Switch(
                                checked = amoledDarkMode,
                                onCheckedChange = { amoledDarkMode = it }
                            )
                        },
                        colors = CustomColors.listItemColors,
                    )
                    // ===== 玻璃材质 =====
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                updateDisplaySetting(
                                    displaySetting.copy(
                                        materialMode = when (displaySetting.materialMode) {
                                            DisplayMaterialMode.FOLLOW_THEME -> DisplayMaterialMode.FLAT
                                            DisplayMaterialMode.FLAT -> DisplayMaterialMode.TRANSLUCENT
                                            DisplayMaterialMode.TRANSLUCENT -> DisplayMaterialMode.GLASS
                                            DisplayMaterialMode.GLASS -> DisplayMaterialMode.FOLLOW_THEME
                                        }
                                    )
                                )
                            },
                        headlineContent = { Text("界面材质") },
                        supportingContent = {
                            Text(
                                when (displaySetting.materialMode) {
                                    DisplayMaterialMode.FOLLOW_THEME -> "跟随主题"
                                    DisplayMaterialMode.FLAT -> "纯色"
                                    DisplayMaterialMode.TRANSLUCENT -> "磨砂玻璃"
                                    DisplayMaterialMode.GLASS -> "液态玻璃"
                                }
                            )
                        },
                        colors = CustomColors.listItemColors,
                    )
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        headlineContent = { Text("界面实时渲染") },
                        supportingContent = { Text("为玻璃界面启用实时背景渲染") },
                        trailingContent = {
                            Switch(
                                checked = displaySetting.interfaceRealtimeRendering,
                                onCheckedChange = {
                                    updateDisplaySetting(displaySetting.copy(interfaceRealtimeRendering = it))
                                },
                            )
                        },
                        colors = CustomColors.listItemColors,
                    )
                    if (displaySetting.interfaceRealtimeRendering) {
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            headlineContent = { Text("聊天气泡实时模糊") },
                            supportingContent = { Text("为聊天气泡实时渲染背景模糊") },
                            trailingContent = {
                                Switch(
                                    checked = displaySetting.chatBubbleRealtimeBlur,
                                    onCheckedChange = {
                                        updateDisplaySetting(displaySetting.copy(chatBubbleRealtimeBlur = it))
                                    },
                                )
                            },
                            colors = CustomColors.listItemColors,
                        )
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            headlineContent = { Text("模糊强度") },
                            supportingContent = {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(
                                        value = displaySetting.interfaceBlurRadius.coerceIn(3f, 20f),
                                        onValueChange = {
                                            updateDisplaySetting(displaySetting.copy(interfaceBlurRadius = it))
                                        },
                                        valueRange = 3f..20f,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${displaySetting.interfaceBlurRadius.coerceIn(3f, 20f).roundToInt()} dp")
                                }
                            },
                            colors = CustomColors.listItemColors,
                        )
                    }
                }
            }
        }
    }
}