package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CellData
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.RadioTech
import com.shiji.pro.core.model.SimInfo
import com.shiji.pro.core.ui.chart.TrendChart
import com.shiji.pro.core.ui.component.EstimateDisclaimer
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.theme.Dimen

/**
 * 卡槽 1/2 蜂窝页（P7）。
 *
 * 2026-11-12 结构拆分：从 FeatureScreens.kt 独立为单标签文件，便于「按制式分组」（研究报告 §11-A2）
 * 与「运营商归属」（§11-A1）在同一文件内演进，不再与其它标签共编。
 */
@Composable
internal fun CellularTab(state: AppState, slot: Int, modifier: Modifier) {
    val cap = state.capabilityOf(CollectorKey.CELLULAR)
    ScrollColumn(modifier) {
        if (cap?.available == false) {
            PermissionGate(cap)
            return@ScrollColumn
        }
        val snap = state.cellularSlots[slot]
        if (snap == null) {
            SectionCard("卡槽 ${slot + 1}") {
                Text("N/A · 无此卡槽数据（未插卡 / 机型不提供 / 尚未刷新）",
                    style = MaterialTheme.typography.bodyMedium)
            }
            return@ScrollColumn
        }
        snap.sim?.let { sim ->
            SectionCard("SIM / 运营商（Slot $slot）") { renderSim(sim) }
            Spacer(Modifier.height(Dimen.Space3))
        }
        SectionCard("服务小区") {
            val cell = snap.servingCell
            if (cell == null) Text("N/A · 服务小区取不到", style = MaterialTheme.typography.bodyMedium)
            else renderCell(cell)
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("邻区（${snap.neighbors.size}）") {
            if (snap.neighbors.isEmpty()) {
                Text(snap.neighborNote ?: "当前机型/系统不提供邻区信息",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                // 研究报告 §11-A2：邻区按制式分组（NR → LTE → WCDMA → TDS → GSM → CDMA → UNKNOWN），
                // 组内仍按键排序；空组不输出，避免「邻区 #1 · 未知」这种无信息行。
                var index = 0
                RadioTech.entries
                    .sortedBy { TECH_ORDER.indexOf(it).let { i -> if (i < 0) TECH_ORDER.size else i } }
                    .forEach { tech ->
                        val group = snap.neighbors.filter { it.tech == tech }
                        if (group.isEmpty()) return@forEach
                        Text("邻区 · ${tech.label}（${group.size}）", style = MaterialTheme.typography.labelLarge)
                        group.forEach { c ->
                            index += 1
                            Text("邻区 #$index", style = MaterialTheme.typography.labelSmall)
                            renderCell(c)
                        }
                    }
            }
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("信号强度趋势（RSRP，近 5 分钟）") {
            Text("RSRP ${snap.signalDbm.displayText()} dBm · 评级 ${snap.qualityGrade.displayText()}",
                style = MaterialTheme.typography.bodyMedium)
            EstimateDisclaimer()
            Spacer(Modifier.height(Dimen.Space2))
            // P14：接真实采样（AppViewModel 环形缓冲 → state.cellularTrend），
            // 先按 5 分钟窗口裁剪，再降频到最多 120 点；点不足 2 个时图表只画坐标轴（不画伪造曲线）。
            val trend = state.cellularTrend[slot].orEmpty()
            val windowed = com.shiji.pro.core.common.Sampling.withinWindow(
                trend.map { com.shiji.pro.core.common.Sampling.Point(it.timestampMs, it.value) },
                System.currentTimeMillis(),
                com.shiji.pro.core.common.Sampling.Window.MIN_5,
            )
            val down = com.shiji.pro.core.common.Sampling.downsample(windowed, 120)
            val chart = if (down.size < 2) emptyList() else down.mapIndexed { i, p ->
                val x = i / (down.size - 1).toFloat()
                // RSRP 常见区间 -140..-40 dBm 归一化
                val y = ((p.value + 140.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
                x to y
            }
            TrendChart(points = chart)
            Text(
                if (trend.isEmpty()) "尚无采样点：打开「自动采集」或点顶栏「刷新」后逐点累积（仅画真实采样，不画占位曲线）"
                else "采样点 ${trend.size} 个（窗口内 ${windowed.size}，绘制 ${down.size}）；20s/1min/5min 切换待接入",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun renderSim(sim: SimInfo) {
    renderFields(
        "运营商" to sim.carrierName, "MCC" to sim.mcc, "MNC" to sim.mnc,
        "注册 MCC" to sim.networkMcc, "注册 MNC" to sim.networkMnc,
        "IMSI" to sim.imsi, "ICCID" to sim.iccid, "电话号码" to sim.phoneNumber,
        "数据网络类型" to sim.dataNetworkType, "5G 显示制式" to sim.displayOverride,
        "漫游状态" to sim.roaming, "SIM 状态" to sim.simState,
    )
}

/** 服务小区/邻区字段渲染：字段名按 key 排序，值统一走三态徽标。 */
@Composable
private fun renderCell(cell: CellData) {
    cell.fields.entries.sortedBy { it.key }.forEach { (k, v) -> renderField(k, v) }
}

/** 邻区分组顺序（§11-A2）：新制式在前，未知殿后。 */
private val TECH_ORDER = listOf(
    RadioTech.NR, RadioTech.LTE, RadioTech.WCDMA,
    RadioTech.TDSCDMA, RadioTech.GSM, RadioTech.CDMA, RadioTech.UNKNOWN,
)
