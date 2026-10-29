package dev.emyth.app.data.service

import android.content.Context
import android.content.Intent
import dev.emyth.app.RouteActivity

/**
 * 吃醋拉回的「强制拉回」状态管理。
 * 开启后，无障碍服务监听到用户切到别的 App，会自动把 Emyth 拽回前台，
 * 直到调用 disable 解除。
 */
object PullBackManager {
    private const val PREFS = "pull_back_prefs"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** 把 Emyth 主界面拽回前台 */
    fun pullBack(context: Context) {
        runCatching {
            val intent = Intent(context, RouteActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
        }
    }
}
