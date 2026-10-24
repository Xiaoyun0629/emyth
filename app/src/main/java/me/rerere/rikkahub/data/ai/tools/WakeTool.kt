/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.ScheduledWake
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.service.ProactiveMessageService
import me.rerere.rikkahub.data.service.ProactiveMessageReceiver
import org.koin.java.KoinJavaComponent.getKoin

private const val EXTRA_SCHEDULED_WAKE_NOTE = "scheduled_wake_note"

fun createScheduleWakeTool(context: Context): Tool = Tool(
    name = "schedule_wake",
    description = "设置一个定时自主唤醒。到点后系统会唤醒你（注入唤醒备注），你可以根据备注决定主动给用户发消息、调用工具，或不打扰（no-op）。用于提醒起床、吃饭、吃药、写作业等。trigger_at 是毫秒时间戳。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("trigger_at") {
                    put("type", "integer")
                    put("description", "唤醒时间（毫秒时间戳），必须是未来时间。可先用 get_time_info 拿当前时间戳再换算。")
                }
                putJsonObject("note") {
                    put("type", "string")
                    put("description", "唤醒备注，告诉你这次为什么醒来，例如\"该提醒她睡觉了\"\"早上叫她起床\"。可选。")
                }
            },
            required = listOf("trigger_at")
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val triggerAt = params["trigger_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val note = params["note"]?.jsonPrimitive?.contentOrNull ?: ""

        if (triggerAt == null || triggerAt <= System.currentTimeMillis()) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", "trigger_at 必须是未来的毫秒时间戳") }.toString()
            ))
        }

        try {
            val store = getKoin().get<SettingsStore>()
            val id = System.currentTimeMillis().toString()
            val wake = ScheduledWake(id = id, triggerAtMillis = triggerAt, note = note)
            store.update { s ->
                s.copy(wakeSetting = s.wakeSetting.copy(scheduledWakes = s.wakeSetting.scheduledWakes + wake))
            }

            // 设置 AlarmManager
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ProactiveMessageReceiver::class.java).apply {
                    action = ProactiveMessageService.ACTION_PROACTIVE_MESSAGE
                    putExtra(EXTRA_SCHEDULED_WAKE_NOTE, note)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, id.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                }
            } catch (e: Exception) {
                android.util.Log.w("WakeTool", "AlarmManager 设置失败", e)
            }

            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("wake_id", id)
                    put("trigger_at", triggerAt)
                    put("note", note)
                    put("result", "已设置唤醒：${note.ifBlank { "无备注" }}，到点系统会唤醒你")
                }.toString()
            ))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", e.message ?: "Unknown error") }.toString()
            ))
        }
    }
)

fun createListWakesTool(context: Context): Tool = Tool(
    name = "list_wakes",
    description = "列出所有已设置的自主唤醒计划，包括触发时间和备注。",
    parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
    execute = { _ ->
        try {
            val store = getKoin().get<SettingsStore>()
            val wakes = store.settingsFlow.value.wakeSetting.scheduledWakes
            val now = System.currentTimeMillis()
            val arr = buildJsonArray {
                wakes.forEach { w ->
                    add(buildJsonObject {
                        put("id", w.id)
                        put("trigger_at", w.triggerAtMillis)
                        put("note", w.note)
                        put("pending", w.triggerAtMillis > now)
                    })
                }
            }
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("count", wakes.size)
                    put("wakes", arr)
                }.toString()
            ))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", e.message ?: "Unknown error") }.toString()
            ))
        }
    }
)

fun createCancelWakeTool(context: Context): Tool = Tool(
    name = "cancel_wake",
    description = "取消一个已设置的自主唤醒计划。按 id 或 note 匹配。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("id") {
                    put("type", "string")
                    put("description", "要取消的唤醒 id（来自 list_wakes）")
                }
                putJsonObject("note") {
                    put("type", "string")
                    put("description", "按备注取消（模糊匹配，取消所有 note 包含此文本的唤醒）")
                }
            }
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val id = params["id"]?.jsonPrimitive?.contentOrNull
        val note = params["note"]?.jsonPrimitive?.contentOrNull

        try {
            val store = getKoin().get<SettingsStore>()
            val wakes = store.settingsFlow.value.wakeSetting.scheduledWakes
            val remaining = if (id != null) {
                wakes.filterNot { it.id == id }
            } else if (note != null) {
                wakes.filterNot { it.note.contains(note) }
            } else {
                wakes
            }
            val cancelled = wakes.size - remaining.size
            store.update { s -> s.copy(wakeSetting = s.wakeSetting.copy(scheduledWakes = remaining)) }
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("cancelled", cancelled)
                    put("remaining", remaining.size)
                }.toString()
            ))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", e.message ?: "Unknown error") }.toString()
            ))
        }
    }
)
