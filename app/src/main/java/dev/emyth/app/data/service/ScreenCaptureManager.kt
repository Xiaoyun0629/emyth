/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer

/**
 * 屏幕捕获管理器：用 MediaProjection 录屏授权，按需抓帧，配合视觉模型「看见」做了无障碍屏蔽的界面（如微信）。
 * 注意：MediaProjection 需要用户授权一次；Android 14+ 会有录屏指示器；app 进程被杀后需重新授权。
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

    /** 是否已授权并持有 MediaProjection。 */
    @Synchronized
    fun isReady(): Boolean = mediaProjection != null && imageReader != null

    /** 授权回调：拿到 resultCode + data 后调用，建立 ImageReader + VirtualDisplay。 */
    @Synchronized
    fun start(context: Context, resultCode: Int, data: Intent) {
        stopInternal()
        try {
            val appContext = context.applicationContext
            val mpm = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = mpm.getMediaProjection(resultCode, data)

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
            android.util.Log.i(TAG, "Screen capture started: ${screenWidth}x${screenHeight}@${screenDensityDpi}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "start capture failed", e)
            stopInternal()
        }
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
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        try {
            imageReader?.close()
        } catch (_: Exception) {}
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
