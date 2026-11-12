package com.shiji.pro.core.common

import com.shiji.pro.core.model.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [Format] 单测（缺陷 D4 回归）。
 *
 * 断言里的数字全部来自真机取证（小米 22041216UC / Android 14 / API 34，UIAutomator dump）：
 * RAM 总量 7848685568、RAM 可用 3129905152、ROM 总量 498205687808、ROM 可用 271640363008、
 * 屏幕尺寸 6.10598541197403 英寸、刷新率 144.00002 Hz。
 * 目的是钉死「显示层不再输出裸整数 / 全精度浮点」这件事，防止后续改动回退。
 */
class FormatTest {

    // ---- 真机字节数：1024 进制 ----

    @Test
    fun `真机 RAM 总量显示为 GiB`() {
        assertEquals("7.31 GiB", Format.bytesHuman(7848685568L))
    }

    @Test
    fun `真机 RAM 可用显示为 GiB`() {
        assertEquals("2.91 GiB", Format.bytesHuman(3129905152L))
    }

    @Test
    fun `真机 ROM 总量显示为 GiB`() {
        // 498205687808 / 1024^3 = 463.990204…，HALF_UP 取 2 位 → 463.99（不是 464.00）
        assertEquals("463.99 GiB", Format.bytesHuman(498205687808L))
    }

    @Test
    fun `真机 ROM 可用显示为 GiB`() {
        // 271640363008 / 1024^3 = 252.984802… → 252.98
        assertEquals("252.98 GiB", Format.bytesHuman(271640363008L))
    }

    // ---- 无效值必须回落 null，绝不输出伪值 ----

    @Test
    fun `零与负数与 null 都返回 null 由调用方回落 N-A`() {
        assertNull(Format.bytesHuman(0L))
        assertNull(Format.bytesHuman(-1L))
        assertNull(Format.bytesHuman(-498205687808L))
        assertNull(Format.bytesHuman(null))
    }

    @Test
    fun `小于一个 KiB 输出整数原值不写无意义小数`() {
        assertEquals("1 B", Format.bytesHuman(1L))
        assertEquals("512 B", Format.bytesHuman(512L))
        assertEquals("1023 B", Format.bytesHuman(1023L))
    }

    @Test
    fun `刚好一个单位边界不进位到下一级`() {
        assertEquals("1.00 KiB", Format.bytesHuman(1024L))
        assertEquals("1.00 MiB", Format.bytesHuman(1048576L))
        assertEquals("1.00 GiB", Format.bytesHuman(1073741824L))
        assertEquals("1.00 TiB", Format.bytesHuman(1099511627776L))
    }

    @Test
    fun `decimal 档走 1000 进制与 SI 后缀`() {
        // 498205687808 / 1000^3 = 498.205687808 GB
        assertEquals("498.21 GB", Format.bytesHuman(498205687808L, "decimal"))
        assertEquals("7.85 GB", Format.bytesHuman(7848685568L, "decimal"))
        // 未知 unit 值按 binary 兜底，不抛异常
        assertEquals("7.31 GiB", Format.bytesHuman(7848685568L, "binary"))
        assertEquals("7.31 GiB", Format.bytesHuman(7848685568L, ""))
    }

    // ---- 真机浮点数：短格式 ----

    @Test
    fun `真机屏幕尺寸换算值保留两位并裁尾零`() {
        assertEquals("6.11", Format.doubleShort(6.10598541197403))
    }

    @Test
    fun `真机刷新率浮点误差被抹平`() {
        assertEquals("144", Format.floatShort(144.00002f))
        assertEquals("144", Format.doubleShort(144.0))
        // 需要更高精度时可显式加大位数（尾零仍被裁，只留有效位）
        assertEquals("6.106", Format.floatShort(6.105985f, 3))
    }

    @Test
    fun `doubleShort 去掉无意义尾零`() {
        assertEquals("2.5", Format.doubleShort(2.5))
        assertEquals("2.5", Format.doubleShort(2.50))
        assertEquals("0", Format.doubleShort(0.0))
        assertEquals("1000", Format.doubleShort(1000.0))
        assertEquals("-6.11", Format.doubleShort(-6.10598541197403))
    }

    @Test
    fun `非有限浮点显示 N-A 不显示 NaN 或 Infinity`() {
        assertEquals("N/A", Format.doubleShort(Double.NaN))
        assertEquals("N/A", Format.doubleShort(Double.POSITIVE_INFINITY))
        assertEquals("N/A", Format.floatShort(Float.NaN))
        assertEquals("N/A", Format.floatShort(Float.NEGATIVE_INFINITY))
    }

    // ---- 三态安全：UNAVAILABLE 一律 N/A，不得渲染占位值 ----

    @Test
    fun `unavailable 走 displayTextWith 恒为 N-A 且 value 为 null`() {
        val f: FieldValue<Long> = FieldValue.unavailable("无存储权限")
        assertNull(f.value)
        assertEquals("N/A", f.displayTextWith { Format.bytesHuman(it)!! })
        // reason 必填，供 UI 说明原因
        assertEquals("无存储权限", f.reason)
    }

    @Test
    fun `measured 与 derived 走 displayTextWith 输出格式化结果`() {
        val ram: FieldValue<Long> = FieldValue.measured(7848685568L)
        assertEquals("7.31 GiB", ram.displayTextWith { Format.bytesHuman(it)!! })

        val size: FieldValue<Double> = FieldValue.derived(6.10598541197403, "对角像素 / densityDpi")
        assertEquals("6.11", size.displayTextWith { Format.doubleShort(it) })

        val hz: FieldValue<Float> = FieldValue.measured(144.00002f)
        assertEquals("144", hz.displayTextWith { Format.floatShort(it) })
    }

    @Test
    fun `格式化不改变原值`() {
        // 只影响显示：领域模型里的 Long / Double 原值必须保持不动（CSV 导出与统计依赖原值）
        val ram: FieldValue<Long> = FieldValue.measured(7848685568L)
        assertEquals("7.31 GiB", Format.bytesHuman(ram.value))
        assertEquals(7848685568L, ram.value!!)

        val size: FieldValue<Double> = FieldValue.derived(6.10598541197403)
        assertEquals("6.11", Format.doubleShort(size.value!!))
        assertEquals(6.10598541197403, size.value!!, 0.0)

        val hz: FieldValue<Float> = FieldValue.measured(144.00002f)
        assertEquals("144", Format.floatShort(hz.value!!))
        // 必须比较 Float 本身：144.00002f 展宽成 double 是 144.00001525878906，
        // 与十进制字面量 144.00002 在 delta=0 的 double 断言下必然不等
        // （实测报错 expected:<144.00002> but was:<144.00001525878906>）。
        // 实现无误，是断言口径错 ⇒ 改断言：装箱 equals 逐位比较才是「原值未被格式化改动」的判据。
        assertEquals(144.00002f, hz.value!!)
    }
}
