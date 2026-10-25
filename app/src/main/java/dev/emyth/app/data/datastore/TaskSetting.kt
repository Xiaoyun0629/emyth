/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.datastore

import kotlinx.serialization.Serializable

@Serializable
data class TaskSetting(
    // 任务内容
    val content: String = "",
    // 任务类型: text（文字提交）/ photo（拍照提交）
    val type: String = "text",
    // 截止时间戳（毫秒），0 表示无任务
    val deadlineMillis: Long = 0,
    // 惩罚描述（超时后 AI 执行的惩罚，由 AI 发布任务时自己填）
    val punishment: String = "",
    // 提交内容（文字提交的内容，或拍照提交的图片本地路径）
    val submission: String = "",
    // 提交时间戳
    val submittedAt: Long = 0,
    // 任务状态: none / pending(进行中) / submitted(待审核) / approved(已通过) / rejected(已拒绝)
    val status: String = "none",
    // 自定义 UI 颜色（背景 / 按钮 / 倒计时文字）
    val backgroundColor: Long? = null,
    val buttonColor: Long? = null,
    val countdownColor: Long? = null,
)
