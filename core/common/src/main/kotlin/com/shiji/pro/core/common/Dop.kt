package com.shiji.pro.core.common

import com.shiji.pro.core.model.DopInfo
import com.shiji.pro.core.model.FieldValue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DOP（Dilution of Precision）几何解算（研究报告 §11-A0：把「知道几颗星」升级为「能判定位质量」）。
 *
 * 口径（必须如实标注）：
 * - 输入是**参与定位卫星**的方位角/仰角（`GnssStatus.getAzimuthDegrees/getElevationDegrees`）。
 * - 用最小二乘设计矩阵 G（行 = [ux, uy, uz, 1]）求 Q = (GᵀG)⁻¹，
 *   HDOP=√(Q00+Q11)、VDOP=√(Q22)、PDOP=√(Q00+Q11+Q22)。
 * - 结果属**估算值**（只由几何构型决定，不含量测误差模型），UI 必须显示「估算值，仅供参考」。
 * - 少于 4 颗参与定位 → 返回 null（几何不可解，不猜）；矩阵奇异/数值退化 → 返回 null。
 *
 * 这是纯 Kotlin 实现（无第三方依赖），可 JVM 单测（DopTest）。
 */
object DopCalculator {

    private const val NOTE = "由参与定位卫星的仰角/方位角几何解算（DOP），估算值，仅供参考"

    /** 求逆的数值容差：主元绝对值小于该值视为奇异（几何退化），返回 null。 */
    private const val PIVOT_EPS = 1e-12

    /**
     * @param azimuthDeg 参与定位卫星的方位角（度，0=北，顺时针）
     * @param elevationDeg 同序的仰角（度，0=地平，90=天顶）
     * @return DOP 三元组（估算值）；不足 4 颗或几何退化时 null
     */
    fun compute(azimuthDeg: List<Double>, elevationDeg: List<Double>): DopInfo? {
        if (azimuthDeg.size != elevationDeg.size || azimuthDeg.size < 4) return null

        // G 的行：卫星到接收机的单位视线向量在本地 ENU 系的 x(东)/y(北)/z(天) 分量 + 钟差列
        val rows = Array(azimuthDeg.size) { i ->
            val az = Math.toRadians(azimuthDeg[i])
            val el = Math.toRadians(elevationDeg[i])
            doubleArrayOf(cos(el) * sin(az), cos(el) * cos(az), sin(el), 1.0)
        }

        // N = GᵀG
        val n = Array(4) { DoubleArray(4) }
        for (row in rows) {
            for (r in 0 until 4) {
                for (c in 0 until 4) n[r][c] += row[r] * row[c]
            }
        }
        val q = invert4(n) ?: return null

        val h = q[0][0] + q[1][1]
        val v = q[2][2]
        val p = h + v
        // 数值退化（负值/零值）不产出伪数值
        if (!(h > 0.0) || !(v > 0.0) || !(p > 0.0)) return null
        if (h.isNaN() || v.isNaN() || p.isNaN()) return null

        return DopInfo(
            hdop = FieldValue.estimated(sqrt(h), NOTE),
            vdop = FieldValue.estimated(sqrt(v), NOTE),
            pdop = FieldValue.estimated(sqrt(p), NOTE),
            usedSatellites = azimuthDeg.size,
            note = NOTE,
        )
    }

    /** 4x4 Gauss-Jordan 求逆；奇异或主元过小返回 null。 */
    private fun invert4(src: Array<DoubleArray>): Array<DoubleArray>? {
        val a = Array(4) { r -> DoubleArray(8) { c -> if (c < 4) src[r][c] else if (c - 4 == r) 1.0 else 0.0 } }
        for (col in 0 until 4) {
            // 选主元（当前列绝对值最大行）
            var pivotRow = col
            var best = abs(a[col][col])
            for (r in col + 1 until 4) {
                val v = abs(a[r][col])
                if (v > best) { best = v; pivotRow = r }
            }
            if (best < PIVOT_EPS) return null
            if (pivotRow != col) {
                val tmp = a[col]; a[col] = a[pivotRow]; a[pivotRow] = tmp
            }
            val pivot = a[col][col]
            for (c in 0 until 8) a[col][c] /= pivot
            for (r in 0 until 4) {
                if (r == col) continue
                val factor = a[r][col]
                if (factor == 0.0) continue
                for (c in 0 until 8) a[r][c] -= factor * a[col][c]
            }
        }
        return Array(4) { r -> DoubleArray(4) { c -> a[r][c + 4] } }
    }
}
