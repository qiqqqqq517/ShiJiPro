package com.shiji.pro.core.model

/**
 * 运营商归属表（研究报告 §5.2 / §11-A1 的落地契约，2026-11-12 冻结）。
 *
 * 机制（借鉴同类工具 `operator_earfcn.xml`，**数据自建、不复制其资产**）：
 * 同一 band 内不同运营商占用不同子频段，因此「band + EARFCN/NR-ARFCN 区间」才能答
 * 「这个小区是哪家运营商」；只有 band 号无法判定归属。
 *
 * 数据纪律：
 * - 每条区间必须能给出公开频谱分配来源（工信部/运营商公开资料），并在
 *   `docs/P13_operator_earfcn_reference.md` 里留双源核验记录；
 * - 未收录的 EARFCN/band 一律返回 null，调用方降级为
 *   [FieldValue.unavailable]("该 EARFCN 不在已收录的运营商子频段内（不猜）")；
 * - 命中后 UI 标注为**换算值**（`Origin.DERIVED`），不是实测。
 *
 * ⚠️ 本文件的 LTE_RANGES / NR_RANGES 由「A1 运营商归属表」责任人填充并配单测；
 * 冻结时为空表，空表时所有查询返回 null（功能降级为 N/A，不伪造归属）。
 */
data class OperatorRange(
    /** 制式：目前只收 LTE 与 NR。 */
    val tech: RadioTech,
    val band: Int,
    /** 该运营商在该 band 内占用的 EARFCN/NR-ARFCN 下界（含）。 */
    val earfcnLow: Int,
    /** 上界（含）。 */
    val earfcnHigh: Int,
    /** 运营商名（如「中国联通」「中国移动」「中国电信」「中国广电」）。 */
    val operator: String,
    /** 来源说明（一句话，指向 docs 参考文档的条目）。 */
    val sourceNote: String = "",
) {
    fun contains(earfcn: Int): Boolean = earfcn in earfcnLow..earfcnHigh
}

/** 命中结果：除运营商名外，带回 band 与命中区间，便于 UI 展示判定依据。 */
data class OperatorMatch(
    val operator: String,
    val band: Int,
    val range: OperatorRange,
)

object OperatorTable {

    /**
     * LTE 运营商子频段（DL EARFCN，按 3GPP TS 36.101 的 F_DL = F_DL_low + 0.1·(N_DL − N_offs) 换算）。
     *
     * 数据来源（2026-11-12 双源核对，≥3 个独立聚合资料一致后才收录，逐条推演见
     * docs/P13_operator_earfcn_reference.md）：
     * - pswp.cn《四大运营商频段-2024》（http://www.pswp.cn/web/21819.shtml）
     * - 知乎《中国四大运营商2G/3G/4G/5G工作频率以及网络制式》（zhuanlan.zhihu.com/p/543216374）
     * - 腾讯云社区转载·网优雇佣军《四大运营商频段使用情况》（cloud.tencent.com.cn/developer/article/1585391）
     *
     * 口径说明：
     * - 电信 B5 按 10MHz（870–880）保守收录：各资料对 15MHz 口径说法不一；
     * - 移动 B41 只收 2575–2635（其公认主用段）：2515–2575 / 2635–2675 与联通电信的再划分
     *   各来源不一致 → 不收录（宁缺勿错，未收录显示 N/A）；
     * - NR（NR-ARFCN）本轮未收录：38.104 栅格与工信部分配文件的边界核对未完成，
     *   `forNrArfcn` 恒返回 null，UI 显示 N/A + 原因，不猜归属。
     */
    val LTE_RANGES: List<OperatorRange> = listOf(
        OperatorRange(RadioTech.LTE, 1, 0, 199, "中国电信", "B1 2110–2130MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 1, 200, 449, "中国联通", "B1 2130–2155MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 3, 1200, 1449, "中国移动", "B3 1805–1830MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 3, 1450, 1749, "中国联通", "B3 1830–1860MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 3, 1750, 1949, "中国电信", "B3 1860–1880MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 5, 2410, 2509, "中国电信", "B5 870–880MHz（10MHz 保守口径）；来源[2][3]"),
        OperatorRange(RadioTech.LTE, 8, 3540, 3689, "中国移动", "B8 934–949MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 8, 3690, 3799, "中国联通", "B8 949–960MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 34, 36200, 36349, "中国移动", "B34 2010–2025MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 39, 38250, 38449, "中国移动", "B39 1880–1900MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 40, 38650, 38849, "中国联通", "B40 2300–2320MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 40, 38850, 39349, "中国移动", "B40 2320–2370MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 40, 39350, 39649, "中国电信", "B40 2370–2400MHz；来源[1][2][3]"),
        OperatorRange(RadioTech.LTE, 41, 40440, 41039, "中国移动", "B41 2575–2635MHz（公认主用段）；来源[1][2][3]"),
    )

    /** NR 运营商子频段：**本轮未收录**（见 LTE_RANGES 的 KDoc 口径说明），查询恒 null → N/A。 */
    val NR_RANGES: List<OperatorRange> = emptyList()

    /** LTE EARFCN → 运营商；未收录返回 null（调用方 N/A，不做最近邻猜测）。 */
    fun forLteEarfcn(earfcn: Int): OperatorMatch? =
        LTE_RANGES.firstOrNull { it.contains(earfcn) }
            ?.let { OperatorMatch(it.operator, it.band, it) }

    /** NR-ARFCN → 运营商；未收录返回 null。 */
    fun forNrArfcn(nrarfcn: Int): OperatorMatch? =
        NR_RANGES.firstOrNull { it.contains(nrarfcn) }
            ?.let { OperatorMatch(it.operator, it.band, it) }

    /** 表是否为空（UI 据此说明「归属表未收录」而不是假装「未知运营商」）。 */
    fun isEmpty(): Boolean = LTE_RANGES.isEmpty() && NR_RANGES.isEmpty()
}
