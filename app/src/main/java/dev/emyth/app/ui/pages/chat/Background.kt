/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import dev.emyth.app.data.datastore.Settings
import dev.emyth.app.ui.components.ui.toComposeColor
import dev.emyth.app.data.datastore.getCurrentAssistant

/** 聊天图片背景的共享视觉参数（气泡实时模糊与页面背景共用，保证逐像素一致） */
data class ChatBackgroundVisuals(
    val baseColor: Color,
    val imageAlpha: Float,
    val gradientTopAlpha: Float = 0.2f,
    val gradientBottomAlpha: Float = 0.5f,
) {
    companion object {
        val Unspecified = ChatBackgroundVisuals(baseColor = Color.Unspecified, imageAlpha = 1f)
    }
}

/** 仅当聊天背景为图片时返回可共享的 Painter（Coil 缓存，不重复加载）；纯色/无背景返回 null */
@Composable
fun rememberChatBackgroundPainter(setting: Settings): Painter? {
    val assistant = setting.getCurrentAssistant()
    return if (assistant.background != null) {
        rememberAsyncImagePainter(model = assistant.background)
    } else {
        null
    }
}

/** 计算与 AssistantBackground 完全一致的背景视觉参数（主题实时值） */
@Composable
fun rememberChatBackgroundVisuals(setting: Settings): ChatBackgroundVisuals {
    val assistant = setting.getCurrentAssistant()
    val baseColor = setting.displaySetting.chatBackgroundColor?.let { it.toComposeColor() }
        ?: MaterialTheme.colorScheme.background
    return ChatBackgroundVisuals(
        baseColor = baseColor,
        imageAlpha = assistant.backgroundOpacity.coerceIn(0f, 1f),
    )
}

@Composable
fun AssistantBackground(
    setting: Settings,
    backgroundPainter: Painter? = null,
) {
    val assistant = setting.getCurrentAssistant()
    val sharedPainter = backgroundPainter ?: rememberChatBackgroundPainter(setting)
    val visuals = rememberChatBackgroundVisuals(setting)
    val chatBackgroundColor = setting.displaySetting.chatBackgroundColor?.let { it.toComposeColor() }

    when {
        assistant.background != null && sharedPainter != null -> {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(visuals.imageAlpha)
                        .paint(
                            painter = sharedPainter,
                            contentScale = ContentScale.Crop,
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    visuals.baseColor.copy(alpha = visuals.gradientTopAlpha),
                                    visuals.baseColor.copy(alpha = visuals.gradientBottomAlpha)
                                )
                            )
                        )
                )
            }
        }

        chatBackgroundColor != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(chatBackgroundColor)
            )
        }
    }
}
