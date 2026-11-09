/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dev.emyth.app.data.wake.VoiceWakeStore
import dev.emyth.app.data.wake.WakeWordDsp
import java.io.File
import kotlin.concurrent.thread

/**
 * 录制唤醒词：录音约 2 秒，提取 MFCC 特征存成模板。
 */
class VoiceWakeEnrollActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecord() else state = "需要麦克风权限才能录制"
        }

    private var state by mutableStateOf("点击下方按钮，然后对着手机清晰念出你的唤醒词")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = state, fontSize = 16.sp)
                Spacer(Modifier.height(28.dp))
                Button(onClick = { ensurePermissionAndRecord() }) {
                    Text("录制唤醒词（约 2 秒）")
                }
            }
        }
    }

    private fun ensurePermissionAndRecord() {
        if (Build.VERSION.SDK_INT >= 23 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startRecord()
        }
    }

    private fun startRecord() {
        state = "正在录音，请对着手机念出唤醒词…"
        thread {
            try {
                val sampleRate = WakeWordDsp.SAMPLE_RATE
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val record = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
                record.startRecording()
                val total = sampleRate * 2 // 2 秒
                val samples = ShortArray(total)
                var read = 0
                val buf = ShortArray(minBuf)
                while (read < total) {
                    val n = record.read(buf, 0, minOf(buf.size, total - read))
                    if (n <= 0) break
                    System.arraycopy(buf, 0, samples, read, n)
                    read += n
                }
                record.stop()
                record.release()

                if (read < sampleRate / 2) {
                    runOnUiThread { state = "录到的声音太短，请重试" }
                    return@thread
                }

                val valid = samples.copyOf(read)
                val frames = WakeWordDsp.extract(valid)
                if (frames.size < 3) {
                    runOnUiThread { state = "提取特征失败，请重试" }
                    return@thread
                }

                val file = File(filesDir, "wake_template.bin")
                WakeWordDsp.saveTemplate(file, frames)
                VoiceWakeStore.setTemplatePath(this, file.absolutePath)
                runOnUiThread { state = "唤醒词已录入 ✓（${frames.size} 帧）" }
            } catch (e: Exception) {
                runOnUiThread { state = "录制失败：${e.message}" }
            }
        }
    }
}
