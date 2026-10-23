/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.ui.toComposeColor
import androidx.compose.ui.graphics.toArgb
import org.koin.java.KoinJavaComponent.getKoin

/**
 * 任务弹窗服务：小悬浮条浮在屏幕顶部，显示任务内容 + 倒计时 + 完成按钮。
 * 不抢焦点（FLAG_NOT_FOCUSABLE），底下对话框照常可见、可打字。
 * 用户点「完成」后状态变 submitted，等 AI 审核；AI 审核通过后发 ACTION_DISMISS 移除。
 */
class TaskOverlayService : Service() {

    companion object {
        private const val TAG = "TaskOverlayService"
        const val ACTION_DISMISS = "me.rerere.rikkahub.DISMISS_TASK_OVERLAY"
        private const val FOREGROUND_NOTIF_ID = 20004

        fun show(context: Context) {
            val intent = Intent(context, TaskOverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun dismiss(context: Context) {
            val intent = Intent(context, TaskOverlayService::class.java).apply {
                action = ACTION_DISMISS
            }
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var countdownText: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        removeOverlayInternal()
        showOverlay()
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        removeOverlayInternal()
    }

    private fun startForegroundCompat() {
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("橘瓣有任务等你完成…")
            .setSmallIcon(R.drawable.small_icon)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FOREGROUND_NOTIF_ID, notification)
        }
    }

    private fun showOverlay() {
        val task = getKoin().get<SettingsStore>().settingsFlow.value.taskSetting
        val bgColor = task.backgroundColor?.toComposeColor()?.toArgb()
            ?: android.graphics.Color.parseColor("#FF2D2D3A")
        val btnColor = task.buttonColor?.toComposeColor()?.toArgb()
            ?: android.graphics.Color.parseColor("#FF7C5CFF")
        val cdColor = task.countdownColor?.toComposeColor()?.toArgb()
            ?: android.graphics.Color.parseColor("#FFFF5C5C")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = dp(12)
            setPadding(pad, dp(10), pad, dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(bgColor)
            }
        }

        val taskText = TextView(this).apply {
            text = task.content
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            maxWidth = dp(220)
            setPadding(0, 0, dp(10), 0)
        }

        countdownText = TextView(this).apply {
            setTextColor(cdColor)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, dp(10), 0)
        }

        val doneButton = TextView(this).apply {
            text = "完成"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(btnColor)
            }
            setOnClickListener {
                scope.launch {
                    val store = getKoin().get<SettingsStore>()
                    store.update { s ->
                        s.copy(taskSetting = s.taskSetting.copy(
                            submission = "已提交（内容在聊天里补充）",
                            submittedAt = System.currentTimeMillis(),
                            status = "submitted",
                        ))
                    }
                }
                stopSelf()
            }
        }

        container.addView(taskText)
        container.addView(countdownText)
        container.addView(doneButton)

        overlayView = container
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(60)
        }

        try {
            windowManager.addView(container, layoutParams)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "addView 失败", e)
        }

        updateCountdown()
    }

    private fun updateCountdown() {
        val task = getKoin().get<SettingsStore>().settingsFlow.value.taskSetting
        val remaining = task.deadlineMillis - System.currentTimeMillis()
        val text = if (task.status == "pending") {
            if (remaining > 0) {
                val m = (remaining / 60_000).coerceAtLeast(0)
                val s = ((remaining % 60_000) / 1000).coerceAtLeast(0)
                "剩 ${m}分${s.toString().padStart(2, '0')}秒"
            } else {
                "已超时"
            }
        } else {
            ""
        }
        countdownText?.text = text
        // 超时或已审核完，自动移除
        val shouldRemove = task.status == "approved" || task.status == "none" ||
            (task.status == "pending" && remaining <= 0 && task.deadlineMillis > 0)
        if (shouldRemove) {
            stopSelf()
        }
    }

    private fun removeOverlayInternal() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {
            }
            overlayView = null
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
