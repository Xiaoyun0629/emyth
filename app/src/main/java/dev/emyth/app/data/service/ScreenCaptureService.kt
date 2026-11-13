/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.emyth.app.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import dev.emyth.app.R

/**
 * 看屏前台服务：MediaProjection 在 Android 10+ 必须由一个 foregroundServiceType="mediaProjection"
 * 的前台服务承载，否则 getMediaProjection 会抛 SecurityException。本服务先 startForeground，再建 MediaProjection。
 */
class ScreenCaptureService : Service() {

    companion object {
        const val NOTIFICATION_ID = 20006
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"

        /** 把授权结果丢给前台服务，由服务建立 MediaProjection。 */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e("ScreenCaptureService", "startForegroundService failed", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 关键顺序：必须先 startForeground(mediaProjection 类型)，再 getMediaProjection。
        // Android 10(API 29) 起 MediaProjection 就要求前台服务带 mediaProjection 类型，不只 14+
        startForegroundCompat()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = readDataExtra(intent)
        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mp = mpm.getMediaProjection(resultCode, data)
                if (mp != null) {
                    ScreenCaptureManager.start(this, mp)
                } else {
                    Log.e("ScreenCaptureService", "getMediaProjection returned null")
                    ScreenCaptureManager.markAuthDenied()
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e("ScreenCaptureService", "getMediaProjection failed", e)
                ScreenCaptureManager.markAuthDenied()
                stopSelf()
            }
        } else {
            ScreenCaptureManager.markAuthDenied()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenCaptureManager.stop()
    }

    private fun startForegroundCompat() {
        // 通用写法：ServiceCompat 内部处理版本差异。
        // API 29+ 会用 mediaProjection 类型（MediaProjection 硬性要求），API 29- 忽略该类型。
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun buildNotification(): Notification {
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle("看屏")
            .setContentText("看屏运行中（录屏授权已生效）")
            .setContentIntent(launchPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun readDataExtra(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }
}
