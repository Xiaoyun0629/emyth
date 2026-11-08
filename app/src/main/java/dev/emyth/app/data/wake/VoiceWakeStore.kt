/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.wake

import android.content.Context

/**
 * 语音唤醒的持久化存储：开关、唤醒词模板路径、回应音频列表。
 * 独立于 SettingsStore，用 SharedPreferences 轻量存。
 */
object VoiceWakeStore {

    private const val PREFS = "voice_wake_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TEMPLATE = "template_path"
    private const val KEY_RESPONSES = "response_paths"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getTemplatePath(ctx: Context): String = prefs(ctx).getString(KEY_TEMPLATE, "") ?: ""

    fun setTemplatePath(ctx: Context, path: String) {
        prefs(ctx).edit().putString(KEY_TEMPLATE, path).apply()
    }

    fun getResponsePaths(ctx: Context): List<String> =
        prefs(ctx).getString(KEY_RESPONSES, "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()

    fun addResponsePath(ctx: Context, path: String) {
        val list = getResponsePaths(ctx).toMutableList()
        if (!list.contains(path)) list.add(path)
        prefs(ctx).edit().putString(KEY_RESPONSES, list.joinToString("|")).apply()
    }

    fun removeResponsePath(ctx: Context, path: String) {
        val list = getResponsePaths(ctx).filter { it != path }
        prefs(ctx).edit().putString(KEY_RESPONSES, list.joinToString("|")).apply()
    }

    fun clearResponses(ctx: Context) {
        prefs(ctx).edit().remove(KEY_RESPONSES).apply()
    }

    fun hasTemplate(ctx: Context): Boolean = getTemplatePath(ctx).isNotBlank()
}
