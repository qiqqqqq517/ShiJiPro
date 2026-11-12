package com.shiji.pro.core.common

import com.shiji.pro.core.model.BandTable
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.LteBand
import com.shiji.pro.core.model.NrBand

/**
 * 频段 / 频率 / 信道 / 小区标识换算（设计稿 §4.2 / §4.5）。
 *
 * ⚠️ 本对象所有输出均为**换算值(Origin.DERIVED)**，不是实测值。
 * 输入超出标准表覆盖范围时返回 `FieldValue.unavailable(...)`，
 * 严禁按「最近频段」猜测一个 Band 号后当换算值展示。
 *
 * 公式来源：3GPP TS 36.101 §5.7.3 / Table 5.7.3-1「E-UTRA channel numbers」、
 * TS 38.104 §5.4.2.1 / Table 5.4.2.1-1（NR-ARFCN 全局栅格）、IEEE 802.11 信道编号；
 * 「E-UTRA operating bands」是 TS 36.101 Table 5.5-1，勿混用表号。
 * 独立核验与来源 URL 分级见 docs/P1_3GPP_band_reference.md。
 */
object FrequencyConverter {

    private const val UNAVAILABLE_SENTINEL = Int.MAX_VALUE

    // ---------------------------------------------------------------- LTE ----

    /**
     * LTE 下行 EARFCN → 下行中心频率(MHz)。
     * FDL = FDL_low + 0.1 * (EARFCN - NDL_low)（3GPP TS 36.101）。
     */
    fun lteEarfcnToDlFrequency(earfcn: Int): FieldValue<Double> {
        if (earfcn == UNAVAILABLE_SENTINEL || earfcn < 0) {
            return FieldValue.unavailable("EARFCN 取不到或为非法值")
        }
        val band = BandTable.lteBandForEarfcn(earfcn)
            ?: return FieldValue.unavailable("EARFCN $earfcn 不在内置 3GPP 频段表覆盖范围")
        val mhz = band.dlFreqLowMhz + 0.1 * (earfcn - band.earfcnLow)
        return FieldValue.derived(mhz, "B${band.band} FDL_low=${band.dlFreqLowMhz}MHz + 0.1*(EARFCN-${band.earfcnLow})")
    }

    /** LTE EARFCN → 频段条目（供 UI 显示 Band 号 / 双工模式 / 常见名称）。 */
    fun lteBandForEarfcn(earfcn: Int): FieldValue<LteBand> {
        if (earfcn == UNAVAILABLE_SENTINEL || earfcn < 0) {
            return FieldValue.unavailable("EARFCN 取不到或为非法值")
        }
        val band = BandTable.lteBandForEarfcn(earfcn)
            ?: return FieldValue.unavailable("EARFCN $earfcn 不在内置 3GPP 频段表覆盖范围")
        return FieldValue.derived(band, "按 EARFCN 区间查表")
    }

    /**
     * LTE ECI = (TAC shl 16) or CI（28 bit）。
     * TAC/CI 为 CellInfo.UNAVAILABLE(MAX_VALUE) 时返回 N/A，不拼出无意义的大数。
     */
    fun lteEci(tac: Int, ci: Int): FieldValue<Long> {
        if (tac == UNAVAILABLE_SENTINEL || ci == UNAVAILABLE_SENTINEL ||
            tac < 0 || ci < 0 || ci > 0xFFFF
        ) {
            return FieldValue.unavailable("TAC 或 CI 取不到 / 越界，无法换算 ECI")
        }
        val eci = (tac.toLong() shl 16) or (ci.toLong() and 0xFFFFL)
        return FieldValue.derived(eci, "ECI = (TAC shl 16) or CI")
    }

    /** LTE eNB ID / 扇区：由 CI 拆位（同为换算值）。 */
    fun lteEnbAndSector(ci: Int): FieldValue<Pair<Int, Int>> {
        if (ci == UNAVAILABLE_SENTINEL || ci < 0 || ci > 0xFFFF) {
            return FieldValue.unavailable("CI 取不到 / 越界，无法拆分 eNB ID")
        }
        return FieldValue.derived((ci shr 8) to (ci and 0xFF), "eNB ID = CI shr 8；扇区 = CI and 0xFF")
    }

    // ----------------------------------------------------------------- NR ----

    /**
     * NR-ARFCN → 参考频率 FREF(MHz)，3GPP TS 38.104 Table 5.4.2.1-1 全局栅格：
     * - 0–3000 MHz      : ΔF=5 kHz,   FREF = 0.005 * NREF
     * - 3000–24250 MHz  : ΔF=15 kHz,  FREF = 3000 + 0.015 * (NREF - 600000)
     * - 24250–100000 MHz: ΔF=60 kHz,  FREF = 24250.08 + 0.06 * (NREF - 2016667)
     */
    fun nrArfcnToFrequency(nrArfcn: Int): FieldValue<Double> {
        if (nrArfcn == UNAVAILABLE_SENTINEL || nrArfcn < 0 || nrArfcn > 3279165) {
            return FieldValue.unavailable("NR-ARFCN 取不到或超出全局栅格范围 0..3279165")
        }
        val mhz = when {
            nrArfcn <= 599999 -> 0.005 * nrArfcn
            nrArfcn <= 2016666 -> 3000.0 + 0.015 * (nrArfcn - 600000)
            else -> 24250.08 + 0.06 * (nrArfcn - 2016667)
        }
        return FieldValue.derived(mhz, "3GPP 38.104 NR-ARFCN 全局栅格")
    }

    /**
     * 频率 → NR 频段候选。频段范围互相重叠（如 n38/n41/n77/n78），
     * 单一频率无法唯一确定 Band，故返回**全部候选**并标注多候选，不静默挑一个。
     */
    fun nrBandsForFrequency(freqMhz: Double): FieldValue<List<NrBand>> {
        if (freqMhz <= 0.0) return FieldValue.unavailable("频率取不到，无法查 NR 频段")
        val candidates = BandTable.nrBandsForFrequency(freqMhz)
        if (candidates.isEmpty()) {
            return FieldValue.unavailable("频率 ${freqMhz}MHz 不在内置 NR FR1 频段表覆盖范围")
        }
        val note = if (candidates.size > 1) "频段范围重叠，共 ${candidates.size} 个候选，非唯一" else "按频率范围查表"
        return FieldValue.derived(candidates, note)
    }

    // --------------------------------------------------------------- WiFi ----

    /** 频率(MHz) → WiFi 信道号。2.4G / 5G / 6G 分别换算；14 信道单列。 */
    fun wifiFrequencyToChannel(freqMhz: Int): FieldValue<Int> {
        if (freqMhz <= 0) return FieldValue.unavailable("频率取不到，无法换算信道")
        val channel = when {
            freqMhz == 2484 -> 14                      // 2.4G ch14 特例
            freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
            freqMhz in 5160..5895 -> (freqMhz - 5000) / 5
            freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
            else -> return FieldValue.unavailable("频率 ${freqMhz}MHz 不属于 2.4/5/6GHz 已知信道范围")
        }
        return FieldValue.derived(channel, "频率→信道号换算")
    }

    fun wifiBandLabel(freqMhz: Int): FieldValue<String> {
        if (freqMhz <= 0) return FieldValue.unavailable("频率取不到")
        val label = when {
            freqMhz < 3000 -> "2.4 GHz"
            freqMhz < 5900 -> "5 GHz"
            else -> "6 GHz"
        }
        return FieldValue.derived(label, "按频率区间判定")
    }

    // ----------------------------------------------------- 网络地址换算 ----

    /** IPv4 前缀长度 → 点分十进制子网掩码。 */
    fun prefixLengthToIpv4Mask(prefixLength: Int): FieldValue<String> {
        if (prefixLength !in 0..32) return FieldValue.unavailable("前缀长度非法：$prefixLength")
        val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        val text = listOf(24, 16, 8, 0).joinToString(".") { ((mask shr it) and 0xFF).toString() }
        return FieldValue.derived(text, "由前缀长度 /$prefixLength 换算")
    }

    /** IPv4 地址 + 前缀长度 → 广播地址（换算值）。 */
    fun ipv4Broadcast(ip: String, prefixLength: Int): FieldValue<String> {
        if (prefixLength !in 0..32) return FieldValue.unavailable("前缀长度非法：$prefixLength")
        val parts = ip.split('.')
        if (parts.size != 4) return FieldValue.unavailable("IPv4 地址格式非法")
        val octets = parts.mapNotNull { it.toIntOrNull()?.takeIf { v -> v in 0..255 } }
        if (octets.size != 4) return FieldValue.unavailable("IPv4 地址格式非法")
        val addr = octets.fold(0L) { acc, v -> (acc shl 8) or v.toLong() }
        val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
        val bcast = (addr and mask) or (mask.inv() and 0xFFFFFFFFL)
        val text = listOf(24, 16, 8, 0).joinToString(".") { ((bcast shr it) and 0xFF).toString() }
        return FieldValue.derived(text, "广播地址 = (IP and mask) or (not mask)")
    }

    // ------------------------------------------------------------- 估算 ----

    /**
     * LTE Timing Advance → 距离（**估算值**，设计稿 §2.4 基站地图）。
     * 1 TA ≈ 16·Ts·c/2 ≈ 78.1 m。返回 Origin.ESTIMATED，UI 必须附「估算值，仅供参考」。
     */
    fun timingAdvanceToDistanceMeters(ta: Int): FieldValue<Double> {
        if (ta == UNAVAILABLE_SENTINEL || ta < 0) {
            return FieldValue.unavailable("Timing Advance 取不到，无法估算距离")
        }
        return FieldValue.estimated(ta * 78.1, "1 TA ≈ 78.1 m，传播时延估算，非实测")
    }

    /** GSM RXLEV：仅 ASU 可用时 ASU→dBm 的换算（ASU 0..31 → -110..-48 dBm）。 */
    fun gsmAsuToDbm(asu: Int): FieldValue<Int> {
        if (asu == UNAVAILABLE_SENTINEL || asu !in 0..31) {
            return FieldValue.unavailable("GSM ASU 取不到或越界")
        }
        return FieldValue.derived(-110 + 2 * asu, "dBm = -110 + 2*ASU")
    }
}
