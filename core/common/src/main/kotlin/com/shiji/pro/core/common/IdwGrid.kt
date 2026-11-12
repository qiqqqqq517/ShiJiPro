package com.shiji.pro.core.common

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * IDW（反距离加权）覆盖栅格（研究报告 §11-B7：覆盖栅格 / 室内插值）。
 *
 * 为什么不用 RenderScript：官方自 Android 12 起废弃（研究报告 §5.3 同结论）；
 * 这里用纯 Kotlin 实现，代价是性能，但零依赖、可单测、跨 API 版本稳定。
 *
 * 语义（必须如实标注）：
 * - 插值是**估算值**（[com.shiji.pro.core.model.FieldValue.estimated] 由调用方包装）；
 * - 无参考点 → null（不画伪造热力图）；
 * - 距离退化（点与点重合）时按 1e-9 米下限保护，避免除零；
 * - 幂参数 p 默认 2（标准 IDW），越大越"局部"。
 */
object IdwGrid {

    /** 栅格一个单元：归一化坐标 (0..1, 0..1) 与插值结果。 */
    data class Cell(val u: Double, val v: Double, val value: Double)

    /** 参考点：经纬度 + 观测值（如 RSRP）。 */
    data class Sample(val latDeg: Double, val lonDeg: Double, val value: Double)

    /**
     * 以参考点的经纬度包围盒为画布范围，插值出 [cols]×[rows] 的栅格。
     *
     * @param samples 参考点（至少 1 个；1 个点时退化为常量面）
     * @param p 幂参数（默认 2）
     * @return 栅格单元（行优先，v=0 为北、v=1 为南；u=0 为西）；无点时返回空列表
     */
    fun interpolate(samples: List<Sample>, cols: Int = 24, rows: Int = 24, p: Double = 2.0): List<Cell> {
        require(cols >= 2 && rows >= 2) { "栅格至少 2×2" }
        if (samples.isEmpty()) return emptyList()
        val latMin = samples.minOf { it.latDeg }
        val latMax = samples.maxOf { it.latDeg }
        val lonMin = samples.minOf { it.lonDeg }
        val lonMax = samples.maxOf { it.lonDeg }
        val latSpan = (latMax - latMin).takeIf { it > 1e-9 } ?: 1e-6
        val lonSpan = (lonMax - lonMin).takeIf { it > 1e-9 } ?: 1e-6

        val out = ArrayList<Cell>(cols * rows)
        for (r in 0 until rows) {
            val v = r / (rows - 1).toDouble()
            val lat = latMax - v * latSpan // v=0 北
            for (c in 0 until cols) {
                val u = c / (cols - 1).toDouble()
                val lon = lonMin + u * lonSpan
                out += Cell(u, v, valueAt(samples, lat, lon, p))
            }
        }
        return out
    }

    /** 单点 IDW：Σ(wi·vi)/Σwi，wi = 1/d^p；距离用等距圆柱近似（小范围足够，且不引入第三方地理库）。 */
    fun valueAt(samples: List<Sample>, latDeg: Double, lonDeg: Double, p: Double = 2.0): Double {
        var num = 0.0
        var den = 0.0
        var exact: Double? = null
        for (s in samples) {
            val dx = (lonDeg - s.lonDeg) * 111_320.0 * kotlin.math.cos(Math.toRadians(latDeg))
            val dy = (latDeg - s.latDeg) * 110_540.0
            val d = sqrt(dx * dx + dy * dy)
            if (d < 1e-6) { exact = s.value; break } // 与参考点重合 → 直接用该点值
            val w = 1.0 / Math.pow(d, p)
            num += w * s.value
            den += w
        }
        exact?.let { return it }
        return if (den <= 0.0) 0.0 else num / den
    }

    /** 参考点包围盒（用于 Canvas 坐标映射与「覆盖范围」提示）；无点返回 null。 */
    fun bounds(samples: List<Sample>): Bounds? {
        if (samples.isEmpty()) return null
        return Bounds(
            latMin = samples.minOf { it.latDeg },
            latMax = samples.maxOf { it.latDeg },
            lonMin = samples.minOf { it.lonDeg },
            lonMax = samples.maxOf { it.lonDeg },
        )
    }

    data class Bounds(val latMin: Double, val latMax: Double, val lonMin: Double, val lonMax: Double) {
        val latSpan: Double get() = abs(latMax - latMin)
        val lonSpan: Double get() = abs(lonMax - lonMin)
    }
}
