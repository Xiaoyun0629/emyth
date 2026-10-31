package dev.emyth.app.data.themeshare

import android.content.Context
import dev.emyth.app.data.datastore.BubbleCornerStyle
import dev.emyth.app.data.datastore.ChatFontFamily
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.datastore.getCurrentAssistant
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent.getKoin
import java.io.File

/** 收集当前主题 -> 分享数据；分享数据 -> 还原主题。 */
object ThemeShareRepository {

    suspend fun export(context: Context): ThemeShareData {
        val store = getKoin().get<SettingsStore>()
        val settings = store.settingsFlow.first()
        val ds = settings.displaySetting
        val assistant = settings.getCurrentAssistant()
        return ThemeShareData(
            themeId = settings.themeId,
            userBubbleColor = ds.userBubbleColor,
            assistantBubbleColor = ds.assistantBubbleColor,
            bubbleCornerRadius = ds.bubbleCornerRadius,
            bubbleCornerStyle = ds.bubbleCornerStyle.name.lowercase(),
            bubbleImageOverlayEnabled = ds.bubbleImageOverlayEnabled,
            chatBubbleTransparency = ds.chatBubbleTransparency,
            chatTextColor = ds.chatTextColor,
            globalTextColor = ds.globalTextColor,
            chatFontFamily = ds.chatFontFamily.name.lowercase(),
            fontSizeRatio = ds.fontSizeRatio,
            avatarSize = ds.avatarSize,
            showUserAvatar = ds.showUserAvatar,
            showAssistantBubble = ds.showAssistantBubble,
            showModelIcon = ds.showModelIcon,
            showModelName = ds.showModelName,
            primaryColor = ds.primaryColor,
            inputFieldColor = ds.inputFieldColor,
            wallpaperBase64 = assistant.background?.let { ThemeShareCodec.fileToBase64(it) },
            userBubbleImageBase64 = ds.userBubbleImagePath.takeIf { it.isNotBlank() }?.let { ThemeShareCodec.fileToBase64(it) },
            assistantBubbleImageBase64 = ds.assistantBubbleImagePath.takeIf { it.isNotBlank() }?.let { ThemeShareCodec.fileToBase64(it) },
            userBubbleImageParams = ds.userBubbleImageParams,
            assistantBubbleImageParams = ds.assistantBubbleImageParams,
        )
    }

    suspend fun import(context: Context, data: ThemeShareData) {
        val store = getKoin().get<SettingsStore>()
        val dir = File(context.filesDir, "themeshare").apply { mkdirs() }
        val wallpaperPath = data.wallpaperBase64?.let { saveImage(dir, "wallpaper", it) }
        val userBubblePath = data.userBubbleImageBase64?.let { saveImage(dir, "user_bubble", it) }
        val aiBubblePath = data.assistantBubbleImageBase64?.let { saveImage(dir, "ai_bubble", it) }

        store.update { settings ->
            settings.copy(
                themeId = data.themeId.ifBlank { settings.themeId },
                displaySetting = settings.displaySetting.copy(
                    userBubbleColor = data.userBubbleColor,
                    assistantBubbleColor = data.assistantBubbleColor,
                    bubbleCornerRadius = data.bubbleCornerRadius,
                    bubbleCornerStyle = enumValueOf(data.bubbleCornerStyle, BubbleCornerStyle.SYMMETRIC),
                    bubbleImageOverlayEnabled = data.bubbleImageOverlayEnabled,
                    chatBubbleTransparency = data.chatBubbleTransparency,
                    chatTextColor = data.chatTextColor,
                    globalTextColor = data.globalTextColor,
                    chatFontFamily = enumValueOf(data.chatFontFamily, ChatFontFamily.DEFAULT),
                    fontSizeRatio = data.fontSizeRatio,
                    avatarSize = data.avatarSize,
                    showUserAvatar = data.showUserAvatar,
                    showAssistantBubble = data.showAssistantBubble,
                    showModelIcon = data.showModelIcon,
                    showModelName = data.showModelName,
                    primaryColor = data.primaryColor,
                    inputFieldColor = data.inputFieldColor,
                    userBubbleImagePath = userBubblePath ?: settings.displaySetting.userBubbleImagePath,
                    assistantBubbleImagePath = aiBubblePath ?: settings.displaySetting.assistantBubbleImagePath,
                    userBubbleImageParams = data.userBubbleImageParams,
                    assistantBubbleImageParams = data.assistantBubbleImageParams,
                ),
                assistants = settings.assistants.map { a ->
                    if (a.id == settings.assistantId && wallpaperPath != null) a.copy(background = wallpaperPath) else a
                }
            )
        }
    }

    private fun saveImage(dir: File, prefix: String, base64: String): String? {
        val f = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        return if (ThemeShareCodec.base64ToFile(base64, f.absolutePath)) f.absolutePath else null
    }

    private inline fun <reified T : Enum<T>> enumValueOf(name: String, default: T): T =
        runCatching { enumValueOf<T>(name.uppercase()) }.getOrDefault(default)
}
