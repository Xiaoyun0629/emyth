/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.pages.voice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01

private val ColorCalling = Color(0xFFFDAE4F) // 呼叫中主色: 琥珀
private val ColorBgCalling = Color(0xFF241A0B) // 暖深褐底

/**
 * 呼叫中界面（用户主动呼叫我，等待 AI 自主决定接/拒）。
 * 取消按钮 = 挂断这次呼叫。
 */
@Composable
fun CallingPage(
    assistantName: String,
    onCancel: () -> Unit,
) {
    // 返回键 = 取消呼叫
    BackHandler { onCancel() }

    val accentColor by animateColorAsState(
        targetValue = ColorCalling,
        animationSpec = tween(durationMillis = 600),
        label = "callingAccent"
    )

    // 呼叫中脉动
    val transition = rememberInfiniteTransition(label = "calling_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBgCalling)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.4f),
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width / 2f,
                            size.height * 0.38f
                        ),
                        radius = size.maxDimension * 0.7f
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部: 呼叫提示
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 90.dp)
            ) {
                Text(
                    text = "正在呼叫…",
                    color = accentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = assistantName.ifBlank { "AI 助手" },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 中部: 呼叫光球（借用 Processing 呼吸节奏表达"等待接听"）
            VoiceOrb(
                amplitudes = emptyList(),
                status = VoiceCallStatus.Processing,
                baseColor = accentColor,
                size = 220.dp,
            )

            // 底部: 取消
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 72.dp)
            ) {
                Surface(
                    onClick = onCancel,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = HugeIcons.Cancel01,
                            contentDescription = "取消呼叫",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = "取消",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
