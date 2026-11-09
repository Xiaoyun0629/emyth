/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import dev.emyth.app.R
import dev.emyth.app.data.ai.tools.LocalToolOption
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.model.Assistant
import dev.emyth.app.ui.components.ai.ModelSelector
import dev.emyth.app.ui.components.nav.BackButton
import dev.emyth.app.ui.components.ui.CardGroup
import dev.emyth.app.ui.theme.CustomColors
import me.rerere.ai.provider.ModelType
import org.koin.androidx.compose.koinViewModel
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.core.parameter.parametersOf

@Composable
fun AssistantLocalToolPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_local_tools))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantLocalToolContent(
            modifier = Modifier.padding(innerPadding),
            assistant = assistant,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
private fun AssistantLocalToolContent(
    modifier: Modifier = Modifier,
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    val settingsStore = getKoin().get<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun toggleLocalTool(option: LocalToolOption, enabled: Boolean) {
        val newLocalTools = if (enabled) {
            assistant.localTools + option
        } else {
            assistant.localTools - option
        }
        onUpdate(assistant.copy(localTools = newLocalTools))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var q by remember { mutableStateOf("") }
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索工具…") },
            singleLine = true
        )
        CardGroup {
            if (q.isBlank() || q.contains("javascript", ignoreCase = true) || q.contains("js", ignoreCase = true) || q.contains("脚本", ignoreCase = true) || q.contains("代码", ignoreCase = true) || q.contains("引擎", ignoreCase = true)) {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_javascript_engine_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_javascript_engine_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.JavascriptEngine),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.JavascriptEngine, it) }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("时间", ignoreCase = true) || q.contains("日期", ignoreCase = true) || q.contains("time", ignoreCase = true) || q.contains("时区", ignoreCase = true) || q.contains("星期", ignoreCase = true)) {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_time_info_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_time_info_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.TimeInfo),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.TimeInfo, it) }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("剪贴板", ignoreCase = true) || q.contains("clipboard", ignoreCase = true) || q.contains("复制", ignoreCase = true) || q.contains("粘贴", ignoreCase = true)) {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_clipboard_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_clipboard_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Clipboard),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Clipboard, it) }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("语音", ignoreCase = true) || q.contains("tts", ignoreCase = true) || q.contains("朗读", ignoreCase = true) || q.contains("说话", ignoreCase = true) || q.contains("合成", ignoreCase = true)) {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_tts_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_tts_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Tts),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Tts, it) }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("通话", ignoreCase = true) || q.contains("电话", ignoreCase = true) || q.contains("来电", ignoreCase = true) || q.contains("语音通话", ignoreCase = true)) {
            item(
                headlineContent = { Text("主动语音通话") },
                supportingContent = { Text("允许 AI 在合适时机主动发起语音通话, 弹出来电界面邀请你接听。开启后 AI 可能会在觉得语音更合适时打来电话") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.RequestVoiceCall),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.RequestVoiceCall, it) }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("询问", ignoreCase = true) || q.contains("ask", ignoreCase = true) || q.contains("提问", ignoreCase = true) || q.contains("反问", ignoreCase = true)) {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_ask_user_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_local_tools_ask_user_desc))
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.AskUser),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.AskUser, it) }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("日历", ignoreCase = true) || q.contains("calendar", ignoreCase = true) || q.contains("日程", ignoreCase = true)) {
            item(
                headlineContent = { Text("日历读写") },
                supportingContent = { Text("允许AI读取、创建和删除日历事件，需要日历权限") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Calendar),
                        onCheckedChange = { toggleLocalTool(LocalToolOption.Calendar, it) }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("跳过", ignoreCase = true) || q.contains("skip", ignoreCase = true) || q.contains("回复", ignoreCase = true)) {
            item(
                headlineContent = { Text("允许跳过回复") },
                supportingContent = { Text("允许AI在认为无需回复时跳过，回复 [SKIP] 的消息将被隐藏") },
                trailingContent = {
                    Switch(
                        checked = assistant.allowSkipReply,
                        onCheckedChange = {
                            val newLocalTools = if (it) {
                                assistant.localTools + LocalToolOption.AllowSkipReply
                            } else {
                                assistant.localTools - LocalToolOption.AllowSkipReply
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools, allowSkipReply = it))
                        }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("工作流", ignoreCase = true) || q.contains("workflow", ignoreCase = true) || q.contains("自动化", ignoreCase = true) || q.contains("触发器", ignoreCase = true)) {
            item(
                headlineContent = { Text("工作流") },
                supportingContent = { Text("开启后 AI 可创建事件驱动的自动化工作流（触发器+条件->动作）") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Workflows),
                        onCheckedChange = {
                            val newLocalTools = if (it) {
                                assistant.localTools + LocalToolOption.Workflows
                            } else {
                                assistant.localTools - LocalToolOption.Workflows
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("屏幕", ignoreCase = true) || q.contains("点击", ignoreCase = true) || q.contains("滑动", ignoreCase = true) || q.contains("截图", ignoreCase = true) || q.contains("无障碍", ignoreCase = true) || q.contains("screen", ignoreCase = true)) {
            item(
                headlineContent = { Text("屏幕自动化") },
                supportingContent = { Text("点击/滑动/输入/读取界面元素/截图。需在系统设置->无障碍中启用Emyth") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.ScreenAutomation),
                        onCheckedChange = {
                            val newLocalTools = if (it) {
                                assistant.localTools + LocalToolOption.ScreenAutomation
                            } else {
                                assistant.localTools - LocalToolOption.ScreenAutomation
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("ssh", ignoreCase = true) || q.contains("远程", ignoreCase = true) || q.contains("服务器", ignoreCase = true) || q.contains("命令", ignoreCase = true)) {
            item(
                headlineContent = { Text("SSH 远程连接") },
                supportingContent = { Text("远程执行命令、SFTP 上传下载、保存主机凭据") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Ssh),
                        onCheckedChange = {
                            val newLocalTools = if (it) {
                                assistant.localTools + LocalToolOption.Ssh
                            } else {
                                assistant.localTools - LocalToolOption.Ssh
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("配置", ignoreCase = true) || q.contains("configure", ignoreCase = true) || q.contains("mcp", ignoreCase = true)) {
            item(
                headlineContent = { Text("全局配置") },
                supportingContent = { Text("让 AI 帮你配置 MCP、设置开关等，无需手动操作") },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.Configure),
                        onCheckedChange = {
                            val newLocalTools = if (it) {
                                assistant.localTools + LocalToolOption.Configure
                            } else {
                                assistant.localTools - LocalToolOption.Configure
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )
            }
            if (q.isBlank() || q.contains("生图", ignoreCase = true) || q.contains("图片", ignoreCase = true) || q.contains("画图", ignoreCase = true) || q.contains("图像", ignoreCase = true) || q.contains("image", ignoreCase = true) || q.contains("生成", ignoreCase = true)) {
            item(
                headlineContent = { Text("生图") },
                supportingContent = {
                    Column {
                        Text("让 AI 直接调用生图模型生成图片")
                        Spacer(Modifier.height(8.dp))
                        ModelSelector(
                            modelId = settings.imageGenerationModelId,
                            providers = settings.providers,
                            type = ModelType.IMAGE,
                            onSelect = { model ->
                                scope.launch {
                                    settingsStore.update { oldSettings ->
                                        oldSettings.copy(imageGenerationModelId = model.id)
                                    }
                                }
                            }
                        )
                    }
                },
                trailingContent = {
                    Switch(
                        checked = assistant.localTools.contains(LocalToolOption.GenerateImage),
                        onCheckedChange = {
                            val newLocalTools = if (it) {
                                assistant.localTools + LocalToolOption.GenerateImage
                            } else {
                                assistant.localTools - LocalToolOption.GenerateImage
                            }
                            onUpdate(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            )
            }
        }
    }
}
