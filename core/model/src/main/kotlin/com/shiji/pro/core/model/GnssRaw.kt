package com.shiji.pro.core.model

/**
 * GNSS 原始观测与导航电文（研究报告 §11-A0 的落地契约，2026-11-12 冻结）。
 *
 * 数据来源全部是**公开 API**（无私有通道）：
 * - `LocationManager.registerGnssMeasurementsCallback` → [GnssMeasurementsEvent]（API 24+）
 *   ⇒ `GnssClock` + `GnssMeasurement[]`（伪距率 / 载波相位 / 多径 / AGC / 钟差）
 * - `LocationManager.registerGnssNavigationMessageCallback` → `GnssNavigationMessage`（API 24+）
 * - `LocationManager.getGnssHardwareModelName()` / `getGnssYearOfHardware()`（API 31+）
 *
 * 三态纪律（与全工程一致）：
 * - `GnssMeasurement` / `GnssClock` 的字段是「`hasXxx()` 有效位 + 取值」成对出现，
 *   有效位为 false 时必须产出 [FieldValue.unavailable]（原因写清「设备未上报该分量」），
 *   **禁止用 0 或缺省值伪装实测**。这里的数据结构因此把所有分量都包成 FieldValue。
 * - DOP（精度因子）由卫星几何计算得出，属**估算值**（[FieldValue.estimated]），
 *   必须附「由仰角/方位角几何解算，估算值」说明。
 */

/** `GnssClock` 摘录（全部字段按 `hasXxx()` 判定，取不到一律 N/A）。 */
data class GnssClockInfo(
    val timeNanos: FieldValue<Long>,
    val fullBiasNanos: FieldValue<Long>,
    val biasNanos: FieldValue<Double>,
    val biasUncertaintyNanos: FieldValue<Double>,
    val driftNanosPerSecond: FieldValue<Double>,
    val driftUncertaintyNanosPerSecond: FieldValue<Double>,
    val leapSecond: FieldValue<Int>,
    val timeUncertaintyNanos: FieldValue<Double>,
    val hardwareClockDiscontinuityCount: FieldValue<Int>,
)

/** 单颗卫星的原始观测（`GnssMeasurement` 的分量级摘录）。 */
data class GnssMeasurementInfo(
    val svid: Int,
    val constellation: GnssConstellation,
    /** `getPseudorangeRateMetersPerSecond()`：伪距率（m/s），PVT 解算的核心观测量。 */
    val pseudorangeRateMps: FieldValue<Double>,
    val pseudorangeRateUncertaintyMps: FieldValue<Double>,
    /** `getReceivedSvTimeNanos()`：接收到的卫星时间（ns）。 */
    val receivedSvTimeNanos: FieldValue<Long>,
    /** `getCn0DbHz()`：载噪比（dB-Hz）。 */
    val cn0DbHz: FieldValue<Double>,
    /** `getCarrierFrequencyHz()`：载波频率（Hz），API 26+。 */
    val carrierFrequencyHz: FieldValue<Float>,
    val carrierCycles: FieldValue<Long>,
    val carrierPhase: FieldValue<Double>,
    val carrierPhaseUncertainty: FieldValue<Double>,
    /** `getAccumulatedDeltaRangeMeters()`：累积载波相位变化（m）。 */
    val accumulatedDeltaRangeMeters: FieldValue<Double>,
    /** `getAccumulatedDeltaRangeState()`：位标志（1=VALID / 2=RESET / 4=CYCLE_SLIP）。 */
    val accumulatedDeltaRangeState: FieldValue<Int>,
    /** `getMultipathIndicator()`：0=UNKNOWN / 1=DETECTED / 2=NOT_DETECTED。 */
    val multipathIndicator: FieldValue<Int>,
    /** `getAutomaticGainControlLevelDb()`：AGC 增益（dB）。 */
    val automaticGainControlLevelDb: FieldValue<Double>,
    /** `getSnrInDb()`：信噪比（dB），API 26+。 */
    val snrInDb: FieldValue<Double>,
)

/** 最近一条导航电文（星历/历书等，`GnssNavigationMessage` 摘要；原始字节只保留最近一条）。 */
data class GnssNavMessageInfo(
    val svid: Int,
    /** `getType()`：1=GPS L1CA / 2=GPS L2C / 4=GPS L5 / 5=GLONASS L1CA / 7=BDS D1 / 8=BDS D2 / 9=GAL INAV … */
    val type: Int,
    val submessageId: Int,
    /** `getData()` 的十六进制串（最多 40 字节，原文不丢精度）。 */
    val dataHex: String,
    val receivedAtMs: Long,
)

/**
 * DOP（Dilution of Precision）——由参与定位卫星的仰角/方位角解算的**估算值**。
 * 只有 `usedSatellites >= 4` 时才可解算（少于 4 颗几何上不可解），否则整个对象为 null。
 */
data class DopInfo(
    val hdop: FieldValue<Double>,
    val vdop: FieldValue<Double>,
    val pdop: FieldValue<Double>,
    val usedSatellites: Int,
    val note: String? = "由仰角/方位角几何解算，估算值，仅供参考",
)

/** 一次原始观测快照（挂在 [GnssSnapshot.raw] 上，随 GNSS 回调持续更新）。 */
data class GnssRawObservation(
    val timestampMs: Long,
    val clock: GnssClockInfo?,
    val measurements: List<GnssMeasurementInfo>,
    /** 本次会话收到的导航电文条数（累计）。 */
    val navMessageCount: Int,
    val lastNavMessage: GnssNavMessageInfo?,
    val dop: DopInfo?,
    /** 本次会话首个定位结果相对会话开始的耗时（ms）；无定位前为 N/A。 */
    val ttffMs: FieldValue<Long>,
    /** `getGnssHardwareModelName()`（API 31+）。 */
    val hardwareModel: FieldValue<String>,
    /** `getGnssYearOfHardware()`（API 31+）。 */
    val hardwareYear: FieldValue<Int>,
    /** 无数据/不支持时的统一说明（如「设备未输出原始观测」「未授权位置权限」）。 */
    val note: String? = null,
)
