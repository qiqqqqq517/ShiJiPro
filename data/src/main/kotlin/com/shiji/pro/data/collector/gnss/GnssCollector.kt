package com.shiji.pro.data.collector.gnss

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssClock
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.shiji.pro.core.common.DopCalculator
import com.shiji.pro.core.domain.CapabilityGate
import com.shiji.pro.core.domain.Collector
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.GnssClockInfo
import com.shiji.pro.core.model.GnssConstellation
import com.shiji.pro.core.model.GnssFix
import com.shiji.pro.core.model.GnssMeasurementInfo
import com.shiji.pro.core.model.GnssNavMessageInfo
import com.shiji.pro.core.model.GnssRawObservation
import com.shiji.pro.core.model.GnssSnapshot
import com.shiji.pro.core.model.LocationProvider
import com.shiji.pro.core.model.Satellite
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * GNSS / 定位采集器（设计稿 §4.6 + 研究报告 §11-A0）。
 * 平台 LocationManager（不依赖 GMS 的 FusedLocationProviderClient）。
 *
 * - 定位：requestLocationUpdates；network provider 无法区分 CELL/WIFI → 合并口径，不臆断。
 * - 卫星：GnssStatus.Callback（API 24+）→ SVID / 星座 / C/N0 / 方位角 / 高度角 / 历书 / 星历 / usedInFix，
 *   并补 **基带 C/N0**（`getBasebandCn0DbHz`，API 30+ 且 `hasBasebandCn0DbHz`）。
 * - **原始观测**（A0）：`registerGnssMeasurementsCallback`（API 24+）→ GnssClock + GnssMeasurement[]，
 *   伪距率/载波相位/多径/AGC/ADR 状态全部按 `hasXxx()` 判定，未上报 → N/A（不填 0）。
 * - **导航电文**（A0）：`registerGnssNavigationMessageCallback`（API 24+）→ 条数累计 + 最近一条十六进制。
 * - **DOP**（A0）：由参与定位卫星的方位/仰角几何解算（[DopCalculator]），估算值。
 * - **TTFF**（A0）：本会话首个定位相对订阅开始的耗时（应用级口径，估算，非系统级 TTFF）。
 * - **硬件**：`getGnssHardwareModelName()` / `getGnssYearOfHardware()`（API 28+）。
 * - NMEA：LocationManager.addNmeaListener（API 24+）→ 由 NmeaRepository 落库。
 * - 逆地理编码：依赖设备内置后端，离线/无 GMS 常返回空 → N/A。
 */
class GnssCollector(
    private val context: Context,
    private val gate: CapabilityGate,
) : Collector<GnssSnapshot> {

    override val key: CollectorKey = CollectorKey.GNSS

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile private var lastFix: GnssFix? = null
    @Volatile private var satellites: List<Satellite> = emptyList()
    @Volatile private var nmeaEnabled = false

    // ---- A0：原始观测 / 导航电文 / TTFF ----
    @Volatile private var clockInfo: GnssClockInfo? = null
    @Volatile private var measurements: List<GnssMeasurementInfo> = emptyList()
    @Volatile private var navMessageCount = 0
    @Volatile private var lastNavMessage: GnssNavMessageInfo? = null
    @Volatile private var sessionStartMs: Long = 0L
    @Volatile private var firstFixAtMs: Long? = null
    /** 原始观测注册结果：未尝试 / 注册失败原因（本机 HAL 不支持是常见情况，必须如实显示）。 */
    @Volatile private var rawAttempted = false
    @Volatile private var rawRegisterError: String? = null

    override fun capability(): Flow<CapabilityState> =
        kotlinx.coroutines.flow.flow { emit(gate.evaluate(key)) }

    /** 持续更新：注册 LocationListener + GnssStatus + 原始观测/电文回调，close 时全部反注册。 */
    // 原始观测/导航电文 API 为 API 24+，本函数内所有调用都在
    // `Build.VERSION.SDK_INT >= N` 分支下创建/注册（回调对象本身也只在 24+ 构造），
    // lint 无法跨变量追踪该守卫，故显式抑制 NewApi；低版本不会执行到这些调用。
    @android.annotation.SuppressLint("NewApi")
    override fun observe(): Flow<GnssSnapshot> = callbackFlow {
        if (!hasFineLocation()) {
            trySend(snapshot())
            awaitClose { }
            return@callbackFlow
        }
        val lm = locationManager
        if (lm == null) {
            trySend(snapshot())
            awaitClose { }
            return@callbackFlow
        }
        sessionStartMs = System.currentTimeMillis()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (firstFixAtMs == null) firstFixAtMs = System.currentTimeMillis()
                lastFix = location.toFix()
                trySend(snapshot())
            }
            @Deprecated("Compat for API < 30")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        val gnssCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    satellites = parseSatellites(status)
                    trySend(snapshot())
                }
            }
        } else null

        val measurementsCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            object : GnssMeasurementsEvent.Callback() {
                override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                    clockInfo = event.clock?.let { it.toClockInfo() }
                    measurements = event.measurements.map { it.toMeasurementInfo() }
                    trySend(snapshot())
                }
            }
        } else null

        val navMessageCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            object : GnssNavigationMessage.Callback() {
                override fun onGnssNavigationMessageReceived(message: GnssNavigationMessage) {
                    navMessageCount += 1
                    lastNavMessage = GnssNavMessageInfo(
                        svid = message.svid,
                        type = message.type,
                        submessageId = message.submessageId,
                        dataHex = message.data.joinToString("") { b -> "%02X".format(b.toInt() and 0xFF) },
                        receivedAtMs = System.currentTimeMillis(),
                    )
                    trySend(snapshot())
                }
            }
        } else null

        try {
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> LocationManager.GPS_PROVIDER
            }
            @Suppress("MissingPermission")
            lm.requestLocationUpdates(provider, 1000L, 0f, listener, context.mainLooper)
            if (gnssCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                @Suppress("MissingPermission")
                lm.registerGnssStatusCallback(gnssCallback, null)
            }
            // A0：原始观测与导航电文回调。机型/系统不支持时 register* 会抛
            // UnsupportedOperationException / SecurityException —— 逐个 try 吞掉，
            // 降级为 raw=null（UI 显示「本机未输出原始观测」），不影响定位与卫星。
            if (measurementsCallback != null) {
                rawAttempted = true
                try {
                    @Suppress("MissingPermission")
                    lm.registerGnssMeasurementsCallback(measurementsCallback)
                    rawRegisterError = null
                } catch (e: Throwable) {
                    // 机型 HAL 不支持原始观测时 register 会抛 UnsupportedOperationException；
                    // 记录原因并在卡片里如实展示（不静默、不伪造）。
                    rawRegisterError = e.javaClass.simpleName + ": " + (e.message ?: "无消息")
                }
            }
            if (navMessageCallback != null) {
                try {
                    @Suppress("MissingPermission")
                    lm.registerGnssNavigationMessageCallback(navMessageCallback)
                } catch (_: Throwable) { }
            }
            trySend(snapshot())
        } catch (e: SecurityException) {
            trySend(snapshot())
        }
        awaitClose {
            try { lm.removeUpdates(listener) } catch (_: Throwable) {}
            if (gnssCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { lm.unregisterGnssStatusCallback(gnssCallback) } catch (_: Throwable) {}
            }
            if (measurementsCallback != null) {
                try { lm.unregisterGnssMeasurementsCallback(measurementsCallback) } catch (_: Throwable) {}
            }
            if (navMessageCallback != null) {
                try { lm.unregisterGnssNavigationMessageCallback(navMessageCallback) } catch (_: Throwable) {}
            }
        }
    }

    fun snapshot(): GnssSnapshot = GnssSnapshot(
        timestampMs = System.currentTimeMillis(),
        fix = lastFix,
        satellites = satellites,
        nmeaEnabled = nmeaEnabled,
        nmeaNote = if (!nmeaEnabled) "NMEA 记录未开启" else null,
        raw = buildRawObservation(),
    )

    /**
     * 组装原始观测：只有真正发起过注册才产出对象（未授权/无 LocationManager → null，UI 走降级空态）。
     *
     * 三种真实状态都如实表达，不把「无回调」伪装成「0 颗」：
     * - 注册抛异常 → note 写明异常类型（多为机型 HAL 不支持）；
     * - 注册成功但尚无回调 → note 说明「尚未收到回调」；
     * - 有回调 → note 为 null（`measurements` 明细见 UI）。
     */
    private fun buildRawObservation(): GnssRawObservation? {
        if (!rawAttempted) return null
        val used = satellites.filter { it.usedInFix }
        val dop = if (used.size >= 4) {
            DopCalculator.compute(
                used.map { (it.azimuthDegrees.value ?: 0f).toDouble() },
                used.map { (it.elevationDegrees.value ?: 0f).toDouble() },
            )
        } else null
        val note = when {
            rawRegisterError != null -> "原始观测注册失败：$rawRegisterError（该机型 GNSS HAL 可能不支持 GnssMeasurement）"
            clockInfo == null && measurements.isEmpty() -> "已注册原始观测回调，但尚未收到回调（部分机型不输出 GnssMeasurement/GnssClock）"
            measurements.isEmpty() -> "本次回调未携带卫星原始观测（设备仅输出钟信息）"
            else -> null
        }
        return GnssRawObservation(
            timestampMs = System.currentTimeMillis(),
            clock = clockInfo,
            measurements = measurements,
            navMessageCount = navMessageCount,
            lastNavMessage = lastNavMessage,
            dop = dop,
            ttffMs = ttffField(),
            hardwareModel = hardwareModelField(),
            hardwareYear = hardwareYearField(),
            note = note,
        )
    }

    /** 应用会话内首个定位耗时（估算口径，非系统级 TTFF）。 */
    private fun ttffField(): FieldValue<Long> {
        val t0 = sessionStartMs
        val t1 = firstFixAtMs
        return when {
            t0 == 0L -> FieldValue.unavailable("尚未开始 GNSS 订阅")
            t1 == null -> FieldValue.unavailable("尚未获得定位，无法计算 TTFF")
            else -> FieldValue.estimated(t1 - t0, "应用会话内首个定位耗时（估算，非系统级 TTFF）")
        }
    }

    private fun hardwareModelField(): FieldValue<String> = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> FieldValue.unavailable("getGnssHardwareModelName() 需 API 28+")
        else -> try {
            locationManager?.gnssHardwareModelName?.takeIf { it.isNotBlank() }
                ?.let { FieldValue.measured(it) }
                ?: FieldValue.unavailable("系统未提供 GNSS 硬件型号")
        } catch (_: Throwable) {
            FieldValue.unavailable("读取 GNSS 硬件型号被系统拒绝")
        }
    }

    private fun hardwareYearField(): FieldValue<Int> = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> FieldValue.unavailable("getGnssYearOfHardware() 需 API 28+")
        else -> try {
            val year = locationManager?.gnssYearOfHardware ?: 0
            if (year > 0) FieldValue.measured(year)
            else FieldValue.unavailable("系统未提供 GNSS 硬件年份")
        } catch (_: Throwable) {
            FieldValue.unavailable("读取 GNSS 硬件年份被系统拒绝")
        }
    }

    private fun Location.toFix(): GnssFix = GnssFix(
        timestampMs = time,
        latitude = FieldValue.measured(latitude),
        longitude = FieldValue.measured(longitude),
        provider = providerFrom(this@toFix.provider),
        accuracyMeters = if (hasAccuracy()) FieldValue.measured(accuracy)
            else FieldValue.unavailable("定位精度取不到"),
        altitudeMeters = if (hasAltitude()) FieldValue.measured(altitude)
            else FieldValue.unavailable("高度分量取不到"),
        bearingDegrees = if (hasBearing()) FieldValue.measured(bearing)
            else FieldValue.unavailable("方位角分量取不到"),
        speedMps = if (hasSpeed()) FieldValue.measured(speed)
            else FieldValue.unavailable("速度分量取不到"),
        address = FieldValue.unavailable("逆地理编码需设备内置后端，离线/无 GMS 常不可用"),
    )

    /** network 源无法区分 CELL 与 WIFI，按设计稿合并口径。 */
    private fun providerFrom(name: String?): LocationProvider = when (name) {
        LocationManager.GPS_PROVIDER -> LocationProvider.GPS
        LocationManager.NETWORK_PROVIDER -> LocationProvider.NETWORK
        LocationManager.FUSED_PROVIDER -> LocationProvider.FUSED
        else -> LocationProvider.UNKNOWN
    }

    @android.annotation.TargetApi(24)
    private fun parseSatellites(status: GnssStatus): List<Satellite> {
        val count = status.satelliteCount
        return (0 until count).map { i ->
            Satellite(
                svid = status.getSvid(i),
                constellation = GnssConstellation.fromTypeId(status.getConstellationType(i)),
                cn0DbHz = FieldValue.measured(status.getCn0DbHz(i)),
                azimuthDegrees = FieldValue.measured(status.getAzimuthDegrees(i)),
                elevationDegrees = FieldValue.measured(status.getElevationDegrees(i)),
                hasAlmanac = status.hasAlmanacData(i),
                hasEphemeris = status.hasEphemerisData(i),
                usedInFix = status.usedInFix(i),
                basebandCn0DbHz = basebandCn0Field(status, i),
            )
        }
    }

    /** A0：基带 C/N0（API 30+；`hasBasebandCn0DbHz` 为 false 或低版本 → N/A 带原因）。 */
    @android.annotation.TargetApi(30)
    private fun basebandCn0Field(status: GnssStatus, index: Int): FieldValue<Float> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return FieldValue.unavailable("getBasebandCn0DbHz 需 API 30+")
        }
        return try {
            if (status.hasBasebandCn0DbHz(index)) {
                FieldValue.measured(status.getBasebandCn0DbHz(index))
            } else {
                FieldValue.unavailable("本机未上报该卫星的基带 C/N0")
            }
        } catch (_: Throwable) {
            FieldValue.unavailable("读取基带 C/N0 失败")
        }
    }

    // ---------------- A0：GnssClock / GnssMeasurement 映射 ----------------

    /** 「hasXxx 为 false → N/A」的成对结构映射；这是三态口径在原始观测上的落点。 */
    @android.annotation.TargetApi(24)
    private fun GnssClock.toClockInfo(): GnssClockInfo = GnssClockInfo(
        timeNanos = FieldValue.measured(timeNanos),
        fullBiasNanos = if (hasFullBiasNanos()) FieldValue.measured(fullBiasNanos)
            else FieldValue.unavailable("时钟未上报 fullBiasNanos"),
        biasNanos = if (hasBiasNanos()) FieldValue.measured(biasNanos)
            else FieldValue.unavailable("时钟未上报 biasNanos"),
        biasUncertaintyNanos = if (hasBiasUncertaintyNanos()) FieldValue.measured(biasUncertaintyNanos)
            else FieldValue.unavailable("时钟未上报 biasUncertaintyNanos"),
        driftNanosPerSecond = if (hasDriftNanosPerSecond()) FieldValue.measured(driftNanosPerSecond)
            else FieldValue.unavailable("时钟未上报 driftNanosPerSecond"),
        driftUncertaintyNanosPerSecond = if (hasDriftUncertaintyNanosPerSecond()) {
            FieldValue.measured(driftUncertaintyNanosPerSecond)
        } else FieldValue.unavailable("时钟未上报 driftUncertaintyNanosPerSecond"),
        leapSecond = if (hasLeapSecond()) FieldValue.measured(leapSecond)
            else FieldValue.unavailable("时钟未上报 leapSecond"),
        timeUncertaintyNanos = if (hasTimeUncertaintyNanos()) FieldValue.measured(timeUncertaintyNanos)
            else FieldValue.unavailable("时钟未上报 timeUncertaintyNanos"),
        hardwareClockDiscontinuityCount = FieldValue.measured(hardwareClockDiscontinuityCount),
    )

    @android.annotation.TargetApi(24)
    private fun GnssMeasurement.toMeasurementInfo(): GnssMeasurementInfo = GnssMeasurementInfo(
        svid = svid,
        constellation = GnssConstellation.fromTypeId(constellationType),
        // 基础观测量（API 24+ 恒提供，无 hasXxx 成对结构）→ 直接按实测
        pseudorangeRateMps = FieldValue.measured(pseudorangeRateMetersPerSecond),
        pseudorangeRateUncertaintyMps = FieldValue.measured(pseudorangeRateUncertaintyMetersPerSecond),
        receivedSvTimeNanos = FieldValue.measured(receivedSvTimeNanos),
        cn0DbHz = FieldValue.measured(cn0DbHz),
        // 扩展观测量（API 26+ 有 hasXxx() 成对结构）→ 有效位为 false 时 N/A
        carrierFrequencyHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasCarrierFrequencyHz()) {
            FieldValue.measured(carrierFrequencyHz)
        } else FieldValue.unavailable("未上报载波频率（需 API 26+ 且机型支持）"),
        carrierCycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasCarrierCycles()) {
            FieldValue.measured(carrierCycles)
        } else FieldValue.unavailable("未上报载波周数（需 API 26+）"),
        carrierPhase = if (hasCarrierPhase()) FieldValue.measured(carrierPhase)
            else FieldValue.unavailable("未上报载波相位"),
        carrierPhaseUncertainty = if (hasCarrierPhaseUncertainty()) FieldValue.measured(carrierPhaseUncertainty)
            else FieldValue.unavailable("未上报载波相位不确定度"),
        accumulatedDeltaRangeMeters = FieldValue.measured(accumulatedDeltaRangeMeters),
        accumulatedDeltaRangeState = FieldValue.measured(accumulatedDeltaRangeState),
        // 多径：0=UNKNOWN（未判定）→ N/A；1=DETECTED / 2=NOT_DETECTED → 实测
        multipathIndicator = if (multipathIndicator == 0) {
            FieldValue.unavailable("多径指示未判定（0=UNKNOWN）")
        } else FieldValue.measured(multipathIndicator),
        automaticGainControlLevelDb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasAutomaticGainControlLevelDb()) {
            FieldValue.measured(automaticGainControlLevelDb)
        } else FieldValue.unavailable("未上报 AGC 增益（需 API 26+）"),
        snrInDb = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSnrInDb()) {
            FieldValue.measured(snrInDb)
        } else FieldValue.unavailable("未上报 SNR（需 API 26+）"),
    )

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
