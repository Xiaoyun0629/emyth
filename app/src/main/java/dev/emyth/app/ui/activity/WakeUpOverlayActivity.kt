/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.activity

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.emyth.app.data.model.Avatar
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.datastore.getCurrentAssistant
import dev.emyth.app.data.wake.VoiceWakeStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.java.KoinJavaComponent.getKoin

/**
 * 语音唤醒后的 Siri 风弹窗：助手头像 + 名字 + 呼吸光圈动画，播放回应音频后自动打开 Emyth。
 */
class WakeUpOverlayActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, WakeUpOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // 忽略启动失败
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (name, avatar) = loadAssistant()
        setContent {
            WakeUpScreen(
                name = name,
                avatar = avatar,
                onDismiss = { dismiss() }
            )
        }
        playResponseAudio()
    }

    private fun loadAssistant(): Pair<String, Avatar> {
        return try {
            val store = getKoin().get<SettingsStore>()
            val settings = runBlocking { store.settingsFlow.first() }
            val assistant = settings.getCurrentAssistant()
            assistant.name to assistant.avatar
        } catch (e: Exception) {
            "助手" to Avatar.Dummy
        }
    }

    private fun playResponseAudio() {
        val paths = VoiceWakeStore.getResponsePaths(this)
        if (paths.isEmpty()) {
            scheduleOpenEmyth()
            return
        }
        val path = paths.random()
        try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setDataSource(path)
            mp.setOnCompletionListener { openEmyth() }
            mp.setOnErrorListener { _, _, _ -> openEmyth(); true }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            openEmyth()
        }
    }

    private fun scheduleOpenEmyth() {
        // 无音频时稍作停留再打开，避免一闪而过
        window.decorView.postDelayed({ openEmyth() }, 800L)
    }

    private fun openEmyth() {
        if (finished) return
        finished = true
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        try {
            val launch = packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(launch)
            }
        } catch (e: Exception) {
        }
        finish()
    }

    private fun dismiss() {
        openEmyth()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }
}

@androidx.compose.runtime.Composable
private fun WakeUpScreen(name: String, avatar: Avatar, onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "wake")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = { onDismiss() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 外圈光圈
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(scale + 0.15f)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                )
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                )
                // 头像
                when (avatar) {
                    is Avatar.Image -> AsyncImage(
                        model = avatar.url,
                        contentDescription = name,
                        modifier = Modifier
                            .size(110.dp)
                            .scale(scale)
                            .clip(CircleShape)
                    )
                    is Avatar.Emoji -> Text(
                        text = avatar.content,
                        fontSize = 64.sp,
                        modifier = Modifier.scale(scale)
                    )
                    else -> Text(
                        text = name.take(1),
                        fontSize = 64.sp,
                        color = Color.White,
                        modifier = Modifier.scale(scale)
                    )
                }
            }
            Text(
                text = name,
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(top = 18.dp)
            )
        }
    }
}
