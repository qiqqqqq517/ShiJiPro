package com.shiji.pro.features.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shiji.pro.core.common.IdwGrid
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.ui.component.ChartFrame
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner
import com.shiji.pro.core.ui.component.StatusTone
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiPalette

/**
 * 覆盖测试（路测）页（P14 + P15 恒定骨架）：契约卡与栅格卡永远存在。
 * 无记录点时栅格区保持固定高度占位，不换成高度不同的空态卡片。
 */
@Composable
internal fun DriveTestTab(state: AppState, modifier: Modifier) {
    val points = state.driveTestPoints.filter { it.rsrp.value != null }
    ScrollColumn(modifier) {
        PageStatusCard(
            label = "覆盖测试（路测）",
            cap = null,
            hasSnapshot = state.driveTestPoints.isNotEmpty(),
            note = "记录点 ${state.driveTestPoints.size} 个，有效 RSRP 点 ${points.size} 个",
        )

        SectionCard(
            title = "覆盖测试（路测）",
            subtitle = "数据契约 / CSV 列 / 室内三模式",
            icon = ShiJiIconKind.DRIVE,
        ) {
            Text("记录点: ${state.driveTestPoints.size}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "室外：GNSS 轨迹 + 逐点信号值；室内：手动打点 / 预设轨迹 / 传感器自动",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "CSV 列: 时间/经纬度/卡槽/RSRP/RSRQ/SINR/频段/EARFCN",
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(Dimen.Space2))
            StatusBanner("当前为数据契约与展示骨架；打点/导出由设置页触发（P10 接入）")
        }

        SectionCard(
            title = "覆盖栅格（IDW 插值，估算）",
            subtitle = "20 × 20 反距离加权热力图",
            icon = ShiJiIconKind.LOCATION,
        ) {
            val samples = points.map {
                IdwGrid.Sample(it.latitude, it.longitude, it.rsrp.value!!.toDouble())
            }
            val cols = 20
            val rows = 20
            val cells = if (samples.isEmpty()) emptyList() else IdwGrid.interpolate(samples, cols, rows)
            val bounds = if (samples.isEmpty()) null else IdwGrid.bounds(samples)
            ChartFrame(
                description = "覆盖栅格热力图",
                hasData = samples.isNotEmpty(),
                emptyMessage = "暂无有效记录点：需要至少 1 个含 RSRP 实测值的路测点，栅格区域保持占位",
            ) {
                CoverageGridCanvas(cells = cells, cols = cols, rows = rows, pointCount = points.size)
            }
            Text(
                "点数 ${points.size} · 栅格 ${cols}×${rows} · " +
                    (bounds?.let {
                        "范围 纬度 ${"%.4f".format(it.latMin)}~${"%.4f".format(it.latMax)} / " +
                            "经度 ${"%.4f".format(it.lonMin)}~${"%.4f".format(it.lonMax)}"
                    } ?: "范围 N/A（无有效记录点）"),
                style = MaterialTheme.typography.labelSmall,
            )
            StatusBanner(
                "口径：反距离加权（IDW，p=2）纯 Kotlin 实现；结果为估算值，" +
                    "点少/分布不均时不可靠，仅供参考。",
                tone = StatusTone.WARNING,
            )
        }
    }
}

@Composable
private fun CoverageGridCanvas(
    cells: List<IdwGrid.Cell>,
    cols: Int,
    rows: Int,
    pointCount: Int,
) {
    val weak = ShiJiPalette.SignalBad
    val strong = ShiJiPalette.SignalExcellent
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimen.ChartHeight)
            .semantics { contentDescription = "覆盖栅格热力图，基于 $pointCount 个记录点插值，结果为估算值" },
    ) {
        val cw = size.width / cols
        val ch = size.height / rows
        cells.forEach { c ->
            val t = ((c.value + 140.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
            // 红（差）→ 绿（好）：两端色值取自全局色板，语义与信号评级一致
            val color = Color(
                red = weak.red + (strong.red - weak.red) * t,
                green = weak.green + (strong.green - weak.green) * t,
                blue = weak.blue + (strong.blue - weak.blue) * t,
                alpha = 0.85f,
            )
            drawRect(
                color = color,
                topLeft = Offset((c.u * size.width).toFloat(), (c.v * size.height).toFloat()),
                size = Size(cw + 1f, ch + 1f),
            )
        }
    }
}
