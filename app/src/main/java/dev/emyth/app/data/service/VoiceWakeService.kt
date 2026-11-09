/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.emyth.app.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import dev.emyth.app.R
import dev.emyth.app.data.wake.VoiceWakeStore
import dev.emyth.app.data.wake.WakeWordDsp
import dev.emyth.app.ui.activity.WakeUpOverlayActivity
import java.io.File

/**
 * 语音唤醒前台服务：持续录音，本地 MFCC + DTW 匹配唤醒词，命中后弹 Siri 风弹窗。
 */
class VoiceWakeService : Service() {

    companion object {
        const val NOTIFICATION_ID = 20007
        private const val TAG = "VoiceWakeService"
        /** DTW 相似度阈值，命中即唤醒（余弦相似度 0~1，越高越像，后续可调） */
        private const val THRESHOLD = 0.80
        private const val COOLDOWN_MS = 5000L

        fun start(context: Context) {
            val intent = Intent(context, VoiceWakeService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceWakeService::class.java))
        }
    }

    private var audioRecord: AudioRecord? = null
    private var running = false
    private var recordThread: Thread? = null
    private var lastTriggerAt = 0L
    private var lastLogAt = 0L
    private var template: List<DoubleArray>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        VoiceWakeStore.addLog(this, "监听服务启动")
        startRecording()
        return START_STICKY
    }

    private fun startRecording() {
        if (running) return
        val tplPath = VoiceWakeStore.getTemplatePath(this)
        val tpl = WakeWordDsp.loadTemplate(File(tplPath))
        if (tpl.isEmpty()) {
            Log.w(TAG, "没有唤醒词模板，服务停止")
            VoiceWakeStore.addLog(this, "没有唤醒词模板，服务停止（请先录唤醒词）")
            stopSelf()
            return
        }
        template = tpl
        VoiceWakeStore.addLog(this, "已加载唤醒词模板（${tpl.size} 帧），开始监听")
        running = true
        recordThread = Thread(::recordLoop, "VoiceWakeRecord")
        recordThread?.start()
    }

    private fun recordLoop() {
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                WakeWordDsp.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                WakeWordDsp.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            audioRecord = record
            record.startRecording()
            VoiceWakeStore.addLog(this@VoiceWakeService, "麦克风录音已启动")

            // 环形窗口：最近 2 秒
            val windowSize = WakeWordDsp.SAMPLE_RATE * 2
            val window = ShortArray(windowSize)
            var writePos = 0
            val buf = ShortArray(WakeWordDsp.SAMPLE_RATE / 10) // 每次读 0.1 秒
            var lastCheck = 0L

            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                for (i in 0 until n) {
                    window[writePos] = buf[i]
                    writePos = (writePos + 1) % windowSize
                }
                val now = System.currentTimeMillis()
                if (now - lastCheck >= 400) {
                    lastCheck = now
                    checkWindow(window, writePos)
                }
            }
            runCatching { record.stop() }
            record.release()
        } catch (e: Exception) {
            Log.e(TAG, "recordLoop error", e)
            VoiceWakeStore.addLog(this@VoiceWakeService, "录音异常：${e.message}")
        }
    }

    private fun checkWindow(window: ShortArray, writePos: Int) {
        val tpl = template ?: return
        // 取最近 1.5 秒
        val len = WakeWordDsp.SAMPLE_RATE * 3 / 2
        val samples = ShortArray(len)
        for (i in 0 until len) {
            val idx = (writePos - len + i + window.size) % window.size
            samples[i] = window[idx]
        }
        val frames = WakeWordDsp.extract(samples)
        if (frames.size < 3) return
        val sim = WakeWordDsp.dtwSimilarity(tpl, frames)
        if (sim > 0.60) {
            val now = System.currentTimeMillis()
            if (now - lastLogAt > 1500) {
                lastLogAt = now
                VoiceWakeStore.addLog(this@VoiceWakeService, "检测到声音，相似度 %.2f（阈值 $THRESHOLD）".format(sim))
            }
        }
        if (sim > THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastTriggerAt > COOLDOWN_MS) {
                lastTriggerAt = now
                Log.i(TAG, "唤醒词命中，相似度=$sim")
                VoiceWakeStore.addLog(this@VoiceWakeService, "✅ 唤醒词命中，相似度 %.2f".format(sim))
                triggerWake()
            }
        }
    }

    private fun triggerWake() {
        try {
            WakeUpOverlayActivity.start(this)
        } catch (e: Exception) {
            Log.e(TAG, "triggerWake failed", e)
        }
    }

    private fun stopRecording() {
        running = false
        audioRecord?.let { runCatching { it.stop() } }
        recordThread?.interrupt()
    }

    override fun onDestroy() {
        super.onDestroy()
        VoiceWakeStore.addLog(this, "监听服务停止")
        stopRecording()
    }

    private fun startForegroundCompat() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    private fun buildNotification(): Notification {
        val launchPendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("语音唤醒")
            .setContentText("正在监听唤醒词…")
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
