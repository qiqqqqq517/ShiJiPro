package com.shiji.pro.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IDW 覆盖栅格单测（研究报告 §11-B7）。
 *
 * 判据：空输入不产图、单点退化为常量面、近点权重更大、参考点处取该点值、
 * 包围盒正确；数值断言用关系与容差，不钉死浮点细节。
 */
class IdwGridTest {

    @Test
    fun `无参考点返回空栅格与空包围盒`() {
        assertTrue(IdwGrid.interpolate(emptyList()).isEmpty())
        assertNull(IdwGrid.bounds(emptyList()))
    }

    @Test
    fun `单个参考点退化为常量面`() {
        val s = listOf(IdwGrid.Sample(39.9, 116.4, -90.0))
        val cells = IdwGrid.interpolate(s, 5, 5)
        assertEquals(25, cells.size)
        cells.forEach { assertEquals(-90.0, it.value, 1e-6) }
        val b = IdwGrid.bounds(s)!!
        assertEquals(39.9, b.latMin, 1e-9)
        assertEquals(39.9, b.latMax, 1e-9)
    }

    @Test
    fun `靠近强信号点的插值高于靠近弱信号点`() {
        // 南北两点：北 -70（好）、南 -120（差）
        val samples = listOf(
            IdwGrid.Sample(40.001, 116.4, -70.0), // 北
            IdwGrid.Sample(40.000, 116.4, -120.0), // 南
        )
        val north = IdwGrid.valueAt(samples, 40.0009, 116.4)
        val south = IdwGrid.valueAt(samples, 40.0001, 116.4)
        assertTrue("北侧插值应更高: $north vs $south", north > south)
        // 两点都应在两观测值之间（IDW 是加权平均）
        assertTrue(north in -120.0..-70.0)
        assertTrue(south in -120.0..-70.0)
    }

    @Test
    fun `参考点处返回该点观测值`() {
        val samples = listOf(
            IdwGrid.Sample(39.9, 116.4, -85.0),
            IdwGrid.Sample(40.1, 116.6, -105.0),
        )
        assertEquals(-85.0, IdwGrid.valueAt(samples, 39.9, 116.4), 1e-6)
        assertEquals(-105.0, IdwGrid.valueAt(samples, 40.1, 116.6), 1e-6)
    }

    @Test
    fun `栅格尺寸与归一化坐标范围正确`() {
        val samples = listOf(
            IdwGrid.Sample(39.0, 116.0, -80.0),
            IdwGrid.Sample(40.0, 117.0, -110.0),
        )
        val cells = IdwGrid.interpolate(samples, cols = 6, rows = 4)
        assertEquals(24, cells.size)
        assertEquals(0.0, cells.minOf { it.u }, 1e-9)
        assertEquals(1.0, cells.maxOf { it.u }, 1e-9)
        assertEquals(0.0, cells.minOf { it.v }, 1e-9)
        assertEquals(1.0, cells.maxOf { it.v }, 1e-9)
        assertNotNull(IdwGrid.bounds(samples))
    }

    @Test
    fun `幂参数越大越局部`() {
        val samples = listOf(
            IdwGrid.Sample(40.0, 116.0, -70.0),
            IdwGrid.Sample(40.01, 116.0, -120.0),
        )
        val p1 = IdwGrid.valueAt(samples, 40.009, 116.0, p = 1.0)
        val p4 = IdwGrid.valueAt(samples, 40.009, 116.0, p = 4.0)
        // 目标点靠近第二个（-120）样本：幂越大越贴近 -120（更小）
        assertTrue("p=4 应更贴近近点: $p4 vs $p1", p4 < p1)
    }
}
