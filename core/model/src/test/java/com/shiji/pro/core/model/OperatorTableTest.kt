package com.shiji.pro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 运营商归属表单测（研究报告 §11-A1）。
 *
 * 三条硬判据：
 * 1. 同一制式内任意两条区间**无重叠**（重叠会导致同一频点归属二义）；
 * 2. 边界命中正确：区间含两端（contains 为闭区间），上界+1 / 下界-1 必须落空；
 * 3. 跨制式隔离：LTE 频点查 NR 表必为 null，反之亦然。
 */
class OperatorTableTest {

    @Test
    fun `LTE 表非空且每条都是 LTE 制式`() {
        assertTrue("LTE_RANGES 不能为空（否则 A1 空转）", OperatorTable.LTE_RANGES.isNotEmpty())
        assertTrue(OperatorTable.LTE_RANGES.all { it.tech == RadioTech.LTE })
        assertTrue(OperatorTable.LTE_RANGES.all { it.earfcnLow <= it.earfcnHigh })
        assertTrue(OperatorTable.LTE_RANGES.all { it.operator.isNotBlank() })
        assertFalse("isEmpty() 在 LTE 非空时应为 false", OperatorTable.isEmpty())
    }

    @Test
    fun `同一制式内任意两区间无重叠`() {
        fun assertNoOverlap(ranges: List<OperatorRange>, label: String) {
            val sorted = ranges.sortedBy { it.earfcnLow }
            for (i in 0 until sorted.size - 1) {
                assertTrue(
                    "$label 重叠：${sorted[i]} 与 ${sorted[i + 1]}",
                    sorted[i].earfcnHigh < sorted[i + 1].earfcnLow,
                )
            }
        }
        assertNoOverlap(OperatorTable.LTE_RANGES, "LTE")
        assertNoOverlap(OperatorTable.NR_RANGES, "NR")
    }

    @Test
    fun `已知中国频点命中正确运营商`() {
        // 联通 B3（1830–1860 DL → 1450–1749）
        assertEquals("中国联通", OperatorTable.forLteEarfcn(1650)?.operator)
        assertEquals(3, OperatorTable.forLteEarfcn(1650)?.band)
        // 移动 B3（1805–1830 → 1200–1449）
        assertEquals("中国移动", OperatorTable.forLteEarfcn(1300)?.operator)
        // 电信 B3（1860–1880 → 1750–1949）
        assertEquals("中国电信", OperatorTable.forLteEarfcn(1800)?.operator)
        // 移动 B34/B39/B41
        assertEquals("中国移动", OperatorTable.forLteEarfcn(36250)?.operator)
        assertEquals("中国移动", OperatorTable.forLteEarfcn(38300)?.operator)
        assertEquals("中国移动", OperatorTable.forLteEarfcn(40500)?.operator)
        // 联通/电信 B40 与 移动 B40 相邻但不重叠
        assertEquals("中国联通", OperatorTable.forLteEarfcn(38700)?.operator)
        assertEquals("中国移动", OperatorTable.forLteEarfcn(39000)?.operator)
        assertEquals("中国电信", OperatorTable.forLteEarfcn(39400)?.operator)
    }

    @Test
    fun `闭区间边界命中且越界落空`() {
        // 用 B5 电信（2410–2509，两侧无相邻运营商段）验「闭区间含端点、越界落空」
        val lonely = OperatorTable.forLteEarfcn(2450)!!.range
        assertEquals("中国电信", OperatorTable.forLteEarfcn(lonely.earfcnLow)?.operator)
        assertEquals("中国电信", OperatorTable.forLteEarfcn(lonely.earfcnHigh)?.operator)
        assertNull(OperatorTable.forLteEarfcn(lonely.earfcnLow - 1))
        assertNull(OperatorTable.forLteEarfcn(lonely.earfcnHigh + 1))

        // B3 三段严丝合缝：联通段的两端命中联通，而 low-1 属移动、high+1 属电信（相邻不重叠）
        val uni = OperatorTable.forLteEarfcn(1650)!!.range
        assertEquals("中国联通", OperatorTable.forLteEarfcn(uni.earfcnLow)?.operator)
        assertEquals("中国联通", OperatorTable.forLteEarfcn(uni.earfcnHigh)?.operator)
        assertEquals("中国移动", OperatorTable.forLteEarfcn(uni.earfcnLow - 1)?.operator)
        assertEquals("中国电信", OperatorTable.forLteEarfcn(uni.earfcnHigh + 1)?.operator)
    }

    @Test
    fun `未收录频点返回 null 而不是猜`() {
        // B41 2515–2635 与联通/电信的再划分来源不一致 → 未收录（40440 之前 / 41039 之后都该 null）
        assertNull(OperatorTable.forLteEarfcn(39700))
        assertNull(OperatorTable.forLteEarfcn(41100))
        // 负数与超范围
        assertNull(OperatorTable.forLteEarfcn(-1))
        assertNull(OperatorTable.forLteEarfcn(999999))
    }

    @Test
    fun `跨制式隔离`() {
        // LTE 频点查 NR 表 → null（NR 本轮未收录，恒 null）
        assertNull(OperatorTable.forNrArfcn(1650))
        assertNull(OperatorTable.forNrArfcn(503000))
        // LTE 频点查 LTE 表 → 命中
        assertTrue(OperatorTable.forLteEarfcn(1650) != null)
    }

    @Test
    fun `命中结果与区间信息一致`() {
        val m = OperatorTable.forLteEarfcn(1650)!!
        assertEquals(m.operator, m.range.operator)
        assertEquals(m.band, m.range.band)
        assertTrue(m.range.sourceNote.isNotBlank())
    }
}
