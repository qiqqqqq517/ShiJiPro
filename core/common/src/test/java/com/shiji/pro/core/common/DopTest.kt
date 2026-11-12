package com.shiji.pro.core.common

import com.shiji.pro.core.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * DOP 几何解算单测（研究报告 §11-A0）。
 *
 * 判据原则：DOP 取决于几何构型，断言用**关系与合理范围**，不钉死精确小数
 * （避免把实现里的浮点细节当契约）。
 */
class DopTest {

    private fun azEl(vararg pairs: Pair<Double, Double>): Pair<List<Double>, List<Double>> =
        pairs.map { it.first } to pairs.map { it.second }

    @Test
    fun `少于 4 颗参与定位返回 null`() {
        val (az, el) = azEl(0.0 to 45.0, 120.0 to 30.0, 240.0 to 60.0)
        assertNull(DopCalculator.compute(az, el))
        assertNull(DopCalculator.compute(emptyList(), emptyList()))
    }

    @Test
    fun `四颗不同仰角且方位分散的卫星给出有限且标注估算的 DOP`() {
        // 注意：4 颗「同一仰角」的卫星在几何上是退化的（4 行线性相关 → 矩阵奇异），
        // 真实可用构型必须仰角错开，这里刻意给出 25°–70° 的混合构型。
        val (az, el) = azEl(0.0 to 70.0, 95.0 to 40.0, 190.0 to 55.0, 285.0 to 25.0)
        val dop = DopCalculator.compute(az, el)
        assertNotNull("不同仰角的四星构型应可解算", dop)
        dop!!
        assertEquals(4, dop.usedSatellites)
        val h = dop.hdop.value!!
        val v = dop.vdop.value!!
        val p = dop.pdop.value!!
        assertTrue("HDOP=$h", h > 0.0 && h.isFinite())
        assertTrue("VDOP=$v", v > 0.0 && v.isFinite())
        assertTrue("PDOP=$p", p > 0.0 && p.isFinite())
        // PDOP² = HDOP² + VDOP²（同一 Q 矩阵推出，关系必须成立）
        assertTrue("PDOP 与 HDOP/VDOP 不自洽", abs(p * p - (h * h + v * v)) < 1e-9)
        // 几何良好时 DOP 应处于可用范围（< 10）
        assertTrue("HDOP 偏大: $h", h < 10.0)
        // 三态：估算值 + 估算声明
        assertEquals(Origin.ESTIMATED, dop.hdop.origin)
        assertEquals(Origin.ESTIMATED, dop.vdop.origin)
        assertEquals(Origin.ESTIMATED, dop.pdop.origin)
        assertTrue(dop.note?.contains("估算值") == true)
    }

    @Test
    fun `几何退化（四颗同向）返回 null`() {
        val (az, el) = azEl(45.0 to 45.0, 45.0 to 45.0, 45.0 to 45.0, 45.0 to 45.0)
        assertNull(DopCalculator.compute(az, el))
    }

    @Test
    fun `仰角接近地平与天顶混合时仍可解算`() {
        val (az, el) = azEl(10.0 to 5.0, 130.0 to 15.0, 250.0 to 80.0, 340.0 to 35.0)
        val dop = DopCalculator.compute(az, el)
        assertNotNull(dop)
        assertTrue(dop!!.hdop.value!! > 0.0)
        assertTrue(dop.pdop.value!! >= dop.hdop.value!!)
    }

    @Test
    fun `星数越多 DOP 不应变差`() {
        // 4 星：不同仰角、方位分散（避免同仰角的几何退化构型）
        val four = DopCalculator.compute(
            listOf(0.0, 100.0, 200.0, 290.0),
            listOf(70.0, 35.0, 60.0, 20.0),
        )!!
        // 在原 4 星基础上增加 2 颗低仰角卫星
        val six = DopCalculator.compute(
            listOf(0.0, 100.0, 200.0, 290.0, 45.0, 245.0),
            listOf(70.0, 35.0, 60.0, 20.0, 10.0, 12.0),
        )!!
        // GDOP/HDOP 对增广观测单调不增（协方差在正定意义下变小）
        assertTrue(
            "6 星 HDOP=${six.hdop.value} 反而大于 4 星 HDOP=${four.hdop.value}",
            six.hdop.value!! <= four.hdop.value!! + 1e-9,
        )
    }
}
