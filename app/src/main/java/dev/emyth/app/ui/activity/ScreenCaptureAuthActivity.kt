/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.activity

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.emyth.app.data.service.ScreenCaptureManager

/**
 * 屏幕捕获授权页：弹出系统录屏授权框，用户同意后把 resultCode + data 交给 ScreenCaptureManager。
 * 关键：MediaProjection 必须由本 Activity（不是 applicationContext）创建，token 才有效。
 */
class ScreenCaptureAuthActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_CODE = 10086

        /** 启动授权页，并标记「正在等待授权」，避免 read_screen 反复弹框。 */
        fun start(context: Context) {
            ScreenCaptureManager.markAwaitingAuth()
            val intent = Intent(context, ScreenCaptureAuthActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mpm.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // 关键修复：用本 Activity（this）创建 MediaProjection，token 绑定关系才正确
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mp = mpm.getMediaProjection(resultCode, data)
                if (mp != null) {
                    ScreenCaptureManager.start(this, mp)
                }
            } else {
                // 用户拒绝授权：清除等待标记，避免后续 read_screen 一直以为「在等授权」
                ScreenCaptureManager.stop()
            }
        }
        finish()
    }
}
