package com.shiji.pro.core.common

import com.shiji.pro.core.model.SignalGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSV / 脱敏 / 采样 / 评级 单测（P1）。 */
class CommonUtilsTest {

    // ---- CSV RFC4180 往返 ----

    @Test
    fun `CSV 含逗号引号与换行的字段可往返`() {
        val rows = listOf(
            listOf("2026-11-12T02:00:00", "SSID,含逗号", "N/A", null),
            listOf("a\"引号", "line1\nline2", "value", ""),
        )
        val csv = CsvCodec.encode(listOf("time", "ssid", "col3", "col4"), rows)
        val back = CsvCodec.decode(csv)
        assertEquals(3, back.size) // header + 2 rows
        assertEquals("SSID,含逗号", back[1][1])
        assertEquals("a\"引号", back[2][0])
        assertEquals("line1\nline2", back[2][1])
        assertEquals("", back[1][3])  // null 输出为空串，回读仍为空串
    }

    @Test
    fun `CSV 表头不被引号误伤`() {
        val csv = CsvCodec.encodeRow(listOf("time", "rsrp", "sinr"))
        assertEquals("time,rsrp,sinr", csv)
    }

    // ---- 脱敏 ----

    @Test
    fun `IMSI 脱敏保留末四位`() {
        val masked = Masking.mask("460001234567890")
        assertTrue(masked.startsWith("•"))
        assertTrue(masked.endsWith("7890"))
        assertTrue(!masked.contains("460001234567890"))
    }

    @Test
    fun `短串全遮蔽`() {
        assertEquals("•••", Masking.mask("abc"))
        assertEquals("", Masking.mask(null))
    }

    @Test
    fun `MAC 脱敏保留厂商前缀`() {
        assertEquals("A1:B2:C3:••:••:••", Masking.maskMac("A1:B2:C3:D4:E5:F6"))
    }

    // ---- 采样 / 统计 ----

    @Test
    fun `窗口裁剪只保留窗口内点`() {
        val pts = listOf(
            Sampling.Point(0L, 1.0),
            Sampling.Point(30_000L, 2.0),
            Sampling.Point(90_000L, 3.0),
        )
        val kept = Sampling.withinWindow(pts, 100_000L, Sampling.Window.SEC_20)
        assertEquals(1, kept.size)
        assertEquals(3.0, kept[0].value, 0.0)
    }

    @Test
    fun `降频保留首尾且不超过上限`() {
        val pts = (0 until 1000).map { Sampling.Point(it.toLong(), it.toDouble()) }
        val out = Sampling.downsample(pts, 100)
        assertEquals(100, out.size)
        assertEquals(0.0, out.first().value, 0.0)
        assertEquals(999.0, out.last().value, 0.0)
    }

    @Test
    fun `环形缓冲容量有界`() {
        val buf = Sampling.RingBuffer(3)
        repeat(10) { buf.add(Sampling.Point(it.toLong(), it.toDouble())) }
        assertEquals(3, buf.size)
        assertEquals(7L, buf.snapshot().first().timestampMs)
    }

    @Test
    fun `jitter 为相邻差绝对值均值`() {
        assertEquals(2.0, Stats.jitter(listOf(10.0, 12.0, 10.0))!!, 1e-9)
        assertEquals(null, Stats.jitter(listOf(10.0)))
    }

    // ---- 信号评级（估算值）----

    @Test
    fun `RSRP 阈值分级`() {
        assertEquals(SignalGrade.EXCELLENT, SignalQuality.gradeByRsrp(-75))
        assertEquals(SignalGrade.GOOD, SignalQuality.gradeByRsrp(-85))
        assertEquals(SignalGrade.FAIR, SignalQuality.gradeByRsrp(-100))
        assertEquals(SignalGrade.POOR, SignalQuality.gradeByRsrp(-110))
        assertEquals(SignalGrade.BAD, SignalQuality.gradeByRsrp(-125))
    }

    @Test
    fun `奇异值不参与评级 返回 UNKNOWN`() {
        assertEquals(SignalGrade.UNKNOWN, SignalQuality.gradeByRsrp(Int.MAX_VALUE))
    }

    @Test
    fun `信号评级与枚举一致`() {
        assertEquals(SignalGrade.GOOD, SignalQuality.gradeByWifiRssi(-55))
    }
}
