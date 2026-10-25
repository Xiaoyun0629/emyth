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
import dev.emyth.app.data.datastore.LockChatSetting
import dev.emyth.app.data.datastore.SettingsStore
import org.koin.java.KoinJavaComponent.getKoin

fun createLockChatTool(): Tool = Tool(
    name = "lock_chat",
    description = "锁定当前对话，让用户在指定时长内无法发送消息（但仍可查看聊天记录），用于强制休息或睡觉。锁定期内输入框会被替换成倒计时提示。到点自动解锁。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putJsonObject("duration_minutes") {
                    put("type", "integer")
                    put("description", "锁定时长（分钟），必须是正整数。例如 60 表示锁 1 小时。")
                }
                putJsonObject("message") {
                    put("type", "string")
                    put("description", "锁定期间显示给用户的话。例如\"该睡觉啦，明早再来找我\"。可选。")
                }
            },
            required = listOf("duration_minutes")
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val duration = params["duration_minutes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val message = params["message"]?.jsonPrimitive?.contentOrNull ?: ""

        if (duration == null || duration <= 0) {
            return@Tool listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", false)
                    put("error", "duration_minutes 必须是正整数")
                }.toString()
            ))
        }

        try {
            val store = getKoin().get<SettingsStore>()
            val untilMillis = System.currentTimeMillis() + duration * 60_000
            store.update {
                it.copy(lockChatSetting = LockChatSetting(lockedUntilMillis = untilMillis, lockMessage = message))
            }
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("locked_until_millis", untilMillis)
                    put("duration_minutes", duration)
                    put("message", message)
                    put("result", "对话已锁定 $duration 分钟，到点自动解锁")
                }.toString()
            ))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", false)
                    put("error", e.message ?: "Unknown error")
                }.toString()
            ))
        }
    }
)


fun createUnlockChatTool(): Tool = Tool(
    name = "unlock_chat",
    description = "解除当前对话的锁定，让用户可以重新发送消息。当用户申请解除锁定、且你同意时调用。",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject { },
            required = emptyList()
        )
    },
    execute = {
        try {
            val store = getKoin().get<SettingsStore>()
            store.update {
                it.copy(lockChatSetting = LockChatSetting(lockedUntilMillis = 0, lockMessage = ""))
            }
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("result", "对话已解锁")
                }.toString()
            ))
        } catch (e: Exception) {
            listOf(UIMessagePart.Text(
                buildJsonObject {
                    put("success", false)
                    put("error", e.message ?: "Unknown error")
                }.toString()
            ))
        }
    }
)
