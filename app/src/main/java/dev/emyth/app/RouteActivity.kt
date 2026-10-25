/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app

import android.annotation.SuppressLint
import android.content.Intent
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.datastore.getAssistantById
import dev.emyth.app.data.repository.ConversationRepository
import dev.emyth.app.data.db.DatabaseMigrationTracker
import dev.emyth.app.data.db.MigrationState
import dev.emyth.app.data.event.AppEvent
import dev.emyth.app.data.event.AppEventBus
import dev.emyth.app.ui.activity.SafeModeActivity
import dev.emyth.app.data.datastore.getCurrentAssistant
import dev.emyth.app.data.datastore.getCurrentChatModel
import dev.emyth.app.ui.context.LocalASRState
import dev.emyth.app.ui.context.LocalCurrentAssistant
import dev.emyth.app.ui.context.LocalCurrentChatModel
import dev.emyth.app.ui.context.LocalDisplaySettings
import dev.emyth.app.ui.context.LocalMcpServers
import dev.emyth.app.ui.context.LocalNavController
import dev.emyth.app.ui.context.LocalProviders
import dev.emyth.app.ui.context.LocalQuickMessages
import dev.emyth.app.ui.context.LocalSettings
import dev.emyth.app.ui.context.LocalSharedTransitionScope
import dev.emyth.app.ui.context.LocalTTSState
import dev.emyth.app.ui.context.LocalToaster
import dev.emyth.app.ui.context.Navigator
import dev.emyth.app.ui.hooks.readBooleanPreference
import dev.emyth.app.ui.hooks.readStringPreference
import dev.emyth.app.ui.hooks.rememberCustomAsrState
import dev.emyth.app.ui.hooks.rememberCustomTtsState
import dev.emyth.app.ui.pages.assistant.AssistantPage
import dev.emyth.app.ui.pages.assistant.detail.AssistantBasicPage
import dev.emyth.app.ui.pages.assistant.detail.AssistantDetailPage
import dev.emyth.app.ui.pages.assistant.detail.AssistantExtensionsPage
import dev.emyth.app.ui.pages.assistant.detail.AssistantLocalToolPage
import dev.emyth.app.ui.pages.assistant.detail.AssistantMcpPage
import dev.emyth.app.ui.pages.assistant.detail.AssistantMemoryPage
import dev.emyth.app.ui.pages.assistant.detail.AssistantPromptPage
import dev.emyth.app.ui.pages.assistant.detail.AssistantRequestPage
import dev.emyth.app.ui.pages.backup.BackupPage
import dev.emyth.app.ui.pages.chat.ChatPage
import dev.emyth.app.ui.pages.debug.DebugPage
import dev.emyth.app.ui.pages.developer.DeveloperPage
import dev.emyth.app.ui.pages.disclaimer.DisclaimerPage
import dev.emyth.app.ui.pages.disclaimer.LegalPage
import dev.emyth.app.ui.pages.extensions.ExtensionsPage
import dev.emyth.app.ui.pages.extensions.ExternalMemoriesPage
import dev.emyth.app.ui.pages.extensions.PromptPage
import dev.emyth.app.ui.pages.extensions.QuickMessagesPage
import dev.emyth.app.ui.pages.extensions.SkillDetailPage
import dev.emyth.app.ui.pages.extensions.SkillsPage
import dev.emyth.app.ui.pages.extensions.workspace.WorkspaceDetailPage
import dev.emyth.app.ui.pages.extensions.workspace.WorkspacePage
import dev.emyth.app.ui.pages.extensions.workspace.WorkspaceTerminalPage
import dev.emyth.app.ui.pages.favorite.FavoritePage
import dev.emyth.app.ui.pages.health.HealthPage
import dev.emyth.app.ui.pages.history.HistoryPage
import dev.emyth.app.ui.pages.imggen.ImageGenPage
import dev.emyth.app.ui.pages.log.LogPage
import dev.emyth.app.ui.pages.security.SecurityAuditPage
import dev.emyth.app.ui.pages.miniapp.MiniAppEditPage
import dev.emyth.app.ui.pages.miniapp.MiniAppManagerPage
import dev.emyth.app.ui.pages.miniapp.MiniAppPage
import dev.emyth.app.ui.pages.search.SearchPage
import dev.emyth.app.ui.pages.setting.SettingAboutPage
import dev.emyth.app.ui.pages.setting.SettingDisplayCodeInteractionPage
import dev.emyth.app.ui.pages.setting.SettingDisplayColorPage
import dev.emyth.app.ui.pages.setting.SettingDisplayGeneralPage
import dev.emyth.app.ui.pages.setting.SettingDisplayIllustrationPage
import dev.emyth.app.ui.pages.setting.SettingDisplayMessagePage
import dev.emyth.app.ui.pages.setting.SettingDisplayNotificationPage
import dev.emyth.app.ui.pages.setting.SettingDisplayPage
import dev.emyth.app.ui.pages.setting.SettingDisplayThemePage
import dev.emyth.app.ui.pages.setting.SettingDisplayTransparencyPage
import dev.emyth.app.ui.pages.setting.SettingThemePage
import dev.emyth.app.ui.pages.setting.SettingDonatePage
import dev.emyth.app.ui.pages.setting.SettingFilesPage
import dev.emyth.app.ui.pages.setting.SettingMcpPage
import dev.emyth.app.ui.pages.setting.SettingModelPage
import dev.emyth.app.ui.pages.setting.SettingPage
import dev.emyth.app.ui.pages.setting.SettingProviderDetailPage
import dev.emyth.app.ui.pages.setting.SettingProviderPage
import dev.emyth.app.ui.pages.setting.SettingSearchDetailPage
import dev.emyth.app.ui.pages.setting.SettingSearchPage
import dev.emyth.app.ui.pages.setting.SettingSpeechPage
import dev.emyth.app.ui.pages.setting.SettingWebPage
import dev.emyth.app.ui.pages.setting.SettingSystemToolsPage
import dev.emyth.app.ui.pages.setting.SecuritySettingPage
import dev.emyth.app.ui.pages.setting.SettingProactiveMessagePage
import dev.emyth.app.ui.pages.setting.SettingWeixinBotPage
import dev.emyth.app.ui.pages.setting.SettingQqBotPage
import dev.emyth.app.plugin.webview.PluginWebViewPage
import dev.emyth.app.ui.pages.memory.MemoryBankPage
import dev.emyth.app.ui.components.ui.EmojiPickerPage
import dev.emyth.app.ui.pages.share.handler.ShareHandlerPage
import dev.emyth.app.ui.pages.stats.StatsPage
import dev.emyth.app.ui.pages.translator.TranslatorPage
 import dev.emyth.app.ui.pages.voice.CallingPage
import dev.emyth.app.ui.pages.voice.IncomingCallPage
 import dev.emyth.app.ui.pages.voice.VoiceCallPage
import dev.emyth.app.service.VoiceCallService
import dev.emyth.app.ui.pages.webview.WebViewPage
import dev.emyth.app.ui.theme.LocalDarkMode
import dev.emyth.app.ui.theme.RikkahubTheme
import dev.emyth.app.utils.CrashHandler
import dev.emyth.app.service.ChatService
import dev.emyth.app.service.KeepAliveService
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val TAG = "RouteActivity"

class RouteActivity : ComponentActivity() {
    companion object {
        /** 当前是否处于画中画（PiP）小窗模式，供通话页切换到简化 UI */
        val isInPip = mutableStateOf(false)
    }

    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private var navStack: MutableList<NavKey>? = null

    // Volume key listener registry — last registered handler wins
    internal val volumeKeyListeners = mutableListOf<(isVolumeUp: Boolean) -> Boolean>()

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isVolumeUp = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> true
                KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.dispatchKeyEvent(event)
            }
            if (volumeKeyListeners.lastOrNull()?.invoke(isVolumeUp) == true) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip.value = isInPictureInPictureMode
    }

    /** 进入画中画小窗（供通话页"切小屏"按钮调用） */
    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "进入画中画失败", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        disableNavigationBarContrast()
        super.onCreate(savedInstanceState)

        if (CrashHandler.hasCrashed(this)) {
            startActivity(Intent(this, SafeModeActivity::class.java))
            finish()
            return
        }

        // 根据设置自动启动保活服务
        try {
            if (settingsStore.settingsFlow.value.keepAliveEnabled) {
                KeepAliveService.start(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动启动保活服务失败", e)
        }

        setContent {
            RikkahubTheme {
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(OkHttpNetworkFetcherFactory(
                                callFactory = { okHttpClient },
                                cacheStrategy = { CacheControlCacheStrategy() },
                            ))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                add(AnimatedImageDecoder.Factory())
                            } else {
                                add(GifDecoder.Factory())
                            }
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                AppRoutes()
            }
        }
    }

    private fun disableNavigationBarContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    @Composable
    private fun ShareHandler(backStack: MutableList<NavKey>) {
        val shareIntent = remember {
            Intent().apply {
                action = intent?.action
                putExtra(Intent.EXTRA_TEXT, intent?.getStringExtra(Intent.EXTRA_TEXT))
                putExtra(Intent.EXTRA_STREAM, intent?.getStringExtra(Intent.EXTRA_STREAM))
                putExtra(Intent.EXTRA_PROCESS_TEXT, intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
            }
        }

        LaunchedEffect(backStack) {
            when (shareIntent.action) {
                Intent.ACTION_SEND -> {
                    val text = shareIntent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    val imageUri = shareIntent.getStringExtra(Intent.EXTRA_STREAM)
                    backStack.add(Screen.ShareHandler(text, imageUri))
                }

                Intent.ACTION_PROCESS_TEXT -> {
                    val text = shareIntent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
                    backStack.add(Screen.ShareHandler(text, null))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Navigate to the chat screen if a conversation ID is provided
        intent.getStringExtra("conversationId")?.let { text ->
            val autoStartVoice = intent.getBooleanExtra("autoStartVoice", false)
            navStack?.let { stack ->
                Snapshot.withMutableSnapshot {
                    // 检查是否已经在同一个对话页面
                    val existingIndex = stack.indexOfLast {
                        it is Screen.Chat && it.id == text
                    }
                    if (existingIndex >= 0) {
                        // 已经在同一个对话，移动到栈顶
                        val existing = stack.removeAt(existingIndex) as Screen.Chat
                        if (autoStartVoice) {
                            stack.add(existing.copy(autoStartVoice = true))
                        } else {
                            stack.add(existing)
                        }
                    } else {
                        // 添加新对话页面
                        stack.add(Screen.Chat(text, autoStartVoice = autoStartVoice))
                    }
                }
            }
        }

        // 新增逻辑: 从语音通话通知点击, 回到通话页面
        intent.getStringExtra("openVoiceCallConversationId")?.let { convId ->
            navStack?.let { stack ->
                Snapshot.withMutableSnapshot {
                    val alreadyOnVoiceCall = stack.lastOrNull().let {
                        it is Screen.VoiceCall && it.conversationId == convId
                    }
                    if (!alreadyOnVoiceCall) {
                        stack.add(Screen.VoiceCall(convId))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    @Composable
    fun AppRoutes() {
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val tts = rememberCustomTtsState()

        // 首次启动：未同意免责声明时强制展示
        if (!settings.disclaimerAccepted) {
            DisclaimerPage(
                onAccept = {
                    scope.launch {
                        settingsStore.update {
                            it.copy(
                                disclaimerAccepted = true,
                                disclaimerAcceptedAt = (System.currentTimeMillis() / 1000).toInt()
                            )
                        }
                    }
                },
                onDecline = { finish() }
            )
            return
        }
        val asr = rememberCustomAsrState()
        val eventBus = koinInject<AppEventBus>()
        val migrationState by DatabaseMigrationTracker.state.collectAsStateWithLifecycle()

        val startScreen = Screen.Chat(
            id = if (readBooleanPreference("create_new_conversation_on_start", true)) {
                Uuid.random().toString()
            } else {
                readStringPreference(
                    "lastConversationId",
                    Uuid.random().toString()
                ) ?: Uuid.random().toString()
            }
        )

        val backStack = rememberNavBackStack(startScreen)
        SideEffect { this@RouteActivity.navStack = backStack }

        ShareHandler(backStack)

        // 监听 App 事件: TTS 朗读 / AI 主动发起语音通话
        LaunchedEffect(tts) {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.Speak -> tts.speak(event.text)
                    is AppEvent.EmojiSelected -> { /* handled in UIAvatar */ }
                    is AppEvent.McpOAuthCallback -> Unit // 由 McpManager 消费
                    is AppEvent.RequestVoiceCall -> {
                        val convId = event.conversationId
                        // 单通话守卫: 已有通话进行中就不重复弹
                        if (VoiceCallService.activeConversationId.value != null) return@collect
                        // 防止重复叠加来电页
                        val alreadyIncoming = backStack.lastOrNull() is Screen.IncomingCall
                        if (!alreadyIncoming) {
                            backStack.add(Screen.IncomingCall(convId))
                        }
                    }
                    is AppEvent.IncomingCallDecision -> {
                        val convId = event.conversationId
                        // 只在"呼叫中"界面才响应（用户可能已经点了取消）
                        if (backStack.lastOrNull() !is Screen.Calling) return@collect
                        if (event.accept) {
                            runCatching { tts.stop() }
                            if (VoiceCallService.activeConversationId.value == null) {
                                VoiceCallService.start(this@RouteActivity, convId)
                            }
                            backStack.removeLastOrNull()
                            backStack.add(Screen.VoiceCall(convId))
                        } else {
                            // 拒接：理由已由 decideIncomingCall 追加进聊天，这里直接退出呼叫界面
                            backStack.removeLastOrNull()
                        }
                    }
                }
            }
        }

        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides Navigator(backStack),
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalDisplaySettings provides settings.displaySetting,
                LocalProviders provides settings.providers,
                LocalMcpServers provides settings.mcpServers,
                LocalQuickMessages provides settings.quickMessages,
                LocalCurrentAssistant provides settings.getCurrentAssistant(),
                LocalCurrentChatModel provides settings.getCurrentChatModel(),
                LocalHighlighter provides highlighter,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
                LocalASRState provides asr,
            ) {
                Toaster(
                    state = toastState,
                    darkTheme = LocalDarkMode.current,
                    richColors = true,
                    alignment = Alignment.TopCenter,
                    showCloseButton = true,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .let { base ->
                            if (settings.themeId == "pearltide") {
                                base.paint(
                                    painter = painterResource(id = R.drawable.pearltide_chat_bg),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                base
                            }
                        }
                ) {
                    NavDisplay(
                        backStack = backStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        modifier = Modifier.fillMaxSize(),
                        onBack = { backStack.removeLastOrNull() },
                        transitionSpec = {
                            if (backStack.size == 1) fadeIn() togetherWith fadeOut()
                            else {
                                slideInHorizontally { it } togetherWith
                                    slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
                            }
                        },
                        popTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        predictivePopTransitionSpec = {
                            slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn() togetherWith
                                slideOutHorizontally { it }
                        },
                        entryProvider = entryProvider {
                            entry<Screen.Chat>(
                                metadata = NavDisplay.transitionSpec { fadeIn() togetherWith fadeOut() }
                                        + NavDisplay.popTransitionSpec { fadeIn() togetherWith fadeOut() }
                            ) { key ->
                                ChatPage(
                                    id = Uuid.parse(key.id),
                                    text = key.text,
                                    files = key.files.map { it.toUri() },
                                    nodeId = key.nodeId?.let { Uuid.parse(it) },
                                    autoStartVoice = key.autoStartVoice,
                                )
                            }

                            entry<Screen.ShareHandler> { key ->
                                ShareHandlerPage(
                                    text = key.text,
                                    image = key.streamUri
                                )
                            }

                            entry<Screen.History> {
                                HistoryPage()
                            }

                            entry<Screen.Favorite> {
                                FavoritePage()
                            }

                            entry<Screen.Assistant> {
                                AssistantPage()
                            }

                            entry<Screen.AssistantDetail> { key ->
                                AssistantDetailPage(key.id)
                            }

                            entry<Screen.AssistantBasic> { key ->
                                AssistantBasicPage(key.id)
                            }

                            entry<Screen.AssistantPrompt> { key ->
                                AssistantPromptPage(key.id)
                            }

                            entry<Screen.AssistantMemory> { key ->
                                AssistantMemoryPage(key.id)
                            }

                            entry<Screen.AssistantRequest> { key ->
                                AssistantRequestPage(key.id)
                            }

                            entry<Screen.AssistantMcp> { key ->
                                AssistantMcpPage(key.id)
                            }

                            entry<Screen.AssistantLocalTool> { key ->
                                AssistantLocalToolPage(key.id)
                            }

                            entry<Screen.AssistantInjections> { key ->
                                AssistantExtensionsPage(key.id)
                            }

                            entry<Screen.Translator> {
                                TranslatorPage()
                            }

                            entry<Screen.Setting> {
                                SettingPage()
                            }

                            entry<Screen.Backup> {
                                BackupPage()
                            }

                            entry<Screen.ImageGen> {
                                ImageGenPage()
                            }

                            entry<Screen.WebView> { key ->
                                WebViewPage(key.url, key.content)
                            }

                            entry<Screen.SettingDisplay> {
                                SettingDisplayPage()
                            }

                            entry<Screen.SettingTheme> {
                                SettingThemePage()
                            }

                            entry<Screen.SettingDisplayTheme> {
                                SettingDisplayThemePage()
                            }
                            entry<Screen.SettingDisplayColor> {
                                SettingDisplayColorPage()
                            }
                            entry<Screen.SettingDisplayTransparency> {
                                SettingDisplayTransparencyPage()
                            }
                            entry<Screen.SettingDisplayIllustration> {
                                SettingDisplayIllustrationPage()
                            }
                            entry<Screen.SettingDisplayMessage> {
                                SettingDisplayMessagePage()
                            }
                            entry<Screen.SettingDisplayCodeInteraction> {
                                SettingDisplayCodeInteractionPage()
                            }
                            entry<Screen.SettingDisplayGeneral> {
                                SettingDisplayGeneralPage()
                            }
                            entry<Screen.SettingDisplayNotification> {
                                SettingDisplayNotificationPage()
                            }

                            entry<Screen.SettingProvider> {
                                SettingProviderPage()
                            }

                            entry<Screen.SettingProviderDetail> { key ->
                                val id = Uuid.parse(key.providerId)
                                SettingProviderDetailPage(id = id)
                            }

                            entry<Screen.SettingModels> {
                                SettingModelPage()
                            }

                            entry<Screen.SettingAbout> {
                                SettingAboutPage()
                            }

                            entry<Screen.SettingSearch> {
                                SettingSearchPage()
                            }

                            entry<Screen.SettingSearchDetail> { key ->
                                val id = Uuid.parse(key.serviceId)
                                SettingSearchDetailPage(id)
                            }

                            entry<Screen.SettingSpeech> {
                                SettingSpeechPage()
                            }

                            entry<Screen.SettingMcp> {
                                SettingMcpPage()
                            }

                            entry<Screen.SettingDonate> {
                                SettingDonatePage()
                            }

                            entry<Screen.SettingFiles> {
                                SettingFilesPage()
                            }

                            entry<Screen.SettingWeb> {
                                SettingWebPage()
                            }

                            entry<Screen.Developer> {
                                DeveloperPage()
                            }

                            entry<Screen.Debug> {
                                DebugPage()
                            }

entry<Screen.Log> {
    LogPage()
}

entry<Screen.SecurityAudit> {
    SecurityAuditPage()
}

entry<Screen.Extensions> {
                                ExtensionsPage()
                            }

                            entry<Screen.QuickMessages> {
                                QuickMessagesPage()
                            }

                            entry<Screen.ExternalMemories> {
                                ExternalMemoriesPage()
                            }

                            entry<Screen.Prompts> {
                                PromptPage()
                            }

                            entry<Screen.Skills> {
                                SkillsPage()
                            }

                            entry<Screen.SkillDetail> { key ->
                                SkillDetailPage(skillName = key.skillName)
                            }

                            entry<Screen.Workspaces> {
                                WorkspacePage()
                            }

                            entry<Screen.WorkspaceDetail> { key ->
                                WorkspaceDetailPage(key.id)
                            }

                            entry<Screen.WorkspaceTerminal> { key ->
                                WorkspaceTerminalPage(key.id)
                            }

                            entry<Screen.MessageSearch> {
                                SearchPage()
                            }

                            entry<Screen.Health> {
                                HealthPage()
                            }

                            entry<Screen.Stats> {
                                StatsPage()
                            }

                            entry<Screen.SettingSystemTools> {
                                SettingSystemToolsPage()
                            }

                            entry<Screen.SettingSecurity> {
                                SecuritySettingPage()
                            }

                            entry<Screen.SettingProactiveMessage> {
                                SettingProactiveMessagePage()
                            }

                            entry<Screen.SettingWeixinBot> {
                                SettingWeixinBotPage()
                            }

                            entry<Screen.SettingQqBot> {
                                SettingQqBotPage()
                            }

                            entry<Screen.Workflows> {
                                dev.emyth.app.workflow.ui.WorkflowsScreen()
                            }

                            entry<Screen.WorkflowDetail> { key ->
                                dev.emyth.app.workflow.ui.WorkflowDetailScreen(key.id)
                            }

                            entry<Screen.SettingPlugins> {
                                val nav = LocalNavController.current
                                dev.emyth.app.plugin.ui.PluginManagePage(
                                    onNavigateToFolder = { folderId ->
                                        nav.navigate(Screen.PluginFolder(folderId))
                                    },
                                    onNavigateToDetail = { pluginId ->
                                        nav.navigate(Screen.PluginDetail(pluginId))
                                    }
                                )
                            }

                            entry<Screen.PluginFolder> { key ->
                                val nav = LocalNavController.current
                                dev.emyth.app.plugin.ui.PluginFolderPage(
                                    folderId = key.folderId,
                                    onNavigateBack = { backStack.removeLastOrNull() },
                                    onNavigateToDetail = { pluginId ->
                                        nav.navigate(Screen.PluginDetail(pluginId))
                                    }
                                )
                            }

                            entry<Screen.PluginDetail> { key ->
                                val nav = LocalNavController.current
                                dev.emyth.app.plugin.ui.PluginDetailPage(
                                    pluginId = key.pluginId,
                                    onNavigateBack = { backStack.removeLastOrNull() },
                                    onNavigateToCustomPage = { customPage ->
                                        when (customPage) {
                                            "memory_bank" -> nav.navigate(Screen.MemoryBank)
                                        }
                                    },
                                    onNavigateToWebView = { pluginId, entryPath ->
                                        nav.navigate(Screen.PluginWebView(pluginId, entryPath))
                                    },
                                    onNavigateToDeclarativeUI = { pluginId ->
                                        nav.navigate(Screen.PluginDeclarativeUI(pluginId))
                                    }
                                )
                            }

                            entry<Screen.PluginDeclarativeUI> { key ->
                                val pluginManager = koinInject<dev.emyth.app.plugin.manager.PluginManager>()
                                dev.emyth.app.plugin.ui.PluginUIDeclarativePage(
                                    pluginId = key.pluginId,
                                    pluginManager = pluginManager,
                                    onNavigateBack = { backStack.removeLastOrNull() }
                                )
                            }

                            entry<Screen.PluginWebView> { key ->
                                val pluginManager = koinInject<dev.emyth.app.plugin.manager.PluginManager>()
                                PluginWebViewPage(
                                    pluginId = key.pluginId,
                                    htmlEntryPath = key.entryPath,
                                    pluginManager = pluginManager,
                                    onNavigateBack = { backStack.removeLastOrNull() }
                                )
                            }

                            entry<Screen.MemoryBank> {
                                MemoryBankPage(
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }

                            entry<Screen.EmojiPicker> {
                                EmojiPickerPage(
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }

                            entry<Screen.VoiceCall> { key ->
                                VoiceCallPage(
                                    conversationId = Uuid.parse(key.conversationId),
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }

                            entry<Screen.IncomingCall> { key ->
                                // 来电界面: 接听 -> 启动通话并跳转到通话页; 拒接 -> 返回
                                // 通过 conversationId 查会话 -> assistantId -> 助手名
                                val conversationRepo = koinInject<ConversationRepository>()
                                val chatService = koinInject<ChatService>()
                                var assistantName by remember {
                                    mutableStateOf("")
                                }
                                LaunchedEffect(key.conversationId) {
                                    assistantName = runCatching {
                                        conversationRepo.getConversationById(Uuid.parse(key.conversationId))
                                            ?.assistantId
                                            ?.let { settings.getAssistantById(it)?.name }
                                    }.getOrNull().orEmpty()
                                }
                                IncomingCallPage(
                                    conversationId = key.conversationId,
                                    assistantName = assistantName,
                                    onStartCall = {
                                        val convId = key.conversationId
                                        // 先停掉聊天侧的 TTS, 避免音频焦点抢占导致 ASR 识别质量下降
                                        // (AI 刚说完话触发主动来电时, 聊天页的 TTS 可能还在播放)
                                        runCatching { tts.stop() }
                                        if (VoiceCallService.activeConversationId.value == null) {
                                            VoiceCallService.start(
                                                this@RouteActivity,
                                                convId
                                            )
                                        }
                                        // 替换来电页为通话页
                                        backStack.removeLastOrNull()
                                        backStack.add(Screen.VoiceCall(convId))
                                    },
                                    onDecline = {
                                        backStack.removeLastOrNull()
                                        try {
                                            chatService.notifyVoiceCallDeclined(Uuid.parse(key.conversationId))
                                        } catch (e: Exception) {
                                            Log.e(TAG, "onDecline 触发 notifyVoiceCallDeclined 失败, conversationId=${key.conversationId}", e)
                                        }
                                    }
                                )
                            }

                            entry<Screen.Calling> { key ->
                                val conversationRepo = koinInject<ConversationRepository>()
                                val chatService = koinInject<ChatService>()
                                var assistantName by remember {
                                    mutableStateOf("")
                                }
                                LaunchedEffect(key.conversationId) {
                                    assistantName = runCatching {
                                        conversationRepo.getConversationById(Uuid.parse(key.conversationId))
                                            ?.assistantId
                                            ?.let { settings.getAssistantById(it)?.name }
                                    }.getOrNull().orEmpty()
                                }
                                // 触发 AI 自主决策（接/拒）
                                LaunchedEffect(key.conversationId) {
                                    runCatching {
                                        chatService.decideIncomingCall(Uuid.parse(key.conversationId))
                                    }
                                }
                                CallingPage(
                                    assistantName = assistantName,
                                    onCancel = { backStack.removeLastOrNull() }
                                )
                            }

                            entry<Screen.MiniAppManager> {
                                MiniAppManagerPage()
                            }

                            entry<Screen.MiniAppEdit> { key ->
                                MiniAppEditPage(id = key.id)
                            }

                            entry<Screen.MiniApp> { key ->
                                MiniAppPage(
                                    url = key.url,
                                    title = key.title,
                                )
                            }

                            entry<Screen.Legal> { key ->
                                LegalPage(
                                    titleRes = key.titleRes,
                                    contentRes = key.contentRes,
                                )
                            }

                        }
                    )
                    AnimatedVisibility(
                        visible = migrationState is MigrationState.Migrating,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val state = migrationState as? MigrationState.Migrating
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.db_migrating),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (state != null) {
                                    Text(
                                        text = "v${state.from} → v${state.to}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed interface Screen : NavKey {
    @Serializable
    data class Chat(
        val id: String,
        val text: String? = null,
        val files: List<String> = emptyList(),
        val nodeId: String? = null,
        val autoStartVoice: Boolean = false,
    ) : Screen

    @Serializable
    data class ShareHandler(val text: String, val streamUri: String? = null) : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object Favorite : Screen

    @Serializable
    data object Assistant : Screen

    @Serializable
    data class AssistantDetail(val id: String) : Screen

    @Serializable
    data class AssistantBasic(val id: String) : Screen

    @Serializable
    data class AssistantPrompt(val id: String) : Screen

    @Serializable
    data class AssistantMemory(val id: String) : Screen

    @Serializable
    data class AssistantRequest(val id: String) : Screen

    @Serializable
    data class AssistantMcp(val id: String) : Screen

    @Serializable
    data class AssistantLocalTool(val id: String) : Screen

    @Serializable
    data class AssistantInjections(val id: String) : Screen

    @Serializable
    data object Translator : Screen

    @Serializable
    data object Setting : Screen

    @Serializable
    data object Backup : Screen

    @Serializable
    data object ImageGen : Screen

    @Serializable
    data class WebView(val url: String = "", val content: String = "") : Screen

    @Serializable
    data object SettingDisplay : Screen

    @Serializable
    data object SettingTheme : Screen

    @Serializable
    data object SettingDisplayTheme : Screen

    @Serializable
    data object SettingDisplayColor : Screen

    @Serializable
    data object SettingDisplayTransparency : Screen

    @Serializable
    data object SettingDisplayIllustration : Screen

    @Serializable
    data object SettingDisplayMessage : Screen

    @Serializable
    data object SettingDisplayCodeInteraction : Screen

    @Serializable
    data object SettingDisplayGeneral : Screen

    @Serializable
    data object SettingDisplayNotification : Screen

    @Serializable
    data object SettingProvider : Screen

    @Serializable
    data class SettingProviderDetail(val providerId: String) : Screen

    @Serializable
    data object SettingModels : Screen

    @Serializable
    data object SettingAbout : Screen

    @Serializable
    data object SettingSearch : Screen

    @Serializable
    data class SettingSearchDetail(val serviceId: String) : Screen

    @Serializable
    data object SettingSpeech : Screen

    @Serializable
    data object SettingMcp : Screen

    @Serializable
    data object SettingDonate : Screen

    @Serializable
    data object SettingFiles : Screen

    @Serializable
    data object SettingWeb : Screen

    @Serializable
    data object Developer : Screen

    @Serializable
    data object Debug : Screen

    @Serializable
    data object Log : Screen

    @Serializable
    data object SecurityAudit : Screen

    @Serializable
    data object Extensions : Screen

    @Serializable
    data object QuickMessages : Screen

    @Serializable
    data object Prompts : Screen

    @Serializable
    data object Skills : Screen

    @Serializable
    data class SkillDetail(val skillName: String) : Screen

    @Serializable
    data object Workspaces : Screen

    @Serializable
    data class WorkspaceDetail(val id: String) : Screen

    @Serializable
    data class WorkspaceTerminal(val id: String) : Screen

    @Serializable
    data object MessageSearch : Screen

    @Serializable
    data object Stats : Screen

    @Serializable
    data object SettingSystemTools : Screen

    @Serializable
    data object SettingSecurity : Screen

    @Serializable
    data object SettingProactiveMessage : Screen

    @Serializable
    data object SettingWeixinBot : Screen

    @Serializable
    data object SettingQqBot : Screen

    @Serializable
    data object Health : Screen

    @Serializable
    data object SettingPlugins : Screen

    @Serializable
    data object Workflows : Screen

    @Serializable
    data class WorkflowDetail(val id: String) : Screen

    @Serializable
    data class PluginDetail(val pluginId: String) : Screen

    @Serializable
    data class PluginFolder(val folderId: String) : Screen

    @Serializable
    data class PluginWebView(val pluginId: String, val entryPath: String) : Screen

    @Serializable
    data class PluginDeclarativeUI(val pluginId: String) : Screen

    @Serializable
    data object MemoryBank : Screen

    @Serializable
    data object EmojiPicker : Screen

    @Serializable
    data object ExternalMemories : Screen

    @Serializable
    data class VoiceCall(val conversationId: String) : Screen

    /**
     * AI 主动发起语音通话时弹出的来电界面.
     * conversationId 用于接听后跳转到对应会话的通话页.
     */
    @Serializable
    data class IncomingCall(val conversationId: String) : Screen

    /**
     * 用户主动呼叫我，等待 AI 自主决定接/拒的"呼叫中"界面。
     */
    @Serializable
    data class Calling(val conversationId: String) : Screen

    @Serializable
    data object MiniAppManager : Screen

    @Serializable
    data class MiniAppEdit(val id: String?) : Screen

    @Serializable
    data class MiniApp(val url: String, val title: String?) : Screen

    @Serializable
    data class Legal(val titleRes: Int, val contentRes: Int) : Screen
}
