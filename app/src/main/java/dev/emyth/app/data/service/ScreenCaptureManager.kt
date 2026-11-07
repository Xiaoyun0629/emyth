/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer

/**
 * 屏幕捕获管理器：用 MediaProjection 录屏授权，按需抓帧，配合视觉模型「看见」做了无障碍屏蔽、读屏读不到的界面。
 * MediaProjection 必须由 ScreenCaptureService（foregroundServiceType=mediaProjection 前台服务）创建后传入。
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensityDpi = 0

    /** 是否已弹出授权框、正在等用户点「允许」。避免 read_screen 反复弹框造成死循环。 */
    @Volatile
    private var awaitingAuth = false

    /**
     * 代际计数：每次 start/stop/清理都自增。
     * 关键作用：旧的 MediaProjection 被 stop() 时，它的 onStop 回调是延迟到主线程执行的；
     * 若不加代际判断，这个迟到的回调会把「后来新建的 MediaProjection」也一起清掉，导致二次授权后永远建不起来。
     */
    private var generation = 0

    /** 是否已授权并持有 MediaProjection。 */
    @Synchronized
    fun isReady(): Boolean = mediaProjection != null && imageReader != null

    /** 是否正在等待用户授权（已弹过框，别再弹）。 */
    @Synchronized
    fun isAwaitingAuth(): Boolean = awaitingAuth

    /** 标记「已弹出授权框」，在授权结果回来前不再重复弹。 */
    @Synchronized
    fun markAwaitingAuth() {
        awaitingAuth = true
    }

    /**
     * 用前台服务已拿到的 MediaProjection 建立 ImageReader + VirtualDisplay。
     */
    @Synchronized
    fun start(context: Context, mp: MediaProjection) {
        stopInternal()
        val myGen = generation
        try {
            val appContext = context.applicationContext
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensityDpi = metrics.densityDpi

            val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val vd = mp.createVirtualDisplay(
                "EmythScreenCapture",
                screenWidth,
                screenHeight,
                screenDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                null
            )

            mediaProjection = mp
            imageReader = reader
            virtualDisplay = vd
            awaitingAuth = false
            android.util.Log.i(TAG, "Screen capture started: ${screenWidth}x${screenHeight}@${screenDensityDpi}")

            // 用户从状态栏手动停止录屏 / 投影被系统回收时，清理资源。代际判断防止旧回调误清理新资源。
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    mainHandler.post { onStopForGeneration(myGen) }
                }
            }, mainHandler)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "start capture failed", e)
            stopInternal()
            awaitingAuth = false
        }
    }

    /** 只有当前代际的投影 onStop 才真正清理资源，过时的回调直接忽略。 */
    @Synchronized
    private fun onStopForGeneration(myGen: Int) {
        if (generation != myGen) return
        generation++
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        awaitingAuth = false
        android.util.Log.i(TAG, "Screen capture stopped (generation $myGen)")
    }

    /** 抓一帧屏幕，转成 Bitmap。 */
    @Synchronized
    fun captureScreen(): Bitmap? {
        val reader = imageReader ?: return null
        return try {
            val image = reader.acquireLatestImage() ?: return null
            try {
                val plane = image.planes[0]
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bmp = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(buffer)
                // 裁掉 rowPadding 造成的右侧空白
                Bitmap.createBitmap(bmp, 0, 0, screenWidth, screenHeight)
            } finally {
                image.close()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "captureScreen failed", e)
            null
        }
    }

    /** 释放资源。 */
    @Synchronized
    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        generation++
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        awaitingAuth = false
    }
}
