package com.shiji.pro.core.model

/**
 * 评级/评分权重（研究报告 §11-B6「信号权重可配置 → 评级/KPI」的落地契约，2026-11-12 冻结）。
 *
 * 用途：只作用于**路测记录点的 KPI 评分聚合**（覆盖测试页），
 * 不改动蜂窝页「实时信号评级」的固定阈值口径（那部分仍由
 * `core:common/SignalQuality.gradeByRsrp` 单指标映射，且标注为估算）。
 *
 * 语义：三个权重是相对比例，构造后由 [normalized] 归一化到和为 1；
 * 全 0 或负数视为非法（由 [normalized] 回落到默认权重，不抛异常炸 UI）。
 */
data class QualityWeights(
    val rsrpWeight: Double = 0.5,
    val rsrqWeight: Double = 0.3,
    val sinrWeight: Double = 0.2,
) {
    fun normalized(): QualityWeights {
        val sum = rsrpWeight + rsrqWeight + sinrWeight
        if (sum <= 0.0 || rsrpWeight < 0 || rsrqWeight < 0 || sinrWeight < 0) return DEFAULT
        return QualityWeights(rsrpWeight / sum, rsrqWeight / sum, sinrWeight / sum)
    }

    companion object {
        val DEFAULT = QualityWeights()
    }
}
