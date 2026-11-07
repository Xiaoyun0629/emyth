/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 设备事件的本地存储（SharedPreferences + JSON），替代不稳定的 Supabase 云端。
 * 只保留最近 MAX_EVENTS 条，超出自动裁剪最旧的，避免无限增长占内存。
 * 字段与 Supabase 的 device_data 表保持一致（timestamp / device_event / foreground_app），
 * 方便 get_device_events 等读取方无缝切换。
 */
object DeviceEventLocalStore {
    private const val PREFS_NAME = "device_event_local_store"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 200

    /** 记录一条设备事件（追加到本地，超出上限自动裁剪最旧的） */
    @Synchronized
    fun record(context: Context, eventType: String, appName: String = "") {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val timestamp = sdf.format(java.util.Date())
            val arr = JSONArray(prefs.getString(KEY_EVENTS, "[]") ?: "[]")
            val obj = JSONObject().apply {
                put("timestamp", timestamp)
                put("device_event", eventType)
                put("foreground_app", appName)
            }
            arr.put(obj)
            while (arr.length() > MAX_EVENTS) {
                arr.remove(0)
            }
            prefs.edit().putString(KEY_EVENTS, arr.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("DeviceEventLocalStore", "record failed", e)
        }
    }

    /** 查询最近 count 条（最新在前），返回与 Supabase REST 一致的 JSON 数组字符串 */
    @Synchronized
    fun queryJson(context: Context, count: Int): String {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString(KEY_EVENTS, "[]") ?: "[]")
            val result = JSONArray()
            val n = count.coerceIn(1, 50)
            for (i in arr.length() - 1 downTo maxOf(0, arr.length() - n)) {
                result.put(arr.getJSONObject(i))
            }
            result.toString()
        } catch (e: Exception) {
            "[]"
        }
    }
}
