package com.shiji.pro.core.model

/**
 * 领域枚举集合（纯 Kotlin，零框架依赖）。与设计稿 §4 字段对照表一一对应。
 */

/** 蜂窝制式（服务小区 / 邻区按此分派 Parser）。 */
enum class RadioTech(val label: String) {
    GSM("GSM"),
    WCDMA("WCDMA"),
    TDSCDMA("TD-SCDMA"),
    CDMA("CDMA/EVDO"),
    LTE("LTE"),
    NR("5G NR"),
    UNKNOWN("未知"),
}

/** 双工模式。 */
enum class DuplexMode { FDD, TDD, UNKNOWN }

/** GNSS 星座（对应 GnssStatus.getConstellationType 常量，API 24+）。 */
enum class GnssConstellation(val typeId: Int, val label: String) {
    UNKNOWN(0, "未知"),
    GPS(1, "GPS"),
    SBAS(2, "SBAS"),
    GLONASS(3, "GLONASS"),
    QZSS(4, "QZSS"),
    BEIDOU(5, "北斗"),
    GALILEO(6, "Galileo"),
    IRNSS(7, "IRNSS/NavIC"),
    ;

    companion object {
        fun fromTypeId(id: Int): GnssConstellation =
            entries.firstOrNull { it.typeId == id } ?: UNKNOWN
    }
}

/** 定位来源（设计稿 §4.6：network 源无法区分 CELL 与 WIFI，故合并口径，不臆断）。 */
enum class LocationProvider(val label: String) {
    GPS("GPS"),
    NETWORK("NETWORK（CELL/WIFI 不可区分）"),
    FUSED("FUSED"),
    CACHE("CACHE（lastKnownLocation）"),
    OFFLINE("OFFLINE"),
    UNKNOWN("UNKNOWN"),
}

/** 信号质量评级（估算值，阈值在 core:common 的 SignalQuality 集中定义）。 */
enum class SignalGrade(val label: String) {
    EXCELLENT("优秀"),
    GOOD("良好"),
    FAIR("一般"),
    POOR("较差"),
    BAD("极差"),
    UNKNOWN("未知"),
}

/** 统一日志类型（设计稿 §8 日志中心）。 */
enum class LogType { CELLULAR, WIFI, GNSS, NMEA, SPEED_TEST, DRIVE_TEST, DEVICE, SYSTEM }

/** WiFi 安全类型（设计稿 §4.5）。 */
enum class WifiSecurity(val label: String) {
    OPEN("开放"),
    WEP("WEP"),
    WPA("WPA"),
    WPA2("WPA2"),
    WPA3("WPA3/SAE"),
    OWE("OWE"),
    EAP("WPA/WPA2-EAP"),
    WAPI("WAPI"),
    UNKNOWN("未知"),
}
