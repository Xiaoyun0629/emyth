/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable

@Serializable
data class ScheduledWake(
    val id: String = "",
    val triggerAtMillis: Long = 0,
    val note: String = "",
)

@Serializable
data class WakeSetting(
    val scheduledWakes: List<ScheduledWake> = emptyList(),
)
