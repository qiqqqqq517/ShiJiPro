package com.shiji.pro.core.common

import com.shiji.pro.core.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 频段/频率/信道换算单测（P1）。数值取自 3GPP TS 36.101 / 38.104 的确定性公式。 */
class FrequencyConverterTest {

    // ---- LTE EARFCN → DL 频率 ----

    @Test
    fun `band3 earfcn 1300 得 1815 MHz`() {
        val r = FrequencyConverter.lteEarfcnToDlFrequency(1300)
        assertEquals(Origin.DERIVED, r.origin)
        assertEquals(1815.0, r.value!!, 1e-9)   // 1805 + 0.1*(1300-1200)
    }

    @Test
    fun `band1 earfcn 0 得 2110 MHz`() {
        assertEquals(2110.0, FrequencyConverter.lteEarfcnToDlFrequency(0).value!!, 1e-9)
    }

    @Test
    fun `band40 earfcn 38650 得 2300 MHz`() {
        assertEquals(2300.0, FrequencyConverter.lteEarfcnToDlFrequency(38650).value!!, 1e-9)
    }

    // ---- 独立核验发现的回归项（P1 交叉对拍）----

    @Test
    fun `band28 earfcn 9210 得 758MHz 且 9659 落在 band28 内`() {
        // 缺陷回归：旧表把 Band28 写成 9260..9660（9660 实为 Band29 SDL 起点），
        // 会漏掉 9210..9259 并把 9660 之后的空洞误算成 Band28 频率。
        val lo = FrequencyConverter.lteEarfcnToDlFrequency(9210)
        assertEquals(Origin.DERIVED, lo.origin)
        assertEquals(28, FrequencyConverter.lteBandForEarfcn(9210).value!!.band)
        assertEquals(758.0, lo.value!!, 1e-9)
        assertEquals(28, FrequencyConverter.lteBandForEarfcn(9659).value!!.band)
    }

    @Test
    fun `band28 与 band29 交界 9660 不得误判为 band28`() {
        assertEquals(28, FrequencyConverter.lteBandForEarfcn(9659).value!!.band)
        // 9660 在内置表中属于空洞（Band29 为 SDL，不纳入 LTE FDD 下行服务小区表）
        assertEquals(Origin.UNAVAILABLE, FrequencyConverter.lteBandForEarfcn(9660).origin)
    }

    @Test
    fun `NR FDD 频段用下行范围 n1 为 2110 到 2170`() {
        // 缺陷回归：旧表 FDD 项误用 UL 范围（n1=1920–1980），
        // 与 NR-ARFCN 得到的 FREF（DL 载波频率）查表口径不一致。
        val n1 = FrequencyConverter.nrBandsForFrequency(2155.0).value!!
        assertEquals(1, n1.size)
        assertEquals(1, n1[0].band)
        assertEquals("2100", n1[0].commonName)
    }

    @Test
    fun `NR n3 下行 1842_5MHz 命中 n3 而非 n3 UL 范围`() {
        val n3 = FrequencyConverter.nrBandsForFrequency(1842.5).value!!
        assertTrue(n3.any { it.band == 3 })
        assertEquals(1805.0, n3.first { it.band == 3 }.freqLowMhz, 1e-9)
    }

    @Test
    fun `频段表未覆盖的 EARFCN 返回不可用而不是猜一个 band`() {
        // 阴性对照：6000 位于 Band20(6150..6449) 之前的空洞，必须降级为 N/A
        val r = FrequencyConverter.lteEarfcnToDlFrequency(6000)
        assertEquals(Origin.UNAVAILABLE, r.origin)
        assertTrue(r.value == null)
        assertTrue(r.reason!!.contains("不在"))
        assertEquals("N/A", r.displayText())
    }

    @Test
    fun `MAX_VALUE 哨兵值不产生伪造频率`() {
        val r = FrequencyConverter.lteEarfcnToDlFrequency(Int.MAX_VALUE)
        assertEquals(Origin.UNAVAILABLE, r.origin)
        assertTrue(r.value == null)
    }

    // ---- LTE Band / ECI ----

    @Test
    fun `earfcn 1300 查表得 Band3 FDD`() {
        val b = FrequencyConverter.lteBandForEarfcn(1300).value!!
        assertEquals(3, b.band)
        assertEquals("FDD", b.duplex.name)
        assertEquals("1800", b.commonName)
    }

    @Test
    fun `ECI 由 TAC 与 CI 换算`() {
        assertEquals(0x1234_5678L, FrequencyConverter.lteEci(0x1234, 0x5678).value!!)
    }

    @Test
    fun `ECI 输入哨兵返回不可用`() {
        assertEquals(Origin.UNAVAILABLE, FrequencyConverter.lteEci(Int.MAX_VALUE, 100).origin)
        assertEquals(Origin.UNAVAILABLE, FrequencyConverter.lteEci(10, Int.MAX_VALUE).origin)
    }

    @Test
    fun `eNB ID 与扇区由 CI 拆位`() {
        val p = FrequencyConverter.lteEnbAndSector(0x5678).value!!
        assertEquals(0x56, p.first)
        assertEquals(0x78, p.second)
    }

    // ---- NR-ARFCN 全局栅格 ----

    @Test
    fun `NR-ARFCN 0 与 600000 边界`() {
        assertEquals(0.0, FrequencyConverter.nrArfcnToFrequency(0).value!!, 1e-9)
        // 600000 属于第二档：3000 + 0.015*(600000-600000) = 3000
        assertEquals(3000.0, FrequencyConverter.nrArfcnToFrequency(600000).value!!, 1e-9)
    }

    @Test
    fun `NR-ARFCN 620000 得 3300 MHz`() {
        // 3000 + 0.015*(620000-600000) = 3300
        assertEquals(3300.0, FrequencyConverter.nrArfcnToFrequency(620000).value!!, 1e-9)
    }

    @Test
    fun `NR-ARFCN 超出全局栅格返回不可用`() {
        assertEquals(Origin.UNAVAILABLE, FrequencyConverter.nrArfcnToFrequency(9999999).origin)
    }

    @Test
    fun `NR 频率 3500MHz 落在重叠频段 返回多候选`() {
        val r = FrequencyConverter.nrBandsForFrequency(3500.0)
        assertEquals(Origin.DERIVED, r.origin)
        // n77(3300-4200) 与 n78(3300-3800) 均覆盖 3500 → 不静默挑一个
        assertTrue(r.value!!.size >= 2)
        assertTrue(r.note!!.contains("候选"))
    }

    @Test
    fun `BandTable 不再提供静默取单值的 NR 查表入口`() {
        // 缺陷回归：旧 nrBandForFrequency 用 firstOrNull，n77 在 n78 前 → 3500MHz 静默返回 n77。
        // 现统一走全候选 API，且 n77/n78 都必须在结果里。
        val bands = com.shiji.pro.core.model.BandTable.nrBandsForFrequency(3500.0)
        assertTrue(bands.any { it.band == 77 })
        assertTrue(bands.any { it.band == 78 })
    }

    // ---- WiFi 信道 ----

    @Test
    fun `2_4G 频率 2437 得信道 6`() {
        assertEquals(6, FrequencyConverter.wifiFrequencyToChannel(2437).value!!)
    }

    @Test
    fun `ch14 特例 2484MHz`() {
        assertEquals(14, FrequencyConverter.wifiFrequencyToChannel(2484).value!!)
    }

    @Test
    fun `5G 频率 5180 得信道 36`() {
        assertEquals(36, FrequencyConverter.wifiFrequencyToChannel(5180).value!!)
    }

    @Test
    fun `6G 频率 5975 得信道 5`() {
        assertEquals(5, FrequencyConverter.wifiFrequencyToChannel(5975).value!!)
    }

    @Test
    fun `非法频率返回不可用`() {
        assertEquals(Origin.UNAVAILABLE, FrequencyConverter.wifiFrequencyToChannel(900).origin)
    }

    // ---- 网络地址换算 ----

    @Test
    fun `24 前缀长度换算掩码`() {
        assertEquals("255.255.255.0", FrequencyConverter.prefixLengthToIpv4Mask(24).value!!)
    }

    @Test
    fun `广播地址换算`() {
        assertEquals("192.168.1.255", FrequencyConverter.ipv4Broadcast("192.168.1.10", 24).value!!)
    }

    @Test
    fun `非法前缀返回不可用`() {
        assertEquals(Origin.UNAVAILABLE, FrequencyConverter.prefixLengthToIpv4Mask(33).origin)
    }

    // ---- 估算值标注 ----

    @Test
    fun `TA 距离标为估算值`() {
        val r = FrequencyConverter.timingAdvanceToDistanceMeters(1)
        assertEquals(Origin.ESTIMATED, r.origin)
        assertEquals(78.1, r.value!!, 1e-9)
        assertTrue(r.note!!.contains("估算"))
    }

    @Test
    fun `GSM ASU 换算`() {
        assertEquals(-48, FrequencyConverter.gsmAsuToDbm(31).value!!)
        assertEquals(Origin.UNAVAILABLE, FrequencyConverter.gsmAsuToDbm(99).origin)
    }
}
