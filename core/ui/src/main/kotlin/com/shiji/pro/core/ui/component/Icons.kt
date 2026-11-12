package com.shiji.pro.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * P15 自绘图标集：延续工程「图标全部自绘、零版权素材」约定（README §7 / ICON_DESIGN §5）。
 * 不引入 `material-icons-*` 制品（版本目录未缓存，离线构建不可解析）。
 *
 * 统一 24×24 逻辑网格、2dp 等效线宽、圆头描边，随 `LocalContentColor` 自动适配主题与选中态。
 * 本组件本身不声明语义文本：由外层 IconButton / Tab 的 contentDescription 提供，避免屏幕阅读器重复朗读。
 */
enum class ShiJiIconKind {
    REFRESH, COMPARE, LOGS, SETTINGS, BACK, CLOSE,
    EMPTY, ERROR, WARNING, SUCCESS, INFO, PERMISSION,
    CELL, WIFI, LOCATION, DEVICE, DRIVE, SPEED,
}

@Composable
fun ShiJiIcon(
    kind: ShiJiIconKind,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f
        if (s <= 0f) return@Canvas
        when (kind) {
            ShiJiIconKind.REFRESH -> {
                arc(tint, 3f, 3f, 18f, -60f, 250f, s)
                line(tint, 19f, 2f, 19f, 8f, s)
                line(tint, 19f, 2f, 13f, 2f, s)
            }
            ShiJiIconKind.COMPARE -> {
                line(tint, 2f, 20f, 22f, 20f, s)
                bar(tint, 4f, 12f, 4f, 8f, s)
                bar(tint, 10f, 7f, 4f, 13f, s)
                bar(tint, 16f, 14f, 4f, 6f, s)
            }
            ShiJiIconKind.LOGS -> {
                line(tint, 9f, 6f, 21f, 6f, s)
                line(tint, 9f, 12f, 21f, 12f, s)
                line(tint, 9f, 18f, 21f, 18f, s)
                dot(tint, 4f, 6f, 1.2f, s)
                dot(tint, 4f, 12f, 1.2f, s)
                dot(tint, 4f, 18f, 1.2f, s)
            }
            ShiJiIconKind.SETTINGS -> {
                ring(tint, 12f, 12f, 3.2f, s)
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val c = cos(a).toFloat()
                    val n = sin(a).toFloat()
                    line(tint, 12f + 5.5f * c, 12f + 5.5f * n, 12f + 9f * c, 12f + 9f * n, s)
                }
            }
            ShiJiIconKind.BACK -> {
                line(tint, 20f, 12f, 4f, 12f, s)
                line(tint, 10f, 6f, 4f, 12f, s)
                line(tint, 10f, 18f, 4f, 12f, s)
            }
            ShiJiIconKind.CLOSE -> {
                line(tint, 6f, 6f, 18f, 18f, s)
                line(tint, 18f, 6f, 6f, 18f, s)
            }
            ShiJiIconKind.EMPTY -> {
                roundRect(tint, 3f, 7f, 18f, 13f, s)
                line(tint, 7f, 7f, 10f, 4f, s)
                line(tint, 17f, 7f, 14f, 4f, s)
                line(tint, 8f, 14f, 16f, 14f, s)
            }
            ShiJiIconKind.ERROR -> {
                ring(tint, 12f, 12f, 9f, s)
                line(tint, 12f, 7f, 12f, 13.5f, s)
                dot(tint, 12f, 17f, 1.2f, s)
            }
            ShiJiIconKind.WARNING -> {
                line(tint, 12f, 3f, 22f, 20f, s)
                line(tint, 22f, 20f, 2f, 20f, s)
                line(tint, 2f, 20f, 12f, 3f, s)
                line(tint, 12f, 9f, 12f, 15f, s)
                dot(tint, 12f, 18f, 1.1f, s)
            }
            ShiJiIconKind.SUCCESS -> {
                ring(tint, 12f, 12f, 9f, s)
                line(tint, 7.5f, 12f, 11f, 15.5f, s)
                line(tint, 11f, 15.5f, 16.5f, 9f, s)
            }
            ShiJiIconKind.INFO -> {
                ring(tint, 12f, 12f, 9f, s)
                line(tint, 12f, 11f, 12f, 17f, s)
                dot(tint, 12f, 7.5f, 1.2f, s)
            }
            ShiJiIconKind.PERMISSION -> {
                roundRect(tint, 5f, 11f, 14f, 10f, s)
                arc(tint, 8f, 4f, 8f, 180f, 180f, s)
                dot(tint, 12f, 16f, 1.2f, s)
            }
            ShiJiIconKind.CELL -> {
                line(tint, 2f, 20f, 22f, 20f, s)
                line(tint, 12f, 4f, 5f, 20f, s)
                line(tint, 12f, 4f, 19f, 20f, s)
                line(tint, 7.5f, 14f, 16.5f, 14f, s)
                dot(tint, 12f, 3f, 1.4f, s)
            }
            ShiJiIconKind.WIFI -> {
                arc(tint, 2f, 2f, 20f, 225f, 90f, s)
                arc(tint, 6f, 6f, 12f, 225f, 90f, s)
                dot(tint, 12f, 19f, 1.6f, s)
            }
            ShiJiIconKind.LOCATION -> {
                ring(tint, 12f, 9f, 4.5f, s)
                line(tint, 8.8f, 12.5f, 12f, 21f, s)
                line(tint, 15.2f, 12.5f, 12f, 21f, s)
            }
            ShiJiIconKind.DEVICE -> {
                roundRect(tint, 6f, 2.5f, 12f, 19f, s)
                line(tint, 9f, 5.5f, 15f, 5.5f, s)
                dot(tint, 12f, 18.5f, 1f, s)
            }
            ShiJiIconKind.DRIVE -> {
                roundRect(tint, 2.5f, 4f, 19f, 16f, s)
                line(tint, 9f, 4f, 9f, 20f, s)
                line(tint, 15.5f, 4f, 15.5f, 20f, s)
                line(tint, 2.5f, 12f, 21.5f, 12f, s)
                dot(tint, 15.5f, 8f, 1.5f, s)
            }
            ShiJiIconKind.SPEED -> {
                arc(tint, 3f, 3f, 18f, 180f, 180f, s)
                line(tint, 12f, 17f, 17f, 9f, s)
                dot(tint, 12f, 17f, 1.4f, s)
            }
        }
    }
}

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, s: Float) {
    drawLine(
        color = color,
        start = Offset(x1 * s, y1 * s),
        end = Offset(x2 * s, y2 * s),
        strokeWidth = 2f * s,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.bar(color: Color, x: Float, y: Float, w: Float, h: Float, s: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * s, y * s),
        size = Size(w * s, h * s),
        cornerRadius = CornerRadius(1f * s, 1f * s),
    )
}

private fun DrawScope.ring(color: Color, cx: Float, cy: Float, r: Float, s: Float) {
    drawCircle(color, radius = r * s, center = Offset(cx * s, cy * s), style = Stroke(2f * s))
}

private fun DrawScope.dot(color: Color, cx: Float, cy: Float, r: Float, s: Float) {
    drawCircle(color, radius = r * s, center = Offset(cx * s, cy * s))
}

private fun DrawScope.arc(
    color: Color, x: Float, y: Float, d: Float,
    startAngle: Float, sweepAngle: Float, s: Float,
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(x * s, y * s),
        size = Size(d * s, d * s),
        style = Stroke(2f * s),
    )
}

private fun DrawScope.roundRect(color: Color, x: Float, y: Float, w: Float, h: Float, s: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * s, y * s),
        size = Size(w * s, h * s),
        cornerRadius = CornerRadius(2.5f * s, 2.5f * s),
        style = Stroke(2f * s),
    )
}
