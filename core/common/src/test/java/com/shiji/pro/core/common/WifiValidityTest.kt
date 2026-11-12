package com.shiji.pro.core.common

import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.Origin
import com.shiji.pro.core.model.WifiSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WiFi 有效性判定单测（缺陷 D2 回归锁）。纯 JVM，不依赖 android.* / Robolectric。
 *
 * 哨兵值组取自真机取证：小米 22041216UC / Android 14 / API 34，WiFi **未连接**（只开了个人热点 ap0，
 * 默认网络是蜂窝 ccmni1），`adb shell dumpsys wifi` 的 mWifiInfo 真值：
 * SSID=&lt;unknown ssid&gt; / BSSID=&lt;none&gt; / MAC=02:00:00:00:00:00 / IP=null /
 * Supplicant state: DISCONNECTED / RSSI=-127 / Link speed=-1Mbps / Frequency=-1MHz / Net ID=-1 /
 * Security type=-1。旧实现把这些直接包成 MEASURED 显示成「实测」，本测试锁死新口径。
 */
class WifiValidityTest {

    // ============================================== 真机实测到的那组哨兵值 ==============================================

    @Test
    fun `真机哨兵 rssi 负127 判 UNAVAILABLE 且 value 为 null 有原因`() {
        val f = WifiValidity.rssiField(-127)
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertNull(f.value)
        assertNotNull(f.reason)
        assertTrue(f.reason!!.isNotBlank())
        assertTrue("必须写明 WiFi 未连接", f.reason!!.contains("WiFi 未连接"))
        assertTrue("必须保留系统原值便于取证", f.reason!!.contains("-127"))
        assertEquals(FieldValue.NA_TEXT, f.displayText())
    }

    @Test
    fun `真机哨兵 频率 负1 判 UNAVAILABLE 且 value 为 null 有原因`() {
        val f = WifiValidity.frequencyField(-1)
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertNull(f.value)
        assertTrue(f.reason!!.isNotBlank())
        assertTrue(f.reason!!.contains("WiFi 未连接"))
        assertTrue(f.reason!!.contains("-1"))
        assertEquals(FieldValue.NA_TEXT, f.displayText())
    }

    @Test
    fun `真机哨兵 链路速率 负1 判 UNAVAILABLE 且 value 为 null 有原因`() {
        val f = WifiValidity.linkSpeedField(-1)
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertNull(f.value)
        assertTrue(f.reason!!.isNotBlank())
        assertTrue(f.reason!!.contains("WiFi 未连接"))
        assertTrue(f.reason!!.contains("-1"))
        // 框架 WifiInfo.LINK_SPEED_UNKNOWN = -1（javap -constants 核验）
    }

    @Test
    fun `真机哨兵 安全类型 负1 判 UNAVAILABLE 且 value 为 null 有原因`() {
        val f = WifiValidity.securityTypeField(-1)
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertNull(f.value)
        assertTrue(f.reason!!.isNotBlank())
        assertTrue(f.reason!!.contains("WiFi 未连接"))
        assertTrue(f.reason!!.contains("-1"))
    }

    @Test
    fun `真机那组值全部不可进曲线`() {
        assertFalse(WifiValidity.rssiField(-127).isPlottable())
        assertFalse(WifiValidity.frequencyField(-1).isPlottable())
        assertFalse(WifiValidity.linkSpeedField(-1).isPlottable())
        assertFalse(WifiValidity.securityTypeField(-1).isPlottable())
    }

    // ============================================== 连接态判定（COMPLETED + netId） ==============================================

    @Test
    fun `DISCONNECTED 且 netId 负1 判未连接并给出依据`() {
        // dumpsys 实测：Supplicant state: DISCONNECTED, Net ID: -1
        val reason = WifiValidity.disconnectedReason("DISCONNECTED", -1)
        assertNotNull("未连接必须给出原因", reason)
        assertTrue(reason!!.contains("WiFi 未连接"))
        assertTrue("不借用其它链路信息要写进原因", reason.contains("不显示其它链路的地址信息"))
        assertTrue("判定依据要含 SupplicantState", reason.contains("DISCONNECTED"))
        assertTrue("判定依据要含 NetworkId", reason.contains("-1"))
    }

    @Test
    fun `netId 负1 但状态 COMPLETED 仍判未连接`() {
        val reason = WifiValidity.disconnectedReason("COMPLETED", -1)
        assertNotNull(reason)
        assertTrue(reason!!.contains("-1"))
    }

    @Test
    fun `状态非 COMPLETED 但 netId 合法也判未连接`() {
        val reason = WifiValidity.disconnectedReason("SCANNING", 3)
        assertNotNull(reason)
        assertTrue(reason!!.contains("SCANNING"))
    }

    @Test
    fun `COMPLETED 且 netId 合法才算真连接`() {
        assertNull(WifiValidity.disconnectedReason("COMPLETED", 0))
        assertNull(WifiValidity.disconnectedReason("COMPLETED", 5))
    }

    @Test
    fun `握手中间态与空值一律不得判为已连接`() {
        listOf(
            "UNINITIALIZED", "DISCONNECTED", "SCANNING", "AUTHENTICATING", "ASSOCIATING",
            "ASSOCIATED", "FOUR_WAY_HANDSHAKE", "GROUP_HANDSHAKE", "INACTIVE",
            "INTERFACE_DISABLED", "DORMANT", "INVALID", null,
        ).forEach { state ->
            assertNotNull("$state 不能算已连接", WifiValidity.disconnectedReason(state, 3))
        }
    }

    // ============================================== 正常值组（真机连上 WiFi 的常见值） ==============================================

    @Test
    fun `正常 rssi 负57 为 MEASURED`() {
        val f = WifiValidity.rssiField(-57)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(-57, f.value)
        assertNull(f.reason)
        assertTrue(f.isPlottable())
    }

    @Test
    fun `正常 频率 5180 为 MEASURED`() {
        val f = WifiValidity.frequencyField(5180)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(5180, f.value)
        assertNull(f.reason)
    }

    @Test
    fun `正常 链路速率 240 为 MEASURED`() {
        val f = WifiValidity.linkSpeedField(240)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(240, f.value)
        assertNull(f.reason)
    }

    @Test
    fun `正常值显示文本就是数值本身`() {
        assertEquals("-57", WifiValidity.rssiField(-57).displayText())
        assertEquals("5180", WifiValidity.frequencyField(5180).displayText())
    }

    // ============================================== 边界值 ==============================================

    @Test
    fun `rssi 上界 0 判有效`() {
        val f = WifiValidity.rssiField(0)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(0, f.value)
    }

    @Test
    fun `rssi 下界 负120 判有效`() {
        val f = WifiValidity.rssiField(-120)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(-120, f.value)
    }

    @Test
    fun `rssi 刚越界 负121 判无效并保留原值`() {
        val f = WifiValidity.rssiField(-121)
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertNull(f.value)
        assertTrue(f.reason!!.contains("-121"))
    }

    @Test
    fun `rssi 正数越界判无效`() {
        // dBm 不可能为正（0 为上界）
        val f = WifiValidity.rssiField(5)
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertNull(f.value)
    }

    @Test
    fun `频率 0 判无效`() {
        val f = WifiValidity.frequencyField(0)
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertNull(f.value)
        assertTrue(f.reason!!.isNotBlank())
        assertTrue(f.reason!!.contains("0"))
    }

    @Test
    fun `链路速率 0 判无效`() {
        val f = WifiValidity.linkSpeedField(0)
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertNull(f.value)
        assertTrue(f.reason!!.isNotBlank())
        assertTrue(f.reason!!.contains("0"))
    }

    @Test
    fun `频率 2412 与 2484 这些合法信道频率判有效`() {
        assertEquals(Origin.MEASURED, WifiValidity.frequencyField(2412).origin)
        assertEquals(Origin.MEASURED, WifiValidity.frequencyField(2484).origin)
        assertEquals(Origin.MEASURED, WifiValidity.frequencyField(5955).origin)
    }

    // ============================================== 安全类型 int → 枚举映射 ==============================================
    // int 值取自 AOSP android-37 的 WifiInfo.SECURITY_TYPE_*（javap -constants 实测，非推测）。

    @Test
    fun `安全类型 0 OPEN 映射 开放`() {
        val f = WifiValidity.securityTypeField(0)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(WifiSecurity.OPEN, f.value)
    }

    @Test
    fun `安全类型 1 WEP 映射 WEP`() {
        assertEquals(WifiSecurity.WEP, WifiValidity.securityTypeField(1).value)
    }

    @Test
    fun `安全类型 2 PSK 映射 WPA2 且 note 留痕框架类型`() {
        val f = WifiValidity.securityTypeField(2)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(WifiSecurity.WPA2, f.value)
        assertNotNull(f.note)
        assertTrue(f.note!!.contains("PSK"))
        assertTrue(f.note!!.contains("getCurrentSecurityType"))
    }

    @Test
    fun `安全类型 3 EAP 映射 EAP`() {
        val f = WifiValidity.securityTypeField(3)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(WifiSecurity.EAP, f.value)
    }

    @Test
    fun `安全类型 4 SAE 映射 WPA3`() {
        val f = WifiValidity.securityTypeField(4)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(WifiSecurity.WPA3, f.value)
    }

    @Test
    fun `安全类型 6 OWE 映射 OWE`() {
        assertEquals(WifiSecurity.OWE, WifiValidity.securityTypeField(6).value)
    }

    @Test
    fun `安全类型 7 与 8 WAPI 子类型都归 WAPI`() {
        assertEquals(WifiSecurity.WAPI, WifiValidity.securityTypeField(7).value)
        assertEquals(WifiSecurity.WAPI, WifiValidity.securityTypeField(8).value)
    }

    @Test
    fun `框架企业级细分类型归 EAP 并在 note 保留原始类型名`() {
        // 5=EAP_WPA3_ENTERPRISE_192_BIT 9=EAP_WPA3_ENTERPRISE 10=OSEN 11/12=Passpoint 13=DPP
        listOf(5, 9, 10, 11, 12, 13).forEach { code ->
            val f = WifiValidity.securityTypeField(code)
            assertEquals("code=$code", Origin.MEASURED, f.origin)
            assertEquals("code=$code", WifiSecurity.EAP, f.value)
            assertNotNull("归并必须留痕", f.note)
            assertTrue(f.note!!.contains("EAP"))
        }
    }

    @Test
    fun `未定义安全类型值不臆断成任何枚举`() {
        listOf(14, 99, -2, Int.MIN_VALUE, Int.MAX_VALUE).forEach { code ->
            val f = WifiValidity.securityTypeField(code)
            assertEquals("code=$code", Origin.UNAVAILABLE, f.origin)
            assertNull("code=$code", f.value)
            assertTrue(f.reason!!.isNotBlank())
        }
    }

    // ============================================== 三态不变式 ==============================================

    @Test
    fun `UNAVAILABLE 条目一律 value 为 null 且 reason 非空`() {
        val all = listOf(
            WifiValidity.rssiField(-127),
            WifiValidity.frequencyField(-1),
            WifiValidity.linkSpeedField(-1),
            WifiValidity.securityTypeField(-1),
        )
        all.forEach { f ->
            assertEquals(Origin.UNAVAILABLE, f.origin)
            assertNull("UNAVAILABLE 时 value 必须为 null", f.value)
            assertNotNull("UNAVAILABLE 时 reason 必填", f.reason)
            assertTrue(f.reason!!.isNotBlank())
        }
    }

    @Test
    fun `MEASURED 条目一律 value 非 null 且 reason 为空`() {
        val all: List<FieldValue<*>> = listOf(
            WifiValidity.rssiField(-57),
            WifiValidity.frequencyField(5180),
            WifiValidity.linkSpeedField(240),
            WifiValidity.securityTypeField(2),
        )
        all.forEach { f ->
            assertEquals(Origin.MEASURED, f.origin)
            assertNotNull(f.value)
            assertNull(f.reason)
        }
    }
}
