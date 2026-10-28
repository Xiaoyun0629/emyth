/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.chat

import android.net.Uri
import android.content.Context
import android.graphics.BitmapFactory
import android.content.ContentResolver
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isFinite
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.emyth.app.ui.components.message.LiveBubbleBlurContext
import dev.emyth.app.ui.components.message.LocalLiveBubbleBlur
import dev.emyth.app.data.datastore.DisplayMaterialMode
import dev.emyth.app.ui.theme.LocalMaterialMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import dev.emyth.app.ui.components.ui.toComposeColor
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.LeftToRightListBullet
import me.rerere.hugeicons.stroke.Menu03
import me.rerere.hugeicons.stroke.MessageAdd01
import me.rerere.hugeicons.stroke.Voice
import dev.emyth.app.R
import dev.emyth.app.Screen
import dev.emyth.app.data.datastore.Settings
import dev.emyth.app.data.datastore.getAssistantById
import dev.emyth.app.data.datastore.findProvider
import dev.emyth.app.data.datastore.getCurrentAssistant
import dev.emyth.app.data.datastore.getCurrentChatModel
import dev.emyth.app.data.files.FilesManager
import dev.emyth.app.data.model.Conversation
import dev.emyth.app.service.ChatError
import dev.emyth.app.service.VoiceCallService
import dev.emyth.app.ui.components.ai.ChatInput
import dev.emyth.app.ui.context.LocalNavController
import dev.emyth.app.ui.context.LocalToaster
import dev.emyth.app.ui.context.Navigator
import dev.emyth.app.ui.hooks.ChatInputState
import dev.emyth.app.ui.hooks.EditStateContent
import dev.emyth.app.ui.hooks.useEditState
import dev.emyth.app.utils.base64Decode
import dev.emyth.app.utils.navigateToChatPage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

private suspend fun decodeChatBackgroundBitmap(
    context: Context,
    model: String,
): ImageBitmap? = withContext(Dispatchers.IO) {
    val uri = runCatching { Uri.parse(model) }.getOrNull()
    val bitmap = when {
        model.startsWith("/") -> {
            BitmapFactory.decodeFile(model)
        }

        uri?.scheme == ContentResolver.SCHEME_CONTENT ||
            uri?.scheme == ContentResolver.SCHEME_ANDROID_RESOURCE -> {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }

        uri?.scheme == ContentResolver.SCHEME_FILE -> {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: uri.path?.let(BitmapFactory::decodeFile)
        }

        uri?.scheme == "http" || uri?.scheme == "https" -> {
            loadChatBackgroundViaCoil(context, model)
        }

        else -> {
            BitmapFactory.decodeFile(model)
                ?: uri?.let {
                    runCatching {
                        context.contentResolver.openInputStream(it)?.use(BitmapFactory::decodeStream)
                    }.getOrNull()
                }
        }
    }
    bitmap?.asImageBitmap()
}

/**
 * 使用项目现有 Coil 3 ImageLoader 加载同一 model（与 Coil 显示背景使用同一套 fetcher/缓存）。
 * 调用方已处于 Dispatchers.IO，Coil 内部网络/解码不会阻塞主线程。
 */
private suspend fun loadChatBackgroundViaCoil(context: Context, model: String): Bitmap? {
    val loader = SingletonImageLoader.get(context)
    val request = ImageRequest.Builder(context)
        .data(model)
        // 硬件位图无法被 asImageBitmap/RenderEffect 采样，关闭硬件加速以保证可读
        .allowHardware(false)
        .build()
    val result = loader.execute(request)
    return (result.image as? BitmapImage)?.bitmap
}

@Composable
fun ChatPage(id: Uuid, text: String?, files: List<Uri>, nodeId: Uuid? = null, autoStartVoice: Boolean = false) {
    val vm: ChatVM = koinViewModel(
        parameters = {
            parametersOf(id.toString())
        }
    )
    val filesManager: FilesManager = koinInject()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val setting by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val loadingJob by vm.conversationJob.collectAsStateWithLifecycle()
    val processingStatus by vm.processingStatus.collectAsStateWithLifecycle()
    val currentChatModel by vm.currentChatModel.collectAsStateWithLifecycle()
    val enableWebSearch by vm.enableWebSearch.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    // Handle back press when drawer is open
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    // Hide keyboard when drawer is open
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            softwareKeyboardController?.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowDpSize()
    val isBigScreen =
        windowAdaptiveInfo.width > windowAdaptiveInfo.height && windowAdaptiveInfo.width >= 1100.dp

    val inputState = vm.inputState

    // 初始化输入状态（处理传入的 files 和 text 参数）
    LaunchedEffect(files, text) {
        if (files.isNotEmpty()) {
            val localFiles = filesManager.createChatFilesByContents(files)
            val contentTypes = files.mapNotNull { file ->
                filesManager.getFileMimeType(file)
            }
            val parts = buildList {
                localFiles.forEachIndexed { index, file ->
                    val type = contentTypes.getOrNull(index)
                    if (type?.startsWith("image/") == true) {
                        add(UIMessagePart.Image(url = file.toString()))
                    } else if (type?.startsWith("video/") == true) {
                        add(UIMessagePart.Video(url = file.toString()))
                    } else if (type?.startsWith("audio/") == true) {
                        add(UIMessagePart.Audio(url = file.toString()))
                    }
                }
            }
            inputState.messageContent = parts
        }
        text?.base64Decode()?.let { decodedText ->
            if (decodedText.isNotEmpty()) {
                inputState.setMessageText(decodedText)
            }
        }
    }

    val chatListState = rememberLazyListState()
    LaunchedEffect(nodeId, conversation.messageNodes.size) {
        if (!vm.chatListInitialized && conversation.messageNodes.isNotEmpty()) {
            if (nodeId != null) {
                val index = conversation.messageNodes.indexOfFirst { it.id == nodeId }
                if (index >= 0) {
                    chatListState.scrollToItem(index)
                }
            } else {
                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
            }
            vm.chatListInitialized = true
        }
    }

    when {
        isBigScreen -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = true,
                    autoStartVoice = autoStartVoice,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
        }

        else -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ChatDrawerContent(
                        navController = navController,
                        current = conversation,
                        vm = vm,
                        settings = setting
                    )
                }
            ) {
                ChatPageContent(
                    inputState = inputState,
                    loadingJob = loadingJob,
                    processingStatus = processingStatus,
                    setting = setting,
                    conversation = conversation,
                    drawerState = drawerState,
                    navController = navController,
                    vm = vm,
                    chatListState = chatListState,
                    enableWebSearch = enableWebSearch,
                    currentChatModel = currentChatModel,
                    bigScreen = false,
                    autoStartVoice = autoStartVoice,
                    errors = errors,
                    onDismissError = { vm.dismissError(it) },
                    onClearAllErrors = { vm.clearAllErrors() },
                )
            }
            BackHandler(drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }
        }
    }
}

@Composable
private fun ChatPageContent(
    inputState: ChatInputState,
    loadingJob: Job?,
    processingStatus: String? = null,
    setting: Settings,
    bigScreen: Boolean,
    conversation: Conversation,
    drawerState: DrawerState,
    navController: Navigator,
    vm: ChatVM,
    chatListState: LazyListState,
    enableWebSearch: Boolean,
    currentChatModel: Model?,
    autoStartVoice: Boolean = false,
    errors: List<ChatError>,
    onDismissError: (Uuid) -> Unit,
    onClearAllErrors: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var previewMode by rememberSaveable { mutableStateOf(false) }
    val hazeState = rememberHazeState()

    // ===== 普通聊天气泡实时背景模糊 =====
    val actualMaterialMode = LocalMaterialMode.current
    val enableLiveBubbleBlur =
        setting.displaySetting.interfaceRealtimeRendering &&
            setting.displaySetting.chatBubbleRealtimeBlur &&
            actualMaterialMode == DisplayMaterialMode.GLASS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val chatBackgroundPainter = rememberChatBackgroundPainter(setting)
    val chatBackgroundVisuals = rememberChatBackgroundVisuals(setting)
    val bubbleBlurBackgroundModel = setting.getCurrentAssistant().background
    val bubbleBlurContext = LocalContext.current.applicationContext
    var bubbleBlurBackgroundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(bubbleBlurBackgroundModel) {
        if (bubbleBlurBackgroundModel != null) {
            bubbleBlurBackgroundBitmap = runCatching {
                decodeChatBackgroundBitmap(bubbleBlurContext, bubbleBlurBackgroundModel)
            }.getOrNull()
        } else {
            bubbleBlurBackgroundBitmap = null
        }
    }
    var bubbleBlurBackgroundOrigin by remember { mutableStateOf(Offset.Unspecified) }
    var bubbleBlurBackgroundSize by remember { mutableStateOf(Size.Unspecified) }
    val bubbleBlurRadiusPx = with(LocalDensity.current) { 5.dp.toPx() }

    TTSAutoPlay(vm = vm, setting = setting, conversation = conversation)

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInWindow()
                    if (pos.isFinite && coordinates.size.width > 0 && coordinates.size.height > 0) {
                        bubbleBlurBackgroundOrigin = pos
                        bubbleBlurBackgroundSize = Size(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat(),
                        )
                    }
                }
        ) {
            CompositionLocalProvider(
                LocalLiveBubbleBlur provides LiveBubbleBlurContext(
                    imageBitmap = if (enableLiveBubbleBlur) bubbleBlurBackgroundBitmap else null,
                    backgroundOriginInWindow = bubbleBlurBackgroundOrigin,
                    backgroundSizePx = bubbleBlurBackgroundSize,
                    baseColor = chatBackgroundVisuals.baseColor,
                    imageAlpha = chatBackgroundVisuals.imageAlpha,
                    gradientTopAlpha = chatBackgroundVisuals.gradientTopAlpha,
                    gradientBottomAlpha = chatBackgroundVisuals.gradientBottomAlpha,
                    enabled = enableLiveBubbleBlur,
                    radiusPx = bubbleBlurRadiusPx,
                )
            ) {
                AssistantBackground(setting = setting, backgroundPainter = chatBackgroundPainter)
        Scaffold(
            topBar = {
                TopBar(
                    settings = setting,
                    conversation = conversation,
                    bigScreen = bigScreen,
                    drawerState = drawerState,
                    previewMode = previewMode,
                    onNewChat = {
                        navigateToChatPage(navController)
                    },
                    onClickMenu = {
                        previewMode = !previewMode
                    },
                    onUpdateTitle = {
                        vm.updateTitle(it)
                    },
                    onVoiceCall = {
                        val activeId = VoiceCallService.activeConversationId.value
                        when {
                            activeId == null -> navController.navigate(
                                Screen.Calling(conversation.id.toString())
                            )
                            activeId == conversation.id.toString() -> navController.navigate(
                                Screen.VoiceCall(conversation.id.toString())
                            )
                            else -> {
                                toaster.show("当前有通话进行中，请先挂断", type = ToastType.Warning)
                            }
                        }
                    },
                )
            },
            bottomBar = {
                val lockChat = setting.lockChatSetting
                var lockRemaining by remember(lockChat.lockedUntilMillis) {
                    mutableStateOf(lockChat.lockedUntilMillis - System.currentTimeMillis())
                }
                if (lockRemaining > 0) {
                    LaunchedEffect(lockChat.lockedUntilMillis) {
                        while (lockRemaining > 0) {
                            delay(1000)
                            lockRemaining = lockChat.lockedUntilMillis - System.currentTimeMillis()
                        }
                    }
                    LockChatBar(
                        remaining = lockRemaining,
                        message = lockChat.lockMessage,
                        backgroundColor = lockChat.lockBackgroundColor,
                        onRequestUnlock = {
                            vm.handleMessageSend(listOf(UIMessagePart.Text("我申请解除锁定")))
                        }
                    )
                } else {
                    ChatInput(
                    state = inputState,
                    loading = loadingJob != null,
                    settings = setting,
                    conversation = conversation,
                    mcpManager = vm.mcpManager,
                    hazeState = hazeState,
                    autoStartVoice = autoStartVoice,
                    onCancelClick = {
                        vm.stopGeneration()
                    },
                    enableSearch = enableWebSearch,
                    onToggleSearch = {
                        vm.updateSettings(setting.copy(enableWebSearch = !enableWebSearch))
                    },
                    onSendClick = {
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(inputState.getContents())
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onVoiceMessage = { url, duration, transcript ->
                        if (currentChatModel == null) {
                            toaster.show("请先选择模型", type = ToastType.Error)
                            return@ChatInput
                        }
                        vm.handleMessageSend(
                            listOf(
                                UIMessagePart.VoiceMessage(
                                    url = url,
                                    duration = duration,
                                    transcript = transcript,
                                )
                            )
                        )
                        scope.launch {
                            chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                        }
                    },
                    onLongSendClick = {
                        if (inputState.isEditing()) {
                            vm.handleMessageEdit(
                                parts = inputState.getContents(),
                                messageId = inputState.editingMessage!!,
                            )
                        } else {
                            vm.handleMessageSend(content = inputState.getContents(), answer = false)
                            scope.launch {
                                chatListState.requestScrollToItem(conversation.currentMessages.size + 5)
                            }
                        }
                        inputState.clearInput()
                    },
                    onUpdateChatModel = {
                        vm.setChatModel(assistant = setting.getCurrentAssistant(), model = it)
                    },
                    onUpdateAssistant = {
                        vm.updateSettings(
                            setting.copy(
                                assistants = setting.assistants.map { assistant ->
                                    if (assistant.id == it.id) {
                                        it
                                    } else {
                                        assistant
                                    }
                                }
                            )
                        )
                    },
                    onUpdateSearchService = { index ->
                        vm.updateSettings(
                            setting.copy(
                                searchServiceSelected = index
                            )
                        )
                    },
                    onCompressContext = { additionalPrompt, targetTokens, keepRecentMessages ->
                        vm.handleCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
                    },
                    )
                }
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            ChatList(
                innerPadding = innerPadding,
                conversation = conversation,
                state = chatListState,
                loading = loadingJob != null,
                processingStatus = processingStatus,
                previewMode = previewMode,
                settings = setting,
                hazeState = hazeState,
                errors = errors,
                onDismissError = onDismissError,
                onClearAllErrors = onClearAllErrors,
                onRegenerate = {
                    vm.regenerateAtMessage(it)
                },
                onEdit = {
                    inputState.editingMessage = it.id
                    inputState.setContents(it.parts)
                },
                onForkMessage = {
                    scope.launch {
                        val fork = vm.forkMessage(message = it)
                        navigateToChatPage(navController, chatId = fork.id)
                    }
                },
                onDelete = {
                    if (loadingJob != null) {
                        vm.showDeleteBlockedWhileGeneratingError()
                    } else {
                        vm.deleteMessage(it)
                    }
                },
                onUpdateMessage = { newNode ->
                    vm.updateConversation(
                        conversation.copy(
                            messageNodes = conversation.messageNodes.map { node ->
                                if (node.id == newNode.id) {
                                    newNode
                                } else {
                                    node
                                }
                            }
                        ))
                    vm.saveConversationAsync()
                },
                onClickSuggestion = { suggestion ->
                    inputState.editingMessage = null
                    inputState.setMessageText(suggestion)
                },
                onTranslate = { message, locale ->
                    vm.translateMessage(message, locale)
                },
                onClearTranslation = { message ->
                    vm.clearTranslationField(message.id)
                },
                onJumpToMessage = { index ->
                    previewMode = false
                    scope.launch {
                        chatListState.animateScrollToItem(index)
                    }
                },
                onToolApproval = { toolCallId, approved, reason ->
                    vm.handleToolApproval(toolCallId, approved, reason)
                },
                onToolAnswer = { toolCallId, answer ->
                    vm.handleToolAnswer(toolCallId, answer)
                },
                onToggleFavorite = { node ->
                    vm.toggleMessageFavorite(node)
                },
                onConversationSystemPromptChange = { newPrompt ->
                    vm.updateConversation(conversation.copy(customSystemPrompt = newPrompt))
                    vm.saveConversationAsync()
                },
            )
        }
    }
            }
        }
}

@Composable
private fun TopBar(
    settings: Settings,
    conversation: Conversation,
    drawerState: DrawerState,
    bigScreen: Boolean,
    previewMode: Boolean,
    onClickMenu: () -> Unit,
    onNewChat: () -> Unit,
    onUpdateTitle: (String) -> Unit,
    onVoiceCall: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val titleState = useEditState<String> {
        onUpdateTitle(it)
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        navigationIcon = {
            if (!bigScreen) {
                IconButton(
                    onClick = {
                        scope.launch { drawerState.open() }
                    }
                ) {
                    Icon(HugeIcons.Menu03, "Messages")
                }
            }
        },
        title = {
            val editTitleWarning = stringResource(R.string.chat_page_edit_title_warning)
            Surface(
                onClick = {
                    if (conversation.messageNodes.isNotEmpty()) {
                        titleState.open(conversation.title)
                    } else {
                        toaster.show(editTitleWarning, type = ToastType.Warning)
                    }
                },
                color = Color.Transparent,
            ) {
                Column {
                    val assistant = settings.getCurrentAssistant()
                    val model = settings.getCurrentChatModel()
                    val provider = model?.findProvider(providers = settings.providers, checkOverwrite = false)
                    Text(
                        text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_chat) },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (model != null && provider != null) {
                        Text(
                            text = "${assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) }} / ${model.displayName} (${provider.name})",
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.65f),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                            )
                        )
                    }
                }
            }
        },
        actions = {
            IconButton(
                onClick = {
                    onVoiceCall()
                }
            ) {
                Icon(HugeIcons.Voice, "Voice Call")
            }

            IconButton(
                onClick = {
                    onClickMenu()
                }
            ) {
                Icon(if (previewMode) HugeIcons.Cancel01 else HugeIcons.LeftToRightListBullet, "Chat Options")
            }

            IconButton(
                onClick = {
                    onNewChat()
                }
            ) {
                Icon(HugeIcons.MessageAdd01, "New Message")
            }
        },
    )
    titleState.EditStateContent { title, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                titleState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_title))
            },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        titleState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        titleState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.chat_page_cancel))
                }
            }
        )
    }
}

@Composable
private fun LockChatBar(remaining: Long, message: String, backgroundColor: Long? = null, onRequestUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(backgroundColor?.toComposeColor() ?: MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message.ifBlank { "该睡觉啦，明天再来找我" },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "距离解锁还有 ${formatLockRemaining(remaining)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRequestUnlock) {
            Text("申请解除")
        }
    }
}

private fun formatLockRemaining(ms: Long): String {
    if (ms <= 0) return "0 秒"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h} 小时 ${m} 分"
        m > 0 -> "${m} 分 ${s} 秒"
        else -> "${s} 秒"
    }
}
