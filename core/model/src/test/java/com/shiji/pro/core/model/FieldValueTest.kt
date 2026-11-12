package com.shiji.pro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 三态契约不变式单测（P1）：从构造入口堵死「用默认值伪装实测值」。 */
class FieldValueTest {

    @Test
    fun `measured 携带数值且标为实测`() {
        val f = FieldValue.measured(-98)
        assertEquals(Origin.MEASURED, f.origin)
        assertEquals(-98, f.value)
        assertEquals("-98", f.displayText())
        assertTrue(f.isPlottable())
    }

    @Test
    fun `derived 携带换算说明`() {
        val f = FieldValue.derived(1842.5, "EARFCN 查表")
        assertEquals(Origin.DERIVED, f.origin)
        assertEquals("EARFCN 查表", f.note)
        assertEquals("1842.5", f.displayText())
    }

    @Test
    fun `estimated 标为估算`() {
        val f = FieldValue.estimated(156.2, "TA 传播模型")
        assertEquals(Origin.ESTIMATED, f.origin)
        assertTrue(f.note!!.contains("模型"))
    }

    @Test
    fun `unavailable 强制 value 为 null 且显示不可用占位`() {
        val f = FieldValue.unavailable<Int>("Android 10+ 普通应用取不到")
        assertEquals(Origin.UNAVAILABLE, f.origin)
        assertEquals(null, f.value)
        assertEquals("N/A", f.displayText())
        assertEquals("Android 10+ 普通应用取不到", f.reason)
        assertTrue(!f.isPlottable())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unavailable 缺原因时拒绝构造`() {
        FieldValue.unavailable<Int>("")
    }

    @Test
    fun `原枚举取值稳定`() {
        assertEquals(
            listOf("MEASURED", "DERIVED", "ESTIMATED", "UNAVAILABLE"),
            Origin.entries.map { it.name },
        )
    }

    @Test
    fun `GnssConstellation 由 typeId 映射且未知 id 落 UNKNOWN`() {
        assertEquals(GnssConstellation.BEIDOU, GnssConstellation.fromTypeId(5))
        assertEquals(GnssConstellation.GPS, GnssConstellation.fromTypeId(1))
        assertEquals(GnssConstellation.UNKNOWN, GnssConstellation.fromTypeId(99))
    }
}
