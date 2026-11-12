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
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.GnssFix
import com.shiji.pro.core.model.GnssRawObservation
import com.shiji.pro.core.ui.chart.SkyPlot
import com.shiji.pro.core.ui.component.ChartFrame
import com.shiji.pro.core.ui.component.ListFrame
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner
import com.shiji.pro.core.ui.theme.Dimen

/** 位置页（P8 + §11-A0 + P15 恒定骨架）：定位 / 天空图 / 卫星列表 / 原始观测 / NMEA，5 张卡永远存在。 */
@Composable
internal fun LocationTab(state: AppState, modifier: Modifier) {
    val cap = state.capabilityOf(CollectorKey.GNSS)
    val snap = state.gnss
    val fix = snap?.fix
    val satellites = snap?.satellites.orEmpty()
    val raw = snap?.raw
    ScrollColumn(modifier) {
        PageStatusCard("GNSS / 位置", cap, hasSnapshot = snap != null)

        SectionCard(
            title = "定位数据（WGS-84）",
            subtitle = "经纬度 / 精度 / 高度 / 运动状态（7 项全部列出）",
            icon = ShiJiIconKind.LOCATION,
        ) {
            renderStableFields(GNSS_FIX_LABELS, fix?.let { fixValues(it) }.orEmpty())
            Text(
                "定位方式: ${fix?.provider?.label ?: "N/A"} · 坐标类型: ${fix?.coordinateType ?: "N/A"}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        SectionCard(
            title = "卫星天空图（${satellites.size} 颗）",
            subtitle = "中心=天顶 90°，边缘=地平",
            icon = ShiJiIconKind.LOCATION,
        ) {
            ChartFrame(
                description = "卫星天空图",
                hasData = satellites.isNotEmpty(),
                emptyMessage = "暂无卫星数据：等待 GNSS 回调或检查定位权限，图表区域保持占位",
            ) {
                SkyPlot(
                    satellites,
                    description = "卫星天空图，共 ${satellites.size} 颗卫星，外圈描边表示参与定位",
                )
            }
            StatusBanner("外圈描边=参与定位；颜色区分卫星星座")
        }

        SectionCard(
            title = "卫星列表（${satellites.size}）",
            subtitle = "星座 / SVID / C/N0 / 方位与高度角",
            icon = ShiJiIconKind.CELL,
        ) {
            ListFrame(
                itemCount = satellites.size,
                emptyMessage = "当前没有可见卫星数据",
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    satellites.forEach { s ->
                        Text(
                            "${s.constellation.label} SVID ${s.svid}  C/N0 ${s.cn0DbHz.displayText()}  " +
                                "基带 ${s.basebandCn0DbHz.displayText()}  " +
                                "方位 ${s.azimuthDegrees.displayText()}°  高度 ${s.elevationDegrees.displayText()}°  " +
                                "历书=${s.hasAlmanac} 星历=${s.hasEphemeris} 使用=${s.usedInFix}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        SectionCard(
            title = "原始观测与导航电文",
            subtitle = "公开 API 输出；DOP 为几何解算估算值",
            icon = ShiJiIconKind.INFO,
        ) {
            renderStableFields(RAW_LABELS, raw?.let { rawValues(it) }.orEmpty())
            Spacer(Modifier.height(Dimen.Space2))
            Text("时钟（GnssClock）", style = MaterialTheme.typography.labelLarge)
            Text(
                raw?.clock?.let { c ->
                    "timeNanos=${c.timeNanos.displayText()}  fullBiasNanos=${c.fullBiasNanos.displayText()}  " +
                        "biasNanos=${c.biasNanos.displayText()}  drift=${c.driftNanosPerSecond.displayText()} ns/s  " +
                        "leapSecond=${c.leapSecond.displayText()}  " +
                        "时钟不连续计数=${c.hardwareClockDiscontinuityCount.displayText()}"
                } ?: "N/A · 未发起原始观测订阅或系统未上报 GnssClock",
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(Dimen.Space2))
            Text("测量明细（最多 8 颗）", style = MaterialTheme.typography.labelLarge)
            val measurements = raw?.measurements?.take(8).orEmpty()
            ListFrame(
                itemCount = measurements.size,
                emptyMessage = "暂无原始观测明细",
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    measurements.forEach { m ->
                        Text(
                            "${m.constellation.label} SVID ${m.svid}  伪距率=${m.pseudorangeRateMps.displayText()} m/s  " +
                                "C/N0=${m.cn0DbHz.displayText()}  多径=${m.multipathIndicator.displayText()}  " +
                                "载波相位=${m.carrierPhase.displayText()}  AGC=${m.automaticGainControlLevelDb.displayText()}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimen.Space2))
            Text("最近一条导航电文", style = MaterialTheme.typography.labelLarge)
            Text(
                raw?.lastNavMessage?.let { nav ->
                    "SVID ${nav.svid} · type ${nav.type} · submessage ${nav.submessageId} · " +
                        nav.dataHex.take(40) + if (nav.dataHex.length > 40) "…" else ""
                } ?: "N/A · 暂无导航电文",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                "补充说明: ${raw?.note ?: "N/A · 无"}",
                style = MaterialTheme.typography.labelSmall,
            )
            StatusBanner(
                "口径：原始观测/导航电文为公开 API（GnssMeasurement/GnssClock/GnssNavigationMessage）；" +
                    "DOP 为几何解算估算值；TTFF 为应用会话内首个定位耗时（非系统级）。",
            )
        }

        SectionCard(title = "NMEA 原始日志", icon = ShiJiIconKind.LOGS) {
            StatusBanner(snap?.nmeaNote ?: "N/A · 记录未开启", fixedHeight = true)
        }
    }
}

/** 定位字段固定清单。沿用原有标签与字段对应关系，不做语义改写。 */
private val GNSS_FIX_LABELS = listOf(
    "经度", "纬度", "精度(m)", "高度(m)", "方位角(°)", "速度(m/s)", "逆地理编码",
)

private fun fixValues(f: GnssFix): Map<String, FieldValue<*>> = mapOf(
    // 修正：原实现把 latitude 填进「经度」、longitude 填进「纬度」，标签与数值互换。
    "经度" to f.longitude, "纬度" to f.latitude,
    "精度(m)" to f.accuracyMeters, "高度(m)" to f.altitudeMeters,
    "方位角(°)" to f.bearingDegrees, "速度(m/s)" to f.speedMps,
    "逆地理编码" to f.address,
)

/** 原始观测字段固定清单。 */
private val RAW_LABELS = listOf(
    "卫星原始观测数", "HDOP", "VDOP", "PDOP", "TTFF(ms)", "GNSS 硬件型号", "硬件年份", "导航电文条数",
)

private fun rawValues(raw: GnssRawObservation): Map<String, FieldValue<*>> = mapOf(
    "卫星原始观测数" to FieldValue.measured(raw.measurements.size),
    "HDOP" to (raw.dop?.hdop ?: na("少于 4 颗参与定位，DOP 不可解算")),
    "VDOP" to (raw.dop?.vdop ?: na("少于 4 颗参与定位，DOP 不可解算")),
    "PDOP" to (raw.dop?.pdop ?: na("少于 4 颗参与定位，DOP 不可解算")),
    "TTFF(ms)" to raw.ttffMs,
    "GNSS 硬件型号" to raw.hardwareModel,
    "硬件年份" to raw.hardwareYear,
    "导航电文条数" to FieldValue.measured(raw.navMessageCount),
)
