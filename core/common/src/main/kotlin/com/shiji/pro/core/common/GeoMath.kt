package com.shiji.pro.core.common

import com.shiji.pro.core.model.UserEndpoint
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 大圆距离与就近选点（研究报告 §11-B4「测速节点库 + 就近选点」的机制落地，2026-11-12）。
 *
 * 纯 Kotlin 零依赖（不依赖任何 Android / java.net API），只做几何计算：
 * - 端点坐标与 GNSS 定位均为 WGS-84 十进制度；
 * - 距离用 Haversine 公式（球面平均半径）；
 * - 跨 ±180° 经线自然正确（Δλ 经 sin²(Δλ/2) 归一，不会出现绕远路）；
 * - 不内置任何第三方节点列表，只对调用方传入的端点集合做最近邻查找。
 */
object GeoMath {

    /** 球面平均地球半径（米，IUGG 平均半径 6371008.8 m）。 */
    const val EARTH_RADIUS_M = 6371008.8

    /**
     * Haversine 大圆距离（米）。输入均为十进制度（WGS-84）。
     * 同点（含浮点一致输入）返回 0；对角交换满足对称性。
     */
    fun haversineMeters(
        lat1Deg: Double,
        lon1Deg: Double,
        lat2Deg: Double,
        lon2Deg: Double,
    ): Double {
        val lat1 = toRadians(lat1Deg)
        val lat2 = toRadians(lat2Deg)
        val dLat = toRadians(lat2Deg - lat1Deg)
        val dLon = toRadians(lon2Deg - lon1Deg)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        // 浮点误差可能让 a 轻微越界 [0,1]，coerce 后再 asin 防 NaN。
        val c = 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_M * c
    }

    /**
     * 就近选点：只对 [UserEndpoint.hasLocation] 的端点计算距离。
     * 空列表或全无位置 → null；否则返回（最近端点, 距离米）。
     * 距离并列时保留先录入者（严格小于才替换），结果确定不抖动。
     */
    fun nearest(
        fromLatDeg: Double,
        fromLonDeg: Double,
        endpoints: List<UserEndpoint>,
    ): Pair<UserEndpoint, Double>? {
        var best: Pair<UserEndpoint, Double>? = null
        for (ep in endpoints) {
            val lat = ep.latDeg ?: continue
            val lon = ep.lonDeg ?: continue
            val d = haversineMeters(fromLatDeg, fromLonDeg, lat, lon)
            val cur = best
            if (cur == null || d < cur.second) best = ep to d
        }
        return best
    }

    /** 度 → 弧度（纯 Kotlin，避免依赖 java.lang.Math）。 */
    private fun toRadians(deg: Double): Double = deg * PI / 180.0
}
