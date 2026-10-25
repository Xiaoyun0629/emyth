/*
 * Emyth
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package dev.emyth.app.ui.components.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Emyth - 自定义"思考中"图标（月牙，呼应 Emyth 星空主题）
 */
public val MoonIcon: ImageVector
    get() {
        if (_moonIcon != null) return _moonIcon!!
        _moonIcon = ImageVector.Builder(
            name = "Moon",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF282828)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 0.0f,
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(21f, 12.79f)
                arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
                arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
                close()
            }
        }
        return _moonIcon!!
    }

private var _moonIcon: ImageVector? = null
