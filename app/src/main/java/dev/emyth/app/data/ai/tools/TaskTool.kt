/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import dev.emyth.app.data.datastore.SettingsStore
import dev.emyth.app.data.datastore.TaskSetting
import org.koin.java.KoinJavaComponent.getKoin
import android.content.Context

fun createTaskTool(context: Context): Tool = Tool(
    name = "task",
    description = "发布一个任务弹窗给用户。弹窗显示任务内容、类型和倒计时，超时会按你填写的惩罚处理。用户需要提交（文字或拍照）给你审核，审核通过才算完成。用于监督吃饭、喝水、睡觉、写作业、吃药等。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "任务内容，例如\"现在去喝水\"\"今晚12点前睡觉\"")
                }
                putJsonObject("type") {
                    put("type", "string")
                    put("description", "任务类型：text（文字提交）或 photo（拍照提交）。默认 text。")
                }
                putJsonObject("deadline_minutes") {
                    put("type", "integer")
                    put("description", "截止时间（分钟），正整数。例如 30 表示 30 分钟内完成。")
                }
                putJsonObject("punishment") {
                    put("type", "string")
                    put("description", "超时后的惩罚，由你自己定，例如\"锁抖音30分钟\"\"切回Emyth\"。可选。")
                }
            },
            required = listOf("content", "deadline_minutes")
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val content = params["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val type = params["type"]?.jsonPrimitive?.contentOrNull ?: "text"
        val deadlineMinutes = params["deadline_minutes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val punishment = params["punishment"]?.jsonPrimitive?.contentOrNull ?: ""

        if (content.isBlank()) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", "content 不能为空") }.toString()
            ))
        }
        if (deadlineMinutes == null || deadlineMinutes <= 0) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", "deadline_minutes 必须是正整数") }.toString()
            ))
        }

        try {
            val store = getKoin().get<SettingsStore>()
            val deadline = System.currentTimeMillis() + deadlineMinutes * 60_000
            val resolvedType = if (type == "photo") "photo" else "text"
            store.update {
                it.copy(taskSetting = TaskSetting(
                    content = content,
                    type = resolvedType,
                    deadlineMillis = deadline,
                    punishment = punishment,
                    status = "pending",
                ))
            }
            try {
                dev.emyth.app.data.service.TaskOverlayService.show(context)
            } catch (e: Exception) {
                android.util.Log.w("TaskTool", "启动任务弹窗失败", e)
            }
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("deadline_millis", deadline)
                    put("deadline_minutes", deadlineMinutes)
                    put("type", resolvedType)
                    put("punishment", punishment)
                    put("result", "任务已发布：$content，截止 $deadlineMinutes 分钟后，超时惩罚：${punishment.ifBlank { "无" }}")
                }.toString()
            ))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", e.message ?: "Unknown error") }.toString()
            ))
        }
    }
)

fun createTaskStatusTool(): Tool = Tool(
    name = "task_status",
    description = "查看当前任务弹窗的状态：进行中/待审核/已完成，以及任务内容、类型、截止时间、提交内容等。用于判断用户是否提交、是否超时。",
    parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
    execute = { _ ->
        try {
            val store = getKoin().get<SettingsStore>()
            val task = store.settingsFlow.value.taskSetting
            val now = System.currentTimeMillis()
            val expired = task.status == "pending" && task.deadlineMillis > 0 && now > task.deadlineMillis
            val textPart = UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("status", task.status)
                    put("content", task.content)
                    put("type", task.type)
                    put("deadline_millis", task.deadlineMillis)
                    put("punishment", task.punishment)
                    put("submission", task.submission)
                    put("submitted_at", task.submittedAt)
                    put("expired", expired)
                    put("remaining_minutes", if (task.deadlineMillis > 0) ((task.deadlineMillis - now) / 60_000).coerceAtLeast(0) else 0)
                }.toString()
            )
            if (task.submission.startsWith("file://")) {
                listOf(textPart, UIMessagePart.Image(url = task.submission))
            } else {
                listOf(textPart)
            }
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", e.message ?: "Unknown error") }.toString()
            ))
        }
    }
)

fun createTaskReviewTool(): Tool = Tool(
    name = "task_review",
    description = "审核用户提交的任务。approved=true 表示通过（任务完成，弹窗解除）；false 表示拒绝（可附 feedback 说明原因，用户需重新提交）。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("approved") {
                    put("type", "boolean")
                    put("description", "是否通过审核")
                }
                putJsonObject("feedback") {
                    put("type", "string")
                    put("description", "拒绝时给用户的说明，可选")
                }
            },
            required = listOf("approved")
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val approved = params["approved"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
        val feedback = params["feedback"]?.jsonPrimitive?.contentOrNull ?: ""

        if (approved == null) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", "approved 必须是布尔值") }.toString()
            ))
        }

        try {
            val store = getKoin().get<SettingsStore>()
            val task = store.settingsFlow.value.taskSetting
            if (task.status != "submitted") {
                return@Tool listOf(UIMessagePart.Text(
                    buildJsonObject { put("success", false); put("error", "当前没有待审核的提交（status=${task.status}）") }.toString()
                ))
            }
            val newStatus = if (approved) "approved" else "rejected"
            store.update {
                it.copy(taskSetting = task.copy(status = newStatus))
            }
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("approved", approved)
                    put("status", newStatus)
                    put("feedback", feedback)
                    put("result", if (approved) "任务已通过，弹窗解除" else "任务被拒绝：${feedback.ifBlank { "未说明原因" }}")
                }.toString()
            ))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject { put("success", false); put("error", e.message ?: "Unknown error") }.toString()
            ))
        }
    }
)
