package com.shiji.pro.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.shiji.pro.core.model.GnssConstellation
import com.shiji.pro.core.model.Satellite
import com.shiji.pro.core.model.WifiAccessPoint
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiPalette

/**
 * 三种自绘 Compose 图表（设计稿 §2.2 / §3.3 / §4.6-2）。
 * 纯 Canvas 实现，不引入 MPAndroidChart 等 View 库；采样降频由调用方用 Sampling.downsample 完成。
 */

/** 信号/速率趋势折线。points 为 (x 归一化 0..1, y 归一化 0..1)。 */
@Composable
fun TrendChart(
    points: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier,
    lineColor: Color = ShiJiPalette.Primary,
) {
    val axis = ShiJiPalette.OutlineLight
    Canvas(modifier = modifier.fillMaxWidth().height(Dimen.ChartHeight)) {
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        drawLine(axis, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
        if (points.size < 2) return@Canvas
        var prev: Offset? = null
        points.forEach { (x, y) ->
            val cur = Offset(x * size.width, (1f - y) * size.height)
            prev?.let { drawLine(lineColor, it, cur, strokeWidth = 3f) }
            prev = cur
        }
    }
}

/** GNSS 卫星天空图（极坐标天顶图）：中心=天顶 90°，边缘=地平 0°；方位角 0°在上、顺时针。 */
@Composable
fun SkyPlot(
    satellites: List<Satellite>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(Dimen.ChartHeight)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 8f
        // 30° / 60° 高度圈 + 方位十字
        listOf(0.33f, 0.66f, 1f).forEach { r ->
            drawCircle(ShiJiPalette.OutlineLight, radius = radius * r, center = c, style = Stroke(1f))
        }
        drawLine(ShiJiPalette.OutlineLight, Offset(c.x, c.y - radius), Offset(c.x, c.y + radius), 1f)
        drawLine(ShiJiPalette.OutlineLight, Offset(c.x - radius, c.y), Offset(c.x + radius, c.y), 1f)
        satellites.forEach { s ->
            val elev = s.elevationDegrees.value ?: return@forEach
            val azim = s.azimuthDegrees.value ?: return@forEach
            val r = (90f - elev.coerceIn(0f, 90f)) / 90f * radius
            val rad = Math.toRadians(azim.toDouble() - 90.0)
            val px = c.x + (r * kotlin.math.cos(rad)).toFloat()
            val py = c.y + (r * kotlin.math.sin(rad)).toFloat()
            drawCircle(constellationColor(s.constellation), radius = 6f, center = Offset(px, py))
            if (s.usedInFix) {
                drawCircle(ShiJiPalette.OnPrimary, radius = 6f, center = Offset(px, py),
                    style = Stroke(1.5f))
            }
        }
    }
}

fun constellationColor(c: GnssConstellation): Color = when (c) {
    GnssConstellation.GPS -> ShiJiPalette.ConstellationGps
    GnssConstellation.BEIDOU -> ShiJiPalette.ConstellationBeidou
    GnssConstellation.GLONASS -> ShiJiPalette.ConstellationGlonass
    GnssConstellation.QZSS -> ShiJiPalette.ConstellationQzss
    GnssConstellation.GALILEO -> ShiJiPalette.ConstellationGalileo
    GnssConstellation.SBAS -> ShiJiPalette.ConstellationSbas
    else -> ShiJiPalette.ConstellationUnknown
}

/** WiFi 信道占用图：按信道分组，每个热点一根柱，颜色按序号区分。 */
@Composable
fun ChannelChart(
    accessPoints: List<WifiAccessPoint>,
    modifier: Modifier = Modifier,
) {
    val palette = listOf(
        ShiJiPalette.Primary, ShiJiPalette.Net4G, ShiJiPalette.Net3G,
        ShiJiPalette.Net5G, ShiJiPalette.SignalGood, ShiJiPalette.Net2G,
    )
    Canvas(modifier = modifier.fillMaxWidth().height(Dimen.ChartHeight)) {
        val baseline = size.height - 4f
        drawLine(ShiJiPalette.OutlineLight, Offset(0f, baseline), Offset(size.width, baseline), 1f)
        val byChannel = accessPoints.groupBy { it.channel.value ?: -1 }
            .filterKeys { it > 0 }
        if (byChannel.isEmpty()) return@Canvas
        val maxCh = byChannel.keys.max()
        val slotW = size.width / (maxCh + 2f)
        byChannel.entries.sortedBy { it.key }.forEachIndexed { idx, (ch, aps) ->
            aps.sortedByDescending { it.rssiDbm }.forEachIndexed { i, ap ->
                val heightRatio = ((ap.rssiDbm + 100).coerceIn(0, 70)) / 70f
                val barH = heightRatio * (baseline - 10f)
                val x = (ch + 1) * slotW + i * 3f
                drawBar(
                    color = palette[idx % palette.size].copy(alpha = 0.75f),
                    topLeft = Offset(x, baseline - barH),
                    size = Size(4f, barH),
                )
            }
        }
    }
}

private fun DrawScope.drawBar(color: Color, topLeft: Offset, size: Size) {
    drawRect(color = color, topLeft = topLeft, size = size)
}
