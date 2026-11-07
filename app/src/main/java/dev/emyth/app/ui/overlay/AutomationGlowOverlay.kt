/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * 光圈悬浮窗：AI 操控手机（调用屏幕操作工具）时，在屏幕边缘亮一圈光，表示「机正在操控」。
 * 纯 UI 状态指示，不拦截任何触摸，到点自动熄灭。
 */
object AutomationGlowOverlay {

    private const val DEFAULT_AUTO_HIDE_MS = 2500L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var hideRunnable: Runnable? = null

    private val hideTask = Runnable { hideInternal() }

    /** 显示光圈。color 为 ARGB Int。autoHideMs 到点自动熄灭。 */
    @Synchronized
    fun show(context: Context, color: Int, autoHideMs: Long = DEFAULT_AUTO_HIDE_MS) {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) return

        hideInternal()

        val view = GlowView(appContext, color)
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(view, lp)
            overlayView = view
            windowManager = wm
        } catch (e: Exception) {
            android.util.Log.e("AutomationGlow", "addView 失败", e)
            return
        }

        mainHandler.removeCallbacks(hideTask)
        mainHandler.postDelayed(hideTask, autoHideMs)
    }

    /** 立即熄灭光圈。 */
    @Synchronized
    fun hide() {
        hideInternal()
    }

    private fun hideInternal() {
        mainHandler.removeCallbacks(hideTask)
        val view = overlayView
        val wm = windowManager
        if (view != null && wm != null) {
            try {
                wm.removeView(view)
            } catch (e: Exception) {
                android.util.Log.e("AutomationGlow", "removeView 失败", e)
            }
        }
        overlayView = null
        windowManager = null
    }

    /**
     * 光圈 View：用三层描边模拟发光——外层粗半透明（光晕）、中层、内层细实线（光圈本体）。
     */
    private class GlowView(context: Context, glowColor: Int) : View(context) {

        private val glow = glowColor

        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 28f
            color = glow
            alpha = 36
        }

        private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = glow
            alpha = 110
        }

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = glow
            alpha = 210
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rect = RectF(14f, 14f, width - 14f, height - 14f)
            val radius = 28f
            canvas.drawRoundRect(rect, radius, radius, outerPaint)
            canvas.drawRoundRect(rect, radius, radius, midPaint)
            canvas.drawRoundRect(rect, radius, radius, corePaint)
        }
    }
}
