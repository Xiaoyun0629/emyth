/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable

@Serializable
data class VoiceCallSetting(
    // 通话背景色（null = 用默认暖色底）
    val backgroundColor: Long? = null,
    // 通话背景图（本地/远程 URL，空 = 无背景图）
    val backgroundImage: String = "",
)
