package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CellData
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.RadioTech
import com.shiji.pro.core.model.SimInfo
import com.shiji.pro.core.ui.chart.TrendChart
import com.shiji.pro.core.ui.component.ChartFrame
import com.shiji.pro.core.ui.component.EstimateDisclaimer
import com.shiji.pro.core.ui.component.ListFrame
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner
import com.shiji.pro.core.ui.theme.Dimen

/**
 * 卡槽 1/2 蜂窝页（P7 + P15 恒定骨架）。
 *
 * 骨架恒定：状态条 + SIM / 服务小区 / 补充字段 / 邻区 / 趋势，5 张卡永远存在，
 * 字段按固定清单全部列出；取不到的值以 N/A + 原因占位，数据到达后原地更新，
 * 不再出现「权限未给 / 快照为空 → 整页只剩一张卡」或数据到达时整块跳出的情况。
 */
@Composable
internal fun CellularTab(state: AppState, slot: Int, modifier: Modifier) {
    val cap = state.capabilityOf(CollectorKey.CELLULAR)
    val snap = state.cellularSlots[slot]
    ScrollColumn(modifier) {
        PageStatusCard("蜂窝卡槽 ${slot + 1}", cap, hasSnapshot = snap != null)

        SectionCard(
            title = "SIM / 运营商（Slot $slot）",
            subtitle = "标识类字段受系统权限限制，取不到时显示 N/A",
            icon = ShiJiIconKind.DEVICE,
        ) {
            renderStableFields(SIM_LABELS, snap?.sim?.let { simValues(it) }.orEmpty())
        }

        val cell = snap?.servingCell
        SectionCard(
            title = "服务小区",
            subtitle = "6 制式字段清单固定展示，未上报项保持 N/A",
            icon = ShiJiIconKind.CELL,
        ) {
            val values = cell?.fields.orEmpty()
            renderStableFields(CELL_LABELS, values, reason = "未上报或当前制式不适用")
            Spacer(Modifier.height(Dimen.Space2))
            Text(
                "制式: ${cell?.tech?.label ?: "N/A"} · 注册状态: ${cell?.registered?.toString() ?: "N/A"}",
                style = MaterialTheme.typography.labelSmall,
            )
            StatusBanner("以上为服务小区完整字段清单；N/A 不代表数值为 0，均为未取得。")
        }

        val servingFields = cell?.fields.orEmpty()
        val extraKeys = servingFields.keys.filter { it !in CELL_LABELS }.sorted()
        SectionCard(
            title = "服务小区 · 补充字段（${extraKeys.size}）",
            subtitle = "换算字段 / 运营商归属等制式扩展项",
            icon = ShiJiIconKind.INFO,
        ) {
            ListFrame(
                itemCount = extraKeys.size,
                emptyMessage = "当前无额外字段（换算字段随采集结果出现）",
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    extraKeys.forEach { key -> renderField(key, servingFields.getValue(key)) }
                }
            }
        }

        val neighbors = snap?.neighbors.orEmpty()
        SectionCard(
            title = "邻区（${neighbors.size}）",
            subtitle = "按网络制式分组，组内按字段排序",
            icon = ShiJiIconKind.DRIVE,
        ) {
            ListFrame(
                itemCount = neighbors.size,
                emptyMessage = snap?.neighborNote ?: "当前机型/系统不提供邻区信息",
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // 研究报告 §11-A2：邻区按制式分组（NR → LTE → WCDMA → TDS → GSM → CDMA → UNKNOWN），
                    // 组内仍按键排序；空组不输出，避免「邻区 #1 · 未知」这种无信息行。
                    var index = 0
                    RadioTech.entries
                        .sortedBy { TECH_ORDER.indexOf(it).let { i -> if (i < 0) TECH_ORDER.size else i } }
                        .forEach { tech ->
                            val group = neighbors.filter { it.tech == tech }
                            if (group.isEmpty()) return@forEach
                            Text(
                                "邻区 · ${tech.label}（${group.size}）",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            group.forEach { c ->
                                index += 1
                                Text("邻区 #$index", style = MaterialTheme.typography.labelSmall)
                                renderCell(c)
                            }
                        }
                }
            }
        }

        SectionCard(
            title = "信号强度趋势（RSRP，近 5 分钟）",
            subtitle = "仅绘制真实采样点，不画占位曲线",
            icon = ShiJiIconKind.SPEED,
        ) {
            Text(
                "RSRP ${snap?.signalDbm?.displayText() ?: "N/A"} dBm · 评级 ${snap?.qualityGrade?.displayText() ?: "N/A"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            EstimateDisclaimer()
            Spacer(Modifier.height(Dimen.Space2))
            // P14：接真实采样（AppViewModel 环形缓冲 → state.cellularTrend），
            // 先按 5 分钟窗口裁剪，再降频到最多 120 点；点不足 2 个时只显示固定高度占位框。
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
            ChartFrame(
                description = "卡槽 ${slot + 1} 近 5 分钟 RSRP 趋势",
                hasData = chart.size >= 2,
                emptyMessage = "尚无采样点：打开「自动采集」或点顶栏「刷新」后逐点累积；仅画真实采样",
            ) {
                TrendChart(
                    points = chart,
                    description = "卡槽 ${slot + 1} 近 5 分钟 RSRP 趋势，窗口内 ${windowed.size} 点，绘制 ${down.size} 点",
                )
            }
            StatusBanner(
                "采样点 ${trend.size} 个（窗口内 ${windowed.size}，绘制 ${down.size}）；20s/1min/5min 切换待接入",
                fixedHeight = true,
            )
        }
    }
}

/** SIM 字段固定清单：所有项均展示，缺值填 N/A。 */
private val SIM_LABELS = listOf(
    "运营商", "MCC", "MNC", "注册 MCC", "注册 MNC",
    "IMSI", "ICCID", "电话号码", "数据网络类型", "5G 显示制式",
    "漫游状态", "SIM 状态", "卡槽索引", "SubscriptionId", "默认数据卡",
)

private fun simValues(sim: SimInfo): Map<String, FieldValue<*>> = mapOf(
    "运营商" to sim.carrierName, "MCC" to sim.mcc, "MNC" to sim.mnc,
    "注册 MCC" to sim.networkMcc, "注册 MNC" to sim.networkMnc,
    "IMSI" to sim.imsi, "ICCID" to sim.iccid, "电话号码" to sim.phoneNumber,
    "数据网络类型" to sim.dataNetworkType, "5G 显示制式" to sim.displayOverride,
    "漫游状态" to sim.roaming, "SIM 状态" to sim.simState,
    "卡槽索引" to FieldValue.measured(sim.slotIndex),
    "SubscriptionId" to (sim.subscriptionId?.let { FieldValue.measured(it) }
        ?: na("系统未提供 subscriptionId")),
    "默认数据卡" to FieldValue.measured(sim.isDefaultData),
)

/**
 * 服务小区固定字段清单：覆盖 6 制式 CellParsers 的全部键（LTE / NR / GSM / WCDMA / TDS / CDMA）。
 * 无论当前注册制式如何都全部列出，未上报项显示 N/A，避免「检测不出来就不写」。
 */
private val CELL_LABELS = listOf(
    // LTE
    "TAC", "CI", "PCI", "EARFCN", "Band", "Bandwidth", "RSRP", "RSRQ", "SINR",
    "RSSI", "TA", "CQI", "Level",
    // NR
    "NR-PCI", "NCI(NR-CGI)", "NR-ARFCN", "Bands", "SS-RSRP", "SS-RSRQ", "SS-SINR",
    "CSI-RSRP", "CSI-RSRQ", "CSI-SINR", "NR",
    // GSM / WCDMA / TDSCDMA
    "LAC", "ARFCN", "BSIC", "RXLEV", "ASU", "UARFCN", "PSC", "CPID",
    // CDMA / EVDO
    "NID", "BID", "SID", "CDMA dBm", "CDMA EcIo", "EVDO dBm", "EVDO EcIo", "EVDO SNR",
)

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
