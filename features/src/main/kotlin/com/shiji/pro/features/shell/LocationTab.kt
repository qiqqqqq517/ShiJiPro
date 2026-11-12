package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.ui.chart.SkyPlot
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.theme.Dimen

/** 位置页（P8 + 研究报告 §11-A0）：定位 / 卫星 / **原始观测与导航电文** / NMEA。 */
@Composable
internal fun LocationTab(state: AppState, modifier: Modifier) {
    val cap = state.capabilityOf(CollectorKey.GNSS)
    ScrollColumn(modifier) {
        if (cap?.available == false) { PermissionGate(cap); return@ScrollColumn }
        val snap = state.gnss
        if (snap == null) { SectionCard("位置") { Text("尚未刷新或定位未就绪", style = MaterialTheme.typography.bodyMedium) }; return@ScrollColumn }
        snap.fix?.let { f ->
            SectionCard("定位数据（WGS-84）") {
                renderFields(
                    "经度" to f.latitude, "纬度" to f.longitude,
                    "精度(m)" to f.accuracyMeters, "高度(m)" to f.altitudeMeters,
                    "方位角(°)" to f.bearingDegrees, "速度(m/s)" to f.speedMps,
                    "逆地理编码" to f.address,
                )
                Text("定位方式: ${f.provider.label}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Dimen.Space3))
        }
        SectionCard("卫星天空图（${snap.satellites.size} 颗）") {
            SkyPlot(snap.satellites)
            Text("中心=天顶 90°，边缘=地平；外圈描边=参与定位", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("卫星列表") {
            snap.satellites.forEach { s ->
                Text("${s.constellation.label} SVID ${s.svid}  C/N0 ${s.cn0DbHz.displayText()}  " +
                    "基带 ${s.basebandCn0DbHz.displayText()}  " +
                    "方位 ${s.azimuthDegrees.displayText()}°  高度 ${s.elevationDegrees.displayText()}°  " +
                    "历书=${s.hasAlmanac} 星历=${s.hasEphemeris} 使用=${s.usedInFix}",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(Dimen.Space3))
        RawObservationCard(snap.raw)
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("NMEA 原始日志") {
            Text(snap.nmeaNote ?: "记录未开启", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A0 卡片：GNSS 原始观测与导航电文。
 *
 * 数据全部来自公开 API（GnssMeasurementsCallback / GnssNavigationMessageCallback / GnssStatus 基带 C/N0 /
 * getGnssHardwareModelName）。设备不输出时 raw == null → 显示降级说明，**不显示一堆 0**。
 */
@Composable
private fun RawObservationCard(raw: com.shiji.pro.core.model.GnssRawObservation?) {
    SectionCard("原始观测与导航电文") {
        if (raw == null) {
            Text(
                "未发起原始观测订阅（常见原因：未授予定位权限，或系统未提供 LocationManager）。" +
                    "授权后请点顶栏「刷新」重试。",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@SectionCard
        }
        renderFields(
            "卫星原始观测数" to FieldValue.measured(raw.measurements.size),
            "HDOP" to (raw.dop?.hdop ?: FieldValue.unavailable<Double>("少于 4 颗参与定位，DOP 不可解算")),
            "VDOP" to (raw.dop?.vdop ?: FieldValue.unavailable<Double>("少于 4 颗参与定位，DOP 不可解算")),
            "PDOP" to (raw.dop?.pdop ?: FieldValue.unavailable<Double>("少于 4 颗参与定位，DOP 不可解算")),
            "TTFF(ms)" to raw.ttffMs,
            "GNSS 硬件型号" to raw.hardwareModel,
            "硬件年份" to raw.hardwareYear,
            "导航电文条数" to FieldValue.measured(raw.navMessageCount),
        )
        raw.clock?.let { c ->
            Spacer(Modifier.height(Dimen.Space2))
            Text("时钟（GnssClock）", style = MaterialTheme.typography.labelLarge)
            Text(
                "timeNanos=${c.timeNanos.displayText()}  fullBiasNanos=${c.fullBiasNanos.displayText()}  " +
                    "biasNanos=${c.biasNanos.displayText()}  drift=${c.driftNanosPerSecond.displayText()} ns/s  " +
                    "leapSecond=${c.leapSecond.displayText()}  时钟不连续计数=${c.hardwareClockDiscontinuityCount.displayText()}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (raw.measurements.isNotEmpty()) {
            Spacer(Modifier.height(Dimen.Space2))
            Text("测量明细（前 8 颗）", style = MaterialTheme.typography.labelLarge)
            raw.measurements.take(8).forEach { m ->
                Text(
                    "${m.constellation.label} SVID ${m.svid}  伪距率=${m.pseudorangeRateMps.displayText()} m/s  " +
                        "C/N0=${m.cn0DbHz.displayText()}  多径=${m.multipathIndicator.displayText()}  " +
                        "载波相位=${m.carrierPhase.displayText()}  AGC=${m.automaticGainControlLevelDb.displayText()}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        raw.lastNavMessage?.let { nav ->
            Spacer(Modifier.height(Dimen.Space2))
            Text(
                "最近一条电文：SVID ${nav.svid} · type ${nav.type} · submessage ${nav.submessageId} · " +
                    nav.dataHex.take(40) + if (nav.dataHex.length > 40) "…" else "",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        raw.note?.let {
            Spacer(Modifier.height(Dimen.Space2))
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            "口径：原始观测/导航电文为公开 API（GnssMeasurement/GnssClock/GnssNavigationMessage）；" +
                "DOP 为几何解算估算值；TTFF 为应用会话内首个定位耗时（非系统级）。",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
