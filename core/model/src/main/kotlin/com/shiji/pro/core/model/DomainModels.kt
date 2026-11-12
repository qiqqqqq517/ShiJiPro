package com.shiji.pro.core.model

/**
 * 全模块领域数据模型（纯 Kotlin，零框架依赖）。设计稿 §4 字段对照表的数据载体。
 * 所有「取不到」的字段用 FieldValue.unavailable 表达，不在模型里用 0/-1 占位。
 */

// ---------------------------------------------------------------- 蜂窝 ----

/** SIM / 运营商信息（按 slot 独立）。敏感字段以脱敏字符串承载。 */
data class SimInfo(
    val slotIndex: Int,
    val subscriptionId: Int?,
    val carrierName: FieldValue<String>,
    val mcc: FieldValue<String>,
    val mnc: FieldValue<String>,
    val networkMcc: FieldValue<String>,
    val networkMnc: FieldValue<String>,
    val imsi: FieldValue<String>,          // 通常 N/A（API 29+ 普通应用取不到）
    val iccid: FieldValue<String>,
    val phoneNumber: FieldValue<String>,
    val dataNetworkType: FieldValue<String>,
    val displayOverride: FieldValue<String>, // NR_NSA / NR_SA 等（API 30+）
    val roaming: FieldValue<Boolean>,
    val simState: FieldValue<String>,
    val isDefaultData: Boolean,
)

/** 服务小区 / 邻区共用的制式化小区数据。 */
data class CellData(
    val tech: RadioTech,
    val isServing: Boolean,
    val fields: Map<String, FieldValue<*>>,  // 字段名 → 三态值，键与 §4.2 一致
    val registered: Boolean = false,
)

/** 单个卡槽的完整蜂窝快照。 */
data class CellularSnapshot(
    val slotIndex: Int,
    val timestampMs: Long,
    val sim: SimInfo?,
    val servingCell: CellData?,
    val neighbors: List<CellData>,
    val signalDbm: FieldValue<Int>,
    val qualityGrade: FieldValue<SignalGrade>, // 估算值
    val neighborNote: String? = null,          // 「当前机型/系统不提供邻区信息」
)

// ----------------------------------------------------------------- WiFi ----

data class WifiConnection(
    val ssid: FieldValue<String>,
    val bssid: FieldValue<String>,
    val rssiDbm: FieldValue<Int>,
    val frequencyMhz: FieldValue<Int>,
    val channel: FieldValue<Int>,          // 换算
    val linkSpeedMbps: FieldValue<Int>,
    val security: FieldValue<WifiSecurity>,
    val ipV4: FieldValue<String>,
    val ipV6: FieldValue<String>,
    val gateway: FieldValue<String>,
    val dns: List<String>,
    val subnetMask: FieldValue<String>,    // 换算
    val broadcast: FieldValue<String>,     // 换算
    val localMac: FieldValue<String>,      // 常 N/A（随机化）
)

data class WifiAccessPoint(
    val bssid: String,
    val ssid: FieldValue<String>,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: FieldValue<Int>,
    val bandLabel: FieldValue<String>,
    val security: FieldValue<WifiSecurity>,
)

data class WifiSnapshot(
    val timestampMs: Long,
    val connection: WifiConnection?,
    val accessPoints: List<WifiAccessPoint>,
    val scanNote: String? = null,           // 扫描节流 / 权限降级提示
)

// ------------------------------------------------------------ 位置/GNSS ----

data class GnssFix(
    val timestampMs: Long,
    val coordinateType: String = "WGS-84",
    val latitude: FieldValue<Double>,
    val longitude: FieldValue<Double>,
    val provider: LocationProvider,
    val accuracyMeters: FieldValue<Float>,
    val altitudeMeters: FieldValue<Double>,
    val bearingDegrees: FieldValue<Float>,
    val speedMps: FieldValue<Float>,
    val address: FieldValue<String>,        // 逆地理编码，常 N/A
)

data class Satellite(
    val svid: Int,
    val constellation: GnssConstellation,
    val cn0DbHz: FieldValue<Float>,
    val azimuthDegrees: FieldValue<Float>,
    val elevationDegrees: FieldValue<Float>,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val usedInFix: Boolean,
    /**
     * 基带 C/N0（`GnssStatus.getBasebandCn0DbHz`，API 30+；`hasBasebandCn0DbHz` 为 false 时 N/A）。
     * 研究报告 §4.4：Cellular-Pro 读了这个分量，我们此前没有。
     */
    val basebandCn0DbHz: FieldValue<Float> =
        FieldValue.unavailable("getBasebandCn0DbHz 需 API 30+，或本机未上报基带 C/N0"),
)

data class GnssSnapshot(
    val timestampMs: Long,
    val fix: GnssFix?,
    val satellites: List<Satellite>,
    val nmeaEnabled: Boolean,
    val nmeaNote: String? = null,
    /** GNSS 原始观测与导航电文（§11-A0）。设备不输出时保持 null，由 UI 显示降级说明。 */
    val raw: GnssRawObservation? = null,
)

// --------------------------------------------------------------- 测速 ----

data class SpeedTestEndpoint(
    val id: String,
    val label: String,
    val baseUrl: String,
    val isSelfHosted: Boolean,
)

data class SpeedTestResult(
    val timestampMs: Long,
    val endpoint: SpeedTestEndpoint,
    val pingMs: FieldValue<Double>,
    val jitterMs: FieldValue<Double>,       // 换算
    val downMbps: FieldValue<Double>,
    val upMbps: FieldValue<Double>,
    val publicIp: FieldValue<String>,       // 第三方查询默认关闭
    val carrier: FieldValue<String>,
    val region: FieldValue<String>,
)

data class SpeedSample(val timestampMs: Long, val downMbps: Double, val upMbps: Double)

// --------------------------------------------------------------- 路测 ----

enum class DriveTestMode { MANUAL, PRESET_PATH, SENSOR_AUTO, OUTDOOR_GPS }

data class DriveTestPoint(
    val id: String,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val slotIndex: Int?,
    val rsrp: FieldValue<Int>,
    val rsrq: FieldValue<Int>,
    val sinr: FieldValue<Int>,
    val band: FieldValue<String>,
    val earfcn: FieldValue<Int>,
    val wifiRssi: FieldValue<Int>,
    val mode: DriveTestMode,
    val note: String?,                      // 传感器位移等估算值声明
)

// --------------------------------------------------------------- 设备 ----

data class BatteryInfo(
    val percent: FieldValue<Int>,
    val status: FieldValue<String>,
    val health: FieldValue<String>,
    val temperatureCelsius: FieldValue<Float>,
    val voltageMv: FieldValue<Int>,
    val plugged: FieldValue<String>,
    val technology: FieldValue<String>,
)

data class ScreenInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val xdpi: FieldValue<Float>,
    val ydpi: FieldValue<Float>,
    val sizeInches: FieldValue<Double>,     // 换算
    val refreshRateHz: FieldValue<Float>,
)

data class MemoryInfo(
    val ramTotalBytes: FieldValue<Long>,
    val ramAvailBytes: FieldValue<Long>,
    val romTotalBytes: FieldValue<Long>,
    val romAvailBytes: FieldValue<Long>,
)

data class HardwareInfo(
    val model: FieldValue<String>,
    val manufacturer: FieldValue<String>,
    val brand: FieldValue<String>,
    val board: FieldValue<String>,
    val socModel: FieldValue<String>,       // API 31+；低版本 N/A
    val abis: List<String>,
    val coreCount: Int,
    val gpuRenderer: FieldValue<String>,   // 常 N/A
)

data class SystemInfo(
    val release: FieldValue<String>,
    val sdkInt: Int,
    val securityPatch: FieldValue<String>,
    val fingerprint: FieldValue<String>,
    val kernel: FieldValue<String>,
    val serial: FieldValue<String>,         // 默认隐藏且脱敏
)

data class SensorInfo(
    val name: String,
    val vendor: String,
    val type: Int,
    val version: Int,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val minDelayUs: Int,
)

data class DeviceSnapshot(
    val timestampMs: Long,
    val battery: BatteryInfo,
    val screen: ScreenInfo,
    val memory: MemoryInfo,
    val hardware: HardwareInfo,
    val system: SystemInfo,
    val sensors: List<SensorInfo>,
)

// --------------------------------------------------------------- 蓝牙 ----

data class BluetoothDeviceInfo(
    val name: FieldValue<String>,
    val mac: FieldValue<String>,
    val rssiDbm: FieldValue<Int>,
    val type: FieldValue<String>,
)

data class BluetoothSnapshot(val timestampMs: Long, val devices: List<BluetoothDeviceInfo>, val note: String? = null)

// --------------------------------------------------------------- 流量 ----

/**
 * 本应用网络开销（研究报告 §11-B9：TrafficStats 本 UID）。
 *
 * 只读**本应用**的收发字节（`getUidTxBytes/getUidRxBytes(myUid)`），
 * 不读取其它 App 的流量（那需要 PACKAGE_USAGE_STATS 特殊授权，本工程不做）。
 * 用于自测「采样/测速本身花了多少流量」，不是逐应用流量统计。
 */
data class TrafficSnapshot(
    val timestampMs: Long,
    val txBytes: FieldValue<Long>,
    val rxBytes: FieldValue<Long>,
    val note: String? = "TrafficStats 本应用累计（自测开销用，非逐应用流量）",
)
