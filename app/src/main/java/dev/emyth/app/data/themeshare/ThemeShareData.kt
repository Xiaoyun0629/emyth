package dev.emyth.app.data.themeshare

import kotlinx.serialization.Serializable
import dev.emyth.app.data.datastore.BubbleImageStyleParams

/**
 * 一套主题的分享数据（序列化成 JSON 后 gzip+base64 成分享码）。
 * 图片以 base64 字符串内嵌，纯本地编解码，不依赖服务器。
 */
@Serializable
data class ThemeShareData(
    val version: Int = 1,
    // 主题预设
    val themeId: String = "",
    // 气泡
    val userBubbleColor: Long? = null,
    val assistantBubbleColor: Long? = null,
    val bubbleCornerRadius: Float = 16f,
    val bubbleCornerStyle: String = "symmetric", // symmetric / asymmetric
    val bubbleImageOverlayEnabled: Boolean = false,
    val chatBubbleTransparency: Float = 0f,
    // 文字
    val chatTextColor: Long? = null,
    val globalTextColor: Long? = null,
    val chatFontFamily: String = "default",
    val fontSizeRatio: Float = 1f,
    // 头像 / 显示
    val avatarSize: Float = 36f,
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    // 颜色
    val primaryColor: Long? = null,
    val inputFieldColor: Long? = null,
    // 图片（base64，可为空）
    val wallpaperBase64: String? = null,
    val userBubbleImageBase64: String? = null,
    val assistantBubbleImageBase64: String? = null,
    // 九宫格气泡图参数
    val userBubbleImageParams: BubbleImageStyleParams = BubbleImageStyleParams(),
    val assistantBubbleImageParams: BubbleImageStyleParams = BubbleImageStyleParams(),
)
