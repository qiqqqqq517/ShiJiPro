package com.shiji.pro.core.common

import com.shiji.pro.core.model.SignalGrade

/**
 * 信号质量评级（设计稿 §8：优秀/良好/一般/较差/极差）。
 *
 * ⚠️ 评级为**估算值**（由阈值映射得出，不是系统 API 返回的实测等级）。
 * 阈值集中在此文件，UI 读取后可查看；结果必须标注「评级为估算」。
 * 输入奇异值（如 Integer.MAX_VALUE / CellInfo.UNAVAILABLE）一律返回 UNKNOWN，
 * 由调用方降级为 N/A，不据此评级。
 */
object SignalQuality {

    /** 以 LTE RSRP(dBm) 为基准的常见工程阈值。 */
    private const val RSRP_EXCELLENT = -80
    private const val RSRP_GOOD = -90
    private const val RSRP_FAIR = -105
    private const val RSRP_POOR = -115

    /** CellInfo 系列用 MAX_VALUE 表示取不到。 */
    private const val UNAVAILABLE_SENTINEL = Int.MAX_VALUE

    fun gradeByRsrp(rsrpDbm: Int): SignalGrade {
        if (rsrpDbm == UNAVAILABLE_SENTINEL || rsrpDbm == Int.MIN_VALUE) return SignalGrade.UNKNOWN
        return when {
            rsrpDbm >= RSRP_EXCELLENT -> SignalGrade.EXCELLENT
            rsrpDbm >= RSRP_GOOD -> SignalGrade.GOOD
            rsrpDbm >= RSRP_FAIR -> SignalGrade.FAIR
            rsrpDbm >= RSRP_POOR -> SignalGrade.POOR
            else -> SignalGrade.BAD
        }
    }

    /** WiFi 2.4/5GHz RSSI(dBm) 评级阈值。 */
    fun gradeByWifiRssi(rssiDbm: Int): SignalGrade = when {
        rssiDbm >= -50 -> SignalGrade.EXCELLENT
        rssiDbm >= -60 -> SignalGrade.GOOD
        rssiDbm >= -70 -> SignalGrade.FAIR
        rssiDbm >= -80 -> SignalGrade.POOR
        else -> SignalGrade.BAD
    }
}
