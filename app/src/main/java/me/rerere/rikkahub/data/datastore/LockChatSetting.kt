/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable

@Serializable
data class LockChatSetting(
    // 锁定结束的时间戳（毫秒），0 表示未锁定
    val lockedUntilMillis: Long = 0,
    // 锁定提示语（输入框上方显示的话）
    val lockMessage: String = "",
)
