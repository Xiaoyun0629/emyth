/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.data.datastore

import kotlinx.serialization.Serializable

/**
 * 收藏的一条语音（存文本，回听时重新 TTS 朗读）
 */
@Serializable
data class VoiceFavoriteItem(
    val text: String = "",
    val timestamp: Long = 0,
)

/**
 * 语音收藏夹设置
 */
@Serializable
data class VoiceFavoriteSetting(
    val favorites: List<VoiceFavoriteItem> = emptyList(),
)
