package com.shiji.pro.core.model

/**
 * 采集调度 / 单一数据源 / 日志 的核心契约模型（纯 Kotlin）。设计稿 §2.3 / §8 / §10.6。
 */

/** 8 个采集器的稳定标识。 */
enum class CollectorKey {
    CELLULAR, WIFI, GNSS, DEVICE, BATTERY, BLUETOOTH, TRAFFIC, SPEED_TEST
}

/** 能力降级状态：权限 / API 版本 / 机型能力三选一的唯一出口。 */
data class CapabilityState(
    val key: CollectorKey,
    val available: Boolean,
    val reason: String? = null,             // 不可用时必须给出原因
    val requiredPermission: String? = null,
    val minApi: Int? = null,
)

/** 可配置的刷新间隔（毫秒）与采集开关。 */
data class SamplingConfig(
    val cellularIntervalMs: Long = 2_000L,
    val wifiScanIntervalMs: Long = 15_000L,
    val gnssIntervalMs: Long = 1_000L,
    val deviceIntervalMs: Long = 30_000L,
    val paused: Boolean = false,            // 后台采集可整体暂停
    val logRetentionDays: Int = 7,
    val chartDownsampleMaxPoints: Int = 200,
)

/** 主题与语言设置。 */
enum class ThemeMode { LIGHT, DARK, FOLLOW_SYSTEM }
enum class AppLanguage { ZH, EN }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val language: AppLanguage = AppLanguage.ZH,
    val maskSensitive: Boolean = true,
    val thirdPartyIpLookup: Boolean = false,  // 默认关闭
    val overlayEnabled: Boolean = false,      // 需 SYSTEM_ALERT_WINDOW
    /**
     * 路测 CSV 导出列（`null` = 全部列）。列名常量见 `core:common/CsvColumns`。
     * 研究报告 §11-A3「CSV 字段可选」的配置载体（2026-11-12 冻结）。
     */
    val driveTestCsvColumns: List<String>? = null,
    /**
     * 路测 KPI 评分权重（只作用于覆盖测试页的聚合评分；
     * 蜂窝页实时评级仍走 `SignalQuality.gradeByRsrp` 固定阈值，口径分开标注）。
     * 研究报告 §11-B6 的配置载体（2026-11-12 冻结）。
     */
    val qualityWeights: QualityWeights = QualityWeights.DEFAULT,
    /** 自动采集总开关（P14：由采样调度器消费；关闭后只保留手动「刷新」）。 */
    val autoSamplingEnabled: Boolean = true,
)

/** 统一日志条目（§8 日志中心）。payload 为脱敏后的紧凑文本/JSON。 */
data class LogEntry(
    val id: String,
    val timestampMs: Long,
    val type: LogType,
    val slot: Int? = null,
    val maskedPayload: String,              // 默认脱敏，明文需显式开关
    val note: String? = null,
)

/**
 * AppState —— 单一数据源（§2.3）。由 AppStateRepositoryImpl 合并 8 路 Flow 而成，
 * ViewModel 只订阅本对象，不直接触碰任何 Android 采集 API。
 * 所有字段都是不可变快照；未采集到的模块为 null，UI 用 EmptyState 降级。
 */
data class AppState(
    val timestampMs: Long,
    val capabilities: Map<CollectorKey, CapabilityState> = emptyMap(),
    val cellularSlots: Map<Int, CellularSnapshot> = emptyMap(),
    val wifi: WifiSnapshot? = null,
    val gnss: GnssSnapshot? = null,
    val device: DeviceSnapshot? = null,
    val bluetooth: BluetoothSnapshot? = null,
    val lastSpeedTest: SpeedTestResult? = null,
    val driveTestPoints: List<DriveTestPoint> = emptyList(),
    val samplingPaused: Boolean = false,
    /** 当前设置（设置页 / CSV 列 / KPI 权重读取入口；由仓储 Flow 订阅写入）。 */
    val settings: AppSettings = AppSettings(),
    /** 测速端点配置（研究报告 §11-B4；为空时测速页显示「端点未配置」并给录入入口）。 */
    val speedConfig: SpeedTestConfig? = null,
    /** 生效中的采样配置（P14：真实被调度器消费，设置页据此显示间隔）。 */
    val samplingConfig: SamplingConfig = SamplingConfig(),
    /** 各卡槽的 RSRP 趋势（环形缓冲快照，P14：趋势图据此绘制真实曲线）。 */
    val cellularTrend: Map<Int, List<TrendPoint>> = emptyMap(),
    /** 本应用网络开销（TrafficStats 本 UID，报告 §11-B9）。 */
    val traffic: TrafficSnapshot? = null,
    /** 常驻前台服务是否在运行（P14；通知栏可见）。 */
    val foregroundSampling: Boolean = false,
) {
    companion object {
        fun empty(nowMs: Long) = AppState(timestampMs = nowMs)
    }
}
