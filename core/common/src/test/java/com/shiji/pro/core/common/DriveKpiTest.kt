package com.shiji.pro.core.common

import com.shiji.pro.core.model.AppSettings
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.QualityWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DriveKpi] 单测（研究报告 §11-B6「信号权重可配置 → KPI」，2026-11-12）。
 *
 * 钉死五件事，防止后续改动把口径漂移：
 * 1. 默认权重（RSRP 0.5 / RSRQ 0.3 / SINR 0.2）下三分量典型值的加权结果；
 * 2. 全 0 / 负权重回落 DEFAULT（[QualityWeights.normalized] 已保证，这里做端到端回归）；
 * 3. 缺失分量只按**有效分量**重归一（缺分量不把分压低）；
 * 4. 空点集 / 全 N/A 点集 → null（UI 必须显示 N/A + 原因，而不是 0 分）；
 * 5. 归一化区间端点 clamp（-140 → 0、-80 → 1，区间外不外推）。
 *
 * 期望值全部由映射式手算：RSRP `(v+140)/60`、RSRQ `(v+20)/10`、SINR `(v+10)/40`。
 */
class DriveKpiTest {

    /** 无有效读数（UNAVAILABLE，value 恒为 null）。 */
    private fun na(): FieldValue<Int> = FieldValue.unavailable("单测：该分量无有效读数")

    private fun measured(v: Int): FieldValue<Int> = FieldValue.measured(v)

    private fun point(
        rsrp: FieldValue<Int> = na(),
        rsrq: FieldValue<Int> = na(),
        sinr: FieldValue<Int> = na(),
    ): KpiPoint = KpiPoint(rsrp, rsrq, sinr)

    // ---------------------------------------------------------------- 1. 默认权重

    @Test
    fun `默认权重三分量典型值按加权求和`() {
        // rsrp -90 → (50/60)；rsrq -10 → 1.0；sinr 20 → 0.75
        // 0.5*(50/60) + 0.3*1.0 + 0.2*0.75 = 0.4166667 + 0.3 + 0.15 = 0.8666667
        val p = point(measured(-90), measured(-10), measured(20))
        val score = DriveKpi.score(listOf(p), QualityWeights.DEFAULT)
        assertEquals(0.8666666666666667, score!!, 1e-9)
        assertTrue("评分必须落在 0..1", score in 0.0..1.0)
    }

    // ------------------------------------------------------- 2. 非法权重回落 DEFAULT

    @Test
    fun `全零权重回落默认权重`() {
        val p = point(measured(-90), measured(-10), measured(20))
        val zero = DriveKpi.score(listOf(p), QualityWeights(0.0, 0.0, 0.0))
        val def = DriveKpi.score(listOf(p), QualityWeights.DEFAULT)
        assertEquals(def!!, zero!!, 1e-12)
        assertEquals(0.8666666666666667, zero, 1e-9)
    }

    @Test
    fun `负权重回落默认权重`() {
        val p = point(measured(-90), measured(-10), measured(20))
        val negative = DriveKpi.score(listOf(p), QualityWeights(-1.0, -1.0, -1.0))
        assertEquals(0.8666666666666667, negative!!, 1e-9)
    }

    // ------------------------------------------------------ 3. 缺失分量按有效权重重归一

    @Test
    fun `仅 RSRP 有值时权重重归一为 1`() {
        // 只剩 RSRP 分量：0.5*(50/60) / 0.5 = 50/60
        val p = point(rsrp = measured(-90))
        assertEquals(0.8333333333333334, DriveKpi.score(listOf(p), QualityWeights.DEFAULT)!!, 1e-9)
    }

    @Test
    fun `缺 RSRQ 时按 RSRP 与 SINR 的有效权重重归一`() {
        // rsrp -90 → 0.8333333（权重 0.5），sinr 20 → 0.75（权重 0.2），rsrq N/A
        // (0.5*0.8333333 + 0.2*0.75) / 0.7 = 0.5666667 / 0.7 = 0.8095238
        val p = point(rsrp = measured(-90), sinr = measured(20))
        assertEquals(0.8095238095238096, DriveKpi.score(listOf(p), QualityWeights.DEFAULT)!!, 1e-9)
    }

    @Test
    fun `零权重分量不参与评分`() {
        // 权重 (1,0,0) 归一后仍是 (1,0,0)：只有 RSRP 有效
        val p = point(measured(-90), measured(-10), measured(20))
        val score = DriveKpi.score(listOf(p), QualityWeights(1.0, 0.0, 0.0))
        assertEquals(0.8333333333333334, score!!, 1e-9)
    }

    @Test
    fun `权重按相对比例归一而非要求和为 1`() {
        // (2,2,0) 与 (0.5,0.5,0) 是同一比例 → 同分：0.5*0.8333333 + 0.5*0.5 = 0.6666667
        val p = point(measured(-90), measured(-15), measured(20))
        val a = DriveKpi.score(listOf(p), QualityWeights(2.0, 2.0, 0.0))!!
        val b = DriveKpi.score(listOf(p), QualityWeights(0.5, 0.5, 0.0))!!
        assertEquals(0.6666666666666667, a, 1e-9)
        assertEquals(b, a, 1e-12)
    }

    // ---------------------------------------------------------------- 4. 空 / 全 N/A

    @Test
    fun `空点集返回 null`() {
        assertNull(DriveKpi.score(emptyList(), QualityWeights.DEFAULT))
    }

    @Test
    fun `所有分量都缺失的点集返回 null`() {
        val points = listOf(point(), point(), point())
        assertNull(DriveKpi.score(points, QualityWeights.DEFAULT))
    }

    @Test
    fun `无效点不计入均值`() {
        // 点 A 只有 rsrp -80（=1.0），点 B 全 N/A → 均值仍为 1.0
        val points = listOf(point(rsrp = measured(-80)), point())
        assertEquals(1.0, DriveKpi.score(points, QualityWeights.DEFAULT)!!, 1e-12)
    }

    // ---------------------------------------------------------------- 5. 区间 clamp

    @Test
    fun `RSRP 区间端点 clamp 到 0 与 1`() {
        // 单分量（只有 RSRP 有值）→ 权重重归一为 1，分数即归一值本身
        assertEquals(0.0, DriveKpi.score(listOf(point(rsrp = measured(-140))), QualityWeights.DEFAULT)!!, 1e-12)
        assertEquals(1.0, DriveKpi.score(listOf(point(rsrp = measured(-80))), QualityWeights.DEFAULT)!!, 1e-12)
        // 区间外不外推
        assertEquals(0.0, DriveKpi.score(listOf(point(rsrp = measured(-200))), QualityWeights.DEFAULT)!!, 1e-12)
        assertEquals(1.0, DriveKpi.score(listOf(point(rsrp = measured(-50))), QualityWeights.DEFAULT)!!, 1e-12)
    }

    @Test
    fun `RSRQ 与 SINR 区间端点同样 clamp`() {
        assertEquals(0.0, DriveKpi.score(listOf(point(rsrq = measured(-20))), QualityWeights.DEFAULT)!!, 1e-12)
        assertEquals(1.0, DriveKpi.score(listOf(point(rsrq = measured(-10))), QualityWeights.DEFAULT)!!, 1e-12)
        assertEquals(0.0, DriveKpi.score(listOf(point(rsrq = measured(-30))), QualityWeights.DEFAULT)!!, 1e-12)

        assertEquals(0.0, DriveKpi.score(listOf(point(sinr = measured(-10))), QualityWeights.DEFAULT)!!, 1e-12)
        assertEquals(1.0, DriveKpi.score(listOf(point(sinr = measured(30))), QualityWeights.DEFAULT)!!, 1e-12)
        assertEquals(1.0, DriveKpi.score(listOf(point(sinr = measured(100))), QualityWeights.DEFAULT)!!, 1e-12)
    }

    // ---------------------------------------------------------------- 6. 多点均值

    @Test
    fun `多点取算术均值`() {
        // 两个 RSRP 单分量点：0.0 与 1.0 → 0.5
        val points = listOf(point(rsrp = measured(-140)), point(rsrp = measured(-80)))
        assertEquals(0.5, DriveKpi.score(points, QualityWeights.DEFAULT)!!, 1e-12)
    }

    @Test
    fun `评分始终落在 0 到 1 之间`() {
        val points = listOf(
            point(measured(-200), measured(-30), measured(100)),
            point(measured(-50), measured(0), measured(-40)),
            point(measured(-110), measured(-15), measured(10)),
        )
        val score = DriveKpi.score(points, QualityWeights.DEFAULT)!!
        assertTrue("实际 $score", score in 0.0..1.0)
    }
}

/**
 * CSV 列集契约单测（研究报告 §11-A3「CSV 字段可选」）。
 *
 * 放在本文件的原因：`:data` 的 `InMemoryDriveTestRepository.exportCsvWith(columns)` 的
 * 列过滤 / 必需列并入语义**完全由 [CsvColumns.effective] 决定**（仓储只做「按列名取单元格」），
 * 而本轮分工里可写的测试文件仅此一个；`CsvColumns` 属 `:core:common` 与 [DriveKpi] 同模块，归此合理。
 * 契约裁定（lead，2026-11-12）：空列表 = 全量列（与设置层 null 同义）；`:data` 的仓储级
 * 往返测试不在本任务可写范围（已上报 lead）。
 */
class DriveCsvColumnSelectionTest {

    @Test
    fun `全量列共 13 列`() {
        assertEquals(13, CsvColumns.DRIVE_TEST_ALL_COLUMNS.size)
        assertEquals("id", CsvColumns.DRIVE_TEST_ALL_COLUMNS.first())
        assertEquals("note", CsvColumns.DRIVE_TEST_ALL_COLUMNS.last())
    }

    /**
     * 契约裁定（lead，2026-11-12）：KDoc 与实现以**此测试为准**——
     * `effective` 现实现为「空集 = 全量列」（与 `AppSettings.driveTestCsvColumns == null` 的
     * 全列语义一致，空列表不再有第二种含义），实现已同步补 `if (selected.isEmpty()) return DRIVE_TEST_ALL_COLUMNS`。
     * 调用方约定：null 与空列表都表示全列；勾选子集时必需列强制并入。
     */
    @Test
    fun `空勾选等于全量列`() {
        assertEquals(CsvColumns.DRIVE_TEST_ALL_COLUMNS, CsvColumns.effective(emptyList()))
        assertEquals(13, CsvColumns.effective(emptyList()).size)
        // 显式传全量列同样是 13 列
        assertEquals(13, CsvColumns.effective(CsvColumns.DRIVE_TEST_ALL_COLUMNS).size)
    }

    @Test
    fun `未自定义由设置层的 null 表达`() {
        // 「null = 全部列」是 CSV 列可选的唯一真源语义（与上面 effective 的空集行为必须区分开）
        assertNull(AppSettings().driveTestCsvColumns)
    }

    @Test
    fun `勾选全量列时顺序不变`() {
        val reversed = CsvColumns.DRIVE_TEST_ALL_COLUMNS.reversed()
        assertEquals(CsvColumns.DRIVE_TEST_ALL_COLUMNS, CsvColumns.effective(reversed))
    }

    @Test
    fun `必需列在最小勾选下被并入`() {
        val effective = CsvColumns.effective(listOf("rsrp"))
        assertEquals(listOf("timestampMs", "latitude", "longitude", "rsrp"), effective)
        assertTrue(effective.containsAll(CsvColumns.REQUIRED_COLUMNS))
    }

    @Test
    fun `结果顺序恒按全量列顺序而非勾选顺序`() {
        val effective = CsvColumns.effective(listOf("sinr", "rsrp"))
        assertEquals(listOf("timestampMs", "latitude", "longitude", "rsrp", "sinr"), effective)
    }

    @Test
    fun `未知列名被丢弃`() {
        val effective = CsvColumns.effective(listOf("rsrp", "notAColumn", "29"))
        assertEquals(listOf("timestampMs", "latitude", "longitude", "rsrp"), effective)
    }

    @Test
    fun `仅必需列时导出列即最小三列`() {
        assertEquals(
            listOf("timestampMs", "latitude", "longitude"),
            CsvColumns.effective(CsvColumns.REQUIRED_COLUMNS.toList()),
        )
    }
}
