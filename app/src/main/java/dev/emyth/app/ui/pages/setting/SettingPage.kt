/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiMagic
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.CloudServer
import me.rerere.hugeicons.stroke.Book01
import me.rerere.hugeicons.stroke.Book03
import me.rerere.hugeicons.stroke.Bookshelf01
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Clapping01
import me.rerere.hugeicons.stroke.Database02
import me.rerere.hugeicons.stroke.Developer
import me.rerere.hugeicons.stroke.GlobalSearch
import me.rerere.hugeicons.stroke.ImageUpload
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LookTop
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Pulse01
import me.rerere.hugeicons.stroke.ServerStack01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Shield02
import me.rerere.hugeicons.stroke.SmartPhone01
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.Sun01
import me.rerere.hugeicons.stroke.WavingHand01
import me.rerere.hugeicons.stroke.MessageMultiple01
import me.rerere.hugeicons.stroke.Message01
import dev.emyth.app.R
import dev.emyth.app.Screen
import dev.emyth.app.data.datastore.isNotConfigured
import dev.emyth.app.data.files.FilesManager
import dev.emyth.app.ui.components.nav.BackButton
import dev.emyth.app.ui.components.ui.CardGroup
import dev.emyth.app.ui.components.ui.Select
import dev.emyth.app.ui.context.LocalNavController
import dev.emyth.app.ui.context.Navigator
import dev.emyth.app.ui.hooks.rememberColorMode
import dev.emyth.app.ui.theme.ColorMode
import dev.emyth.app.ui.theme.CustomColors
import dev.emyth.app.utils.openUrl
import dev.emyth.app.utils.plus
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filesManager: FilesManager = koinInject()

    if (settings.launchCount > 100 && (settings.launchCount - settings.sponsorAlertDismissedAt) >= 50) {
        AlertDialog(
            onDismissRequest = {
                vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
            },
            icon = { Icon(HugeIcons.WavingHand01, null) },
            title = { Text(stringResource(R.string.setting_page_sponsor_alert_title)) },
            text = { Text(stringResource(R.string.setting_page_sponsor_alert_desc)) },
            confirmButton = {
                Button(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                    navController.navigate(Screen.SettingDonate)
                }) {
                    Text(stringResource(R.string.setting_page_sponsor_alert_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.updateSettings(settings.copy(sponsorAlertDismissedAt = settings.launchCount))
                }) {
                    Text(stringResource(R.string.setting_page_sponsor_alert_dismiss))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                actions = {
                },
                colors = CustomColors.topBarColors
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("settingSearch") {
                SettingSearchBar(navController)
            }

            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(navController)
                }
            }

            item("generalSettings") {
                var colorMode by rememberColorMode()
                val selectedColorModeText = when (colorMode) {
                    ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                    ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                    ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_general_settings)) },
                ) {
                    item(
                        leadingContent = { Icon(HugeIcons.Sun01, null) },
                        trailingContent = {
                            Select(
                                options = ColorMode.entries,
                                selectedOption = colorMode,
                                onOptionSelected = {
                                    colorMode = it
                                    navController.navigate(Screen.Setting) {
                                        popUpTo(Screen.Setting) {
                                            inclusive = true
                                        }
                                    }
                                },
                                optionToString = {
                                    when (it) {
                                        ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                        ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                        ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                    }
                                },
                                modifier = Modifier.width(150.dp)
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_color_mode)) },
                        supportingContent = { Text(selectedColorModeText) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingDisplay) },
                        leadingContent = { Icon(HugeIcons.Settings03, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_display_setting_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_display_setting)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingPlugins) },
                        leadingContent = { Icon(HugeIcons.Package, null) },
                        supportingContent = { Text("管理本地插件，导入ZIP插件包") },
                        headlineContent = { Text("插件管理") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.VoiceFavorite) },
                        leadingContent = { Icon(HugeIcons.Mic01, null) },
                        supportingContent = { Text("收藏的 AI 语音，随时回听") },
                        headlineContent = { Text("语音收藏") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSecurity) },
                        leadingContent = { Icon(HugeIcons.Shield02, null) },
                        supportingContent = { Text("工具调用确认、自动批准、工作流拦截等安全选项") },
                        headlineContent = { Text("安全设置") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Assistant) },
                        leadingContent = { Icon(HugeIcons.LookTop, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_assistant)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Extensions) },
                        leadingContent = { Icon(HugeIcons.Package, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_extensions_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_extensions)) },
                    )
                }
            }

            item("modelServices") {
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_model_and_services)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingModels) },
                        leadingContent = { Icon(HugeIcons.AiMagic, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_default_model)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingProvider) },
                        leadingContent = { Icon(HugeIcons.Brain02, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_providers_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_providers)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSearch) },
                        leadingContent = { Icon(HugeIcons.GlobalSearch, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_search_service)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSpeech) },
                        leadingContent = { Icon(HugeIcons.Megaphone01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_tts_service)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingMcp) },
                        leadingContent = { Icon(HugeIcons.McpServer, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_mcp)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingWeb) },
                        leadingContent = { Icon(HugeIcons.ServerStack01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_web_server_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_web_server)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingSystemTools) },
                        leadingContent = { Icon(HugeIcons.SmartPhone01, null) },
                        supportingContent = { Text("位置、通知、日历、闹钟等系统工具") },
                        headlineContent = { Text("系统工具") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingVoiceWake) },
                        leadingContent = { Icon(HugeIcons.Mic01, null) },
                        supportingContent = { Text("喊唤醒词，弹出助手并播放回应音频") },
                        headlineContent = { Text("语音唤醒") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingProactiveMessage) },
                        leadingContent = { Icon(HugeIcons.WavingHand01, null) },
                        supportingContent = { Text("AI 在设定间隔内主动发消息，有记忆有上下文") },
                        headlineContent = { Text("主动消息") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingWeixinBot) },
                        leadingContent = { Icon(HugeIcons.MessageMultiple01, null) },
                        supportingContent = { Text("把微信号变成 AI 入口，扫码登录后用微信收发消息") },
                        headlineContent = { Text("微信 Bot") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingQqBot) },
                        leadingContent = { Icon(HugeIcons.Message01, null) },
                        supportingContent = { Text("填 AppID/Secret，用 QQ 私聊跟 AI 对话") },
                        headlineContent = { Text("QQ Bot") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Workflows) },
                        leadingContent = { Icon(HugeIcons.SmartPhone01, null) },
                        supportingContent = { Text("Tasker 风格自动化：触发器 + 条件 -> 执行动作，由 AI 编写") },
                        headlineContent = { Text("工作流") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Health) },
                        leadingContent = { Icon(HugeIcons.Pulse01, null) },
                        supportingContent = { Text("Gadgetbridge 健康数据查看") },
                        headlineContent = { Text("健康数据") },
                    )
                }
            }

            item("dataSettings") {
                val storageState by produceState(-1 to 0L) {
                    value = filesManager.countChatFiles()
                }
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_data_settings)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.Backup) },
                        leadingContent = { Icon(HugeIcons.Database02, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_data_backup)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingFiles) },
                        leadingContent = { Icon(HugeIcons.ImageUpload, null) },
                        supportingContent = {
                            if (storageState.first == -1) {
                                Text(stringResource(R.string.calculating))
                            } else {
                                Text(
                                    stringResource(
                                        R.string.setting_page_chat_storage_desc,
                                        storageState.first,
                                        storageState.second / 1024 / 1024.0
                                    )
                                )
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.setting_page_chat_storage)) },
                    )
                }
            }

            item("aboutSettings") {
                val context = LocalContext.current
                val shareText = stringResource(R.string.setting_page_share_text)
                val share = stringResource(R.string.setting_page_share)
                val noShareApp = stringResource(R.string.setting_page_no_share_app)
                CardGroup(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    title = { Text(stringResource(R.string.setting_page_about)) },
                ) {
                    item(
                        onClick = { navController.navigate(Screen.SettingAbout) },
                        leadingContent = { Icon(HugeIcons.Clapping01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_about_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_about)) },
                    )
                    item(
                        onClick = { context.openUrl("https://github.com/Xiaoyun0629/emyth") },
                        leadingContent = { Icon(HugeIcons.Book01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_documentation_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_documentation)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.Log) },
                        leadingContent = { Icon(HugeIcons.Bookshelf01, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_request_logs_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_request_logs)) },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SecurityAudit) },
                        leadingContent = { Icon(HugeIcons.Alert01, null) },
                        supportingContent = { Text("查看插件安装、工作流拦截、敏感操作等安全事件记录") },
                        headlineContent = { Text("安全审计日志") },
                    )
                    item(
                        onClick = { navController.navigate(Screen.SettingDonate) },
                        leadingContent = { Icon(HugeIcons.InLove, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_donate_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_donate)) },
                    )
                    item(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            intent.putExtra(Intent.EXTRA_TEXT, shareText)
                            try {
                                context.startActivity(Intent.createChooser(intent, share))
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
                            }
                        },
                        leadingContent = { Icon(HugeIcons.Share04, null) },
                        supportingContent = { Text(stringResource(R.string.setting_page_share_desc)) },
                        headlineContent = { Text(stringResource(R.string.setting_page_share)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderConfigWarningCard(navController: Navigator) {
    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_page_config_api_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.setting_page_config_api_desc))
                },
                leadingContent = {
                    Icon(HugeIcons.Alert01, null)
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )

            TextButton(
                onClick = {
                    navController.navigate(Screen.SettingProvider)
                }
            ) {
                Text(stringResource(R.string.setting_page_config))
            }
        }
    }
}


private class SearchEntry(val title: String, val kws: List<String>, val go: () -> Unit)

@Composable
private fun SettingSearchBar(navController: Navigator) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val entries = remember {
        listOf(
            SearchEntry("显示设置", listOf("显示", "外观", "主题", "颜色", "壁纸", "气泡", "头像", "玻璃", "模糊"), { navController.navigate(Screen.SettingDisplay) }),
            SearchEntry("插件管理", listOf("插件", "plugin", "导入"), { navController.navigate(Screen.SettingPlugins) }),
            SearchEntry("语音收藏", listOf("语音收藏", "收藏", "回听", "语音"), { navController.navigate(Screen.VoiceFavorite) }),
            SearchEntry("安全设置", listOf("安全", "工具确认", "自动批准", "拦截", "权限"), { navController.navigate(Screen.SettingSecurity) }),
            SearchEntry("助手", listOf("助手", "assistant", "人设", "本地工具"), { navController.navigate(Screen.Assistant) }),
            SearchEntry("扩展", listOf("扩展", "extension"), { navController.navigate(Screen.Extensions) }),
            SearchEntry("默认模型", listOf("模型", "model", "默认模型"), { navController.navigate(Screen.SettingModels) }),
            SearchEntry("供应商", listOf("供应商", "provider", "api", "key", "接口", "模型服务"), { navController.navigate(Screen.SettingProvider) }),
            SearchEntry("搜索服务", listOf("搜索", "search", "联网", "搜索服务"), { navController.navigate(Screen.SettingSearch) }),
            SearchEntry("语音服务", listOf("语音", "tts", "朗读", "说话", "合成", "声音"), { navController.navigate(Screen.SettingSpeech) }),
            SearchEntry("MCP", listOf("mcp", "工具"), { navController.navigate(Screen.SettingMcp) }),
            SearchEntry("Web 服务器", listOf("web", "服务器", "网页", "局域网"), { navController.navigate(Screen.SettingWeb) }),
            SearchEntry("系统工具", listOf("系统工具", "位置", "通知", "日历", "闹钟", "工具", "定位"), { navController.navigate(Screen.SettingSystemTools) }),
            SearchEntry("语音唤醒", listOf("唤醒", "唤醒词", "语音唤醒"), { navController.navigate(Screen.SettingVoiceWake) }),
            SearchEntry("主动消息", listOf("主动消息", "主动发消息", "主动"), { navController.navigate(Screen.SettingProactiveMessage) }),
            SearchEntry("微信 Bot", listOf("微信", "weixin", "bot", "机器人", "微信机器人"), { navController.navigate(Screen.SettingWeixinBot) }),
            SearchEntry("QQ Bot", listOf("qq", "bot", "qq机器人"), { navController.navigate(Screen.SettingQqBot) }),
            SearchEntry("工作流", listOf("工作流", "workflow", "自动化", "触发器", "任务"), { navController.navigate(Screen.Workflows) }),
            SearchEntry("健康数据", listOf("健康", "心率", "睡眠", "步数", "手环", "gadgetbridge"), { navController.navigate(Screen.Health) }),
            SearchEntry("备份", listOf("备份", "backup", "恢复", "导出", "导入"), { navController.navigate(Screen.Backup) }),
            SearchEntry("聊天记录存储", listOf("存储", "文件", "聊天记录", "占用", "空间"), { navController.navigate(Screen.SettingFiles) }),
            SearchEntry("关于", listOf("关于", "about", "版本"), { navController.navigate(Screen.SettingAbout) }),
            SearchEntry("文档", listOf("文档", "github", "教程", "帮助"), { context.openUrl("https://github.com/Xiaoyun0629/emyth") }),
            SearchEntry("请求日志", listOf("日志", "log", "请求"), { navController.navigate(Screen.Log) }),
            SearchEntry("安全审计日志", listOf("审计", "安全日志", "安全事件"), { navController.navigate(Screen.SecurityAudit) }),
            SearchEntry("捐赠", listOf("捐赠", "赞助", "donate", "打赏"), { navController.navigate(Screen.SettingDonate) }),
        )
    }
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索设置项…") },
            leadingIcon = { Icon(HugeIcons.GlobalSearch, null) },
            singleLine = true,
        )
        if (query.isNotBlank()) {
            val results = entries.filter { e ->
                e.title.contains(query, ignoreCase = true) || e.kws.any { it.contains(query, ignoreCase = true) }
            }
            Spacer(Modifier.height(8.dp))
            CardGroup {
                if (results.isEmpty()) {
                    item(headlineContent = { Text("没找到「$query」相关的设置") })
                } else {
                    results.forEach { e ->
                        item(
                            onClick = { query = ""; e.go() },
                            headlineContent = { Text(e.title) },
                        )
                    }
                }
            }
        }
    }
}
