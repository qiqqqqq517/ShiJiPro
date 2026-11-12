package com.shiji.pro.core.common

import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.QualityWeights

/**
 * 路测 KPI 评分的单点输入（研究报告 §11-B6「信号权重可配置 → 评级/KPI」，2026-11-12 落地）。
 *
 * 只取三个可归一化的信号分量：RSRP / RSRQ / SINR，且**直接复用 [FieldValue] 三态**：
 * 取不到的分量（[com.shiji.pro.core.model.Origin.UNAVAILABLE]，其 value 恒为 null）
 * 不参与评分，按 [DriveKpi.score] 的规则对剩余有效分量重归一权重，
 * 绝不用 0 / -1 之类的占位值充数。
 *
 * 由 `DriveTestPoint` 构造：`KpiPoint(p.rsrp, p.rsrq, p.sinr)`。
 */
data class KpiPoint(
    val rsrp: FieldValue<Int>,
    val rsrq: FieldValue<Int>,
    val sinr: FieldValue<Int>,
)

/**
 * 路测记录点的加权 KPI 评分（纯 Kotlin，可在 JVM 单测直接跑，不 import android.*）。
 *
 * 归一化映射（区间端点闭区间，区间外一律 clamp，不外推）：
 * - RSRP：`-140..-80 dB → 0..1`（-140 → 0，-80 → 1，-200 也 → 0，-50 也 → 1）
 * - RSRQ：`-20..-10 dB → 0..1`
 * - SINR：`-10..30 dB → 0..1`
 *
 * 计分口径：
 * - **单点分** = `Σ(有效分量权重 × 该分量归一值) / Σ(有效分量权重)`，
 *   权重取 [QualityWeights.normalized]（先归一到和为 1，非法值回落 DEFAULT），
 *   再只按**该点实际有值的分量**重归一 —— 所以缺分量不会把分压低。
 *   若某点所有分量都无值，或有效权重和 ≤ 0（例如用户把有值分量的权重都调成 0），该点不计。
 * - **多点分** = 所有「可计分点」的算术均值（等权，不按采样时长加权）。
 * - 无任何可计分点 → 返回 `null`，由 UI 显示 N/A 并给出原因。
 *
 * ⚠️ 语义声明：这是**估算值**（线性归一 + 等权均值的简化模型，非运营商标准口径），
 * UI 侧必须以 [com.shiji.pro.core.model.Origin.ESTIMATED] 包装并附「估算值，仅供参考」声明；
 * 蜂窝页的「实时信号评级」仍走 `SignalQuality.gradeByRsrp` 固定阈值，两套口径不混用。
 */
object DriveKpi {

    /** RSRP 归一化下界（dB）：视为最差，映射到 0。 */
    private const val RSRP_MIN = -140.0

    /** RSRP 归一化上界（dB）：视为最好，映射到 1。 */
    private const val RSRP_MAX = -80.0

    /** RSRQ 归一化下界（dB）。 */
    private const val RSRQ_MIN = -20.0

    /** RSRQ 归一化上界（dB）。 */
    private const val RSRQ_MAX = -10.0

    /** SINR 归一化下界（dB）。 */
    private const val SINR_MIN = -10.0

    /** SINR 归一化上界（dB）。 */
    private const val SINR_MAX = 30.0

    /**
     * 加权 KPI 评分。
     *
     * @param points 路测记录点（可为空列表）。
     * @param weights 用户配置的三分量权重；内部先 [QualityWeights.normalized]，
     *                再按每个点的**有效分量**重归一。
     * @return `0.0..1.0` 的评分（乘 100 即为百分制）；无任何可计分点时返回 null（UI 显示 N/A + 原因）。
     */
    fun score(points: List<KpiPoint>, weights: QualityWeights): Double? {
        if (points.isEmpty()) return null
        val w = weights.normalized()
        var sum = 0.0
        var counted = 0
        points.forEach { p ->
            val s = pointScore(p, w)
            if (s != null) {
                sum += s
                counted++
            }
        }
        if (counted == 0) return null
        return (sum / counted).coerceIn(0.0, 1.0)
    }

    /** 单点分：只对有效分量归一权重；无有效分量或有效权重和 ≤ 0 → null（该点不计）。 */
    private fun pointScore(p: KpiPoint, w: QualityWeights): Double? {
        var acc = 0.0
        var wSum = 0.0

        valueOf(p.rsrp)?.let {
            acc += w.rsrpWeight * scale(it, RSRP_MIN, RSRP_MAX)
            wSum += w.rsrpWeight
        }
        valueOf(p.rsrq)?.let {
            acc += w.rsrqWeight * scale(it, RSRQ_MIN, RSRQ_MAX)
            wSum += w.rsrqWeight
        }
        valueOf(p.sinr)?.let {
            acc += w.sinrWeight * scale(it, SINR_MIN, SINR_MAX)
            wSum += w.sinrWeight
        }

        if (wSum <= 0.0) return null
        return (acc / wSum).coerceIn(0.0, 1.0)
    }

    /** 参与评分的分量：必须是有值且非 N/A（[FieldValue.isPlottable]）的读数。 */
    private fun valueOf(v: FieldValue<Int>): Int? = if (v.isPlottable()) v.value else null

    /** 线性归一并 clamp 到 `0..1`；[hi] == [lo] 时退化为 0（防御除零，正常不会发生）。 */
    private fun scale(v: Int, lo: Double, hi: Double): Double {
        if (hi == lo) return 0.0
        return ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0)
    }
}
