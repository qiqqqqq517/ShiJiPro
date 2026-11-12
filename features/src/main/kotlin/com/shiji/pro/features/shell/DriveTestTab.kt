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
import com.shiji.pro.core.common.IdwGrid
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.theme.Dimen

/**
 * 覆盖测试（路测）页：记录点 / 室内三模式 / CSV 列 / KPI / **覆盖栅格（IDW）**。
 *
 * P14（研究报告 §11-B7）：不用 RenderScript（Android 12 起废弃），用纯 Kotlin IDW
 * 把记录点插值成热力栅格 —— 结果全部是**估算值**，点少时明确提示不可靠，不画伪造数据。
 */
@Composable
internal fun DriveTestTab(state: AppState, modifier: Modifier) {
    ScrollColumn(modifier) {
        SectionCard("覆盖测试（路测）") {
            Text("记录点: ${state.driveTestPoints.size}", style = MaterialTheme.typography.bodyMedium)
            Text("室外：GNSS 轨迹 + 逐点信号值；室内：手动打点 / 预设轨迹 / 传感器自动",
                style = MaterialTheme.typography.bodySmall)
            Text("CSV 列: 时间/经纬度/卡槽/RSRP/RSRQ/SINR/频段/EARFCN",
                style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(Dimen.Space2))
            Text("当前为数据契约与展示骨架；打点/导出由设置页触发（P10 接入）",
                style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Dimen.Space3))
        CoverageGridCard(state)
    }
}

/**
 * 覆盖栅格：把 recordPoints 的 RSRP 用 IDW 插值成 20×20 热力图。
 *
 * 三态：只有 `rsrp` 有实测值的点参与插值；不足 1 个点 → 不画图（显示原因）。
 */
@Composable
private fun CoverageGridCard(state: AppState) {
    SectionCard("覆盖栅格（IDW 插值，估算）") {
        val points = state.driveTestPoints.filter { it.rsrp.value != null }
        if (points.isEmpty()) {
            Text(
                "暂无有效记录点（需要至少 1 个含 RSRP 实测值的路测点）。" +
                    "打点功能接入后会在此显示插值热力图。",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        val samples = points.map {
            IdwGrid.Sample(it.latitude, it.longitude, it.rsrp.value!!.toDouble())
        }
        val cols = 20
        val rows = 20
        val cells = IdwGrid.interpolate(samples, cols, rows)
        val bounds = IdwGrid.bounds(samples)
        Canvas(modifier = Modifier.fillMaxWidth().height(Dimen.ChartHeight)) {
            val cw = size.width / cols
            val ch = size.height / rows
            cells.forEach { c ->
                val t = ((c.value + 140.0) / 100.0).coerceIn(0.0, 1.0)
                // 红（差）→ 绿（好）：RSRP 越高越绿；色相近似，够表达强弱即可
                val color = Color(red = (1.0 - t).toFloat(), green = t.toFloat(), blue = 0.25f, alpha = 0.85f)
                drawRect(
                    color = color,
                    topLeft = Offset((c.u * size.width).toFloat(), (c.v * size.height).toFloat()),
                    size = Size(cw + 1f, ch + 1f),
                )
            }
        }
        Text(
            "点数 ${points.size} · 栅格 ${cols}×${rows} · " +
                "范围 纬度 ${"%.4f".format(bounds!!.latMin)}~${"%.4f".format(bounds.latMax)} / " +
                "经度 ${"%.4f".format(bounds.lonMin)}~${"%.4f".format(bounds.lonMax)}",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "口径：反距离加权（IDW，p=2）纯 Kotlin 实现（RenderScript 自 Android 12 已废弃）；" +
                "结果为估算值，点少/分布不均时不可靠，仅供参考。",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
