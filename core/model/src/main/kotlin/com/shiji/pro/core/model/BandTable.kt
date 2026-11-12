package com.shiji.pro.core.model

/**
 * 3GPP 频段常量表（设计稿 §4.2：EARFCN / NR-ARFCN → Band + 频率 = 换算值）。
 *
 * ⚠️ 数据来源：3GPP TS 36.101 Table 5.7.3-1（E-UTRA operating bands）、
 *    TS 38.101-1 / 38.104 的 NR-ARFCN 全局栅格规则。
 *    独立核验结果与来源 URL 见 docs/P1_3GPP_band_reference.md（并行核验产出）。
 *
 * 设计约束：
 * - 本表只用于「换算」字段，结果一律包成 FieldValue.derived(...)；
 *   任何查不到的输入返回 null，由调用方降级为 FieldValue.unavailable("频段表未覆盖该 EARFCN")，
 *   **严禁**按最近频段猜测一个 Band 号。
 * - 表中只保留「下界频率 + EARFCN 区间」这一最小充分集，避免把上行/双工常量写错。
 */

/** LTE 频段条目（下行 EARFCN 区间与下行起始频率）。 */
data class LteBand(
    val band: Int,
    val duplex: DuplexMode,
    val earfcnLow: Int,
    val earfcnHigh: Int,
    val dlFreqLowMhz: Double,
    /** 常见名称，如 "1800"。用于 UI 附注，不参与计算。 */
    val commonName: String = "",
) {
    fun contains(earfcn: Int): Boolean = earfcn in earfcnLow..earfcnHigh
}

/** NR 频段条目（FR1 频率范围，单位 MHz）。 */
data class NrBand(
    val band: Int,
    val duplex: DuplexMode,
    val freqLowMhz: Double,
    val freqHighMhz: Double,
    val commonName: String = "",
) {
    fun contains(freqMhz: Double): Boolean = freqMhz in freqLowMhz..freqHighMhz
}

object BandTable {

    /** E-UTRA operating bands（3GPP TS 36.101）。 */
    val LTE_BANDS: List<LteBand> = listOf(
        LteBand(1, DuplexMode.FDD, 0, 599, 2110.0, "2100"),
        LteBand(2, DuplexMode.FDD, 600, 1199, 1930.0, "1900"),
        LteBand(3, DuplexMode.FDD, 1200, 1949, 1805.0, "1800"),
        LteBand(4, DuplexMode.FDD, 1950, 2399, 2110.0, "1700/2100"),
        LteBand(5, DuplexMode.FDD, 2400, 2649, 869.0, "850"),
        LteBand(7, DuplexMode.FDD, 2750, 3449, 2620.0, "2600"),
        LteBand(8, DuplexMode.FDD, 3450, 3799, 925.0, "900"),
        LteBand(12, DuplexMode.FDD, 5010, 5179, 729.0, "700"),
        LteBand(13, DuplexMode.FDD, 5180, 5279, 746.0, "700"),
        LteBand(17, DuplexMode.FDD, 5730, 5849, 734.0, "700"),
        LteBand(20, DuplexMode.FDD, 6150, 6449, 791.0, "800"),
        LteBand(28, DuplexMode.FDD, 9210, 9659, 758.0, "700"),
        LteBand(38, DuplexMode.TDD, 37750, 38249, 2570.0, "2600 TDD"),
        LteBand(39, DuplexMode.TDD, 38250, 38649, 1880.0, "1900 TDD"),
        LteBand(40, DuplexMode.TDD, 38650, 39649, 2300.0, "2300 TDD"),
        LteBand(41, DuplexMode.TDD, 39650, 41589, 2496.0, "2500 TDD"),
    )

    /**
     * NR FR1 频段**下行(DL) / TDD** 频率范围（3GPP TS 38.101-1 §5.2 / TS 38.104 Table 5.2-1）。
     *
     * 口径说明（非规范结论，属本工程的取数假设）：`CellIdentityNr.getNrarfcn()` 在 Android
     * 公开文档未明示是 DL 还是 UL ARFCN；本表按「服务小区下行载波」口径存 DL 范围。
     * 若某机型上报 UL ARFCN，FDD 频段（如 n1 UL 1920–1980）将不落在本表 DL 范围而返回 N/A，
     * 属**降级而非伪造**，由 P3 采集层依据实测方向再做二次判定。
     *
     * FDD 例：n1 = 2110–2170（UL 1920–1980）；n20 为反向双工（DL 791–821 / UL 832–862）。
     * 独立核验与来源分级见 docs/P1_3GPP_band_reference.md。
     */
    val NR_BANDS: List<NrBand> = listOf(
        NrBand(1, DuplexMode.FDD, 2110.0, 2170.0, "2100"),   // DL; UL 1920–1980
        NrBand(3, DuplexMode.FDD, 1805.0, 1880.0, "1800"),   // DL; UL 1710–1785
        NrBand(5, DuplexMode.FDD, 869.0, 894.0, "850"),      // DL; UL 824–849
        NrBand(7, DuplexMode.FDD, 2620.0, 2690.0, "2600"),   // DL; UL 2500–2570
        NrBand(8, DuplexMode.FDD, 925.0, 960.0, "900"),      // DL; UL 880–915
        NrBand(20, DuplexMode.FDD, 791.0, 821.0, "800"),     // DL; UL 832–862（反向双工）
        NrBand(28, DuplexMode.FDD, 758.0, 803.0, "700"),     // DL; UL 703–748
        NrBand(38, DuplexMode.TDD, 2570.0, 2620.0, "2600 TDD"),
        NrBand(40, DuplexMode.TDD, 2300.0, 2400.0, "2300 TDD"),
        NrBand(41, DuplexMode.TDD, 2496.0, 2690.0, "2600 TDD"),
        NrBand(77, DuplexMode.TDD, 3300.0, 4200.0, "3700"),
        NrBand(78, DuplexMode.TDD, 3300.0, 3800.0, "3500"),
        NrBand(79, DuplexMode.TDD, 4400.0, 5000.0, "4500"),
    )

    fun lteBandForEarfcn(earfcn: Int): LteBand? =
        LTE_BANDS.firstOrNull { it.contains(earfcn) }

    /**
     * NR 频段区间互相重叠（n77 3300–4200 与 n78 3300–3800、n38 与 n41 在 2570–2620），
     * **单一频率无法唯一确定 Band**。因此不提供返回单值的便捷方法：
     * 调用方一律走 [nrBandsForFrequency] 拿全候选 + 「非唯一」标注，禁止 firstOrNull 静默挑一个。
     */
    fun nrBandsForFrequency(freqMhz: Double): List<NrBand> =
        NR_BANDS.filter { it.contains(freqMhz) }
}
