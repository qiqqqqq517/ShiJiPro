package com.shiji.pro.core.common

/**
 * 路测 CSV 列常量（研究报告 §11-A3「CSV 字段可选」的冻结契约，2026-11-12）。
 *
 * 列名与 `InMemoryDriveTestRepository.exportCsv()` 的表头一一对应；
 * 「CSV 字段可选」= 用户从 [DRIVE_TEST_ALL_COLUMNS] 里勾选子集，
 * 过滤实现由 `:data` 的仓储覆盖 `DriveTestRepository.exportCsvWith(columns)` 完成。
 * 约束：至少保留 `timestampMs` 与 `latitude`/`longitude`（无时间无坐标的行不可复现）。
 */
object CsvColumns {

    /** 全量列（顺序即导出顺序）。 */
    val DRIVE_TEST_ALL_COLUMNS: List<String> = listOf(
        "id", "timestampMs", "latitude", "longitude", "slot",
        "rsrp", "rsrq", "sinr", "band", "earfcn", "wifiRssi", "mode", "note",
    )

    /** 不可剔除的最小列集。 */
    val REQUIRED_COLUMNS: Set<String> = setOf("timestampMs", "latitude", "longitude")

    /** 勾选列 → 有效导出列：空集 = 全量列；否则并入必需列、按全量顺序过滤。 */
    fun effective(selected: List<String>): List<String> {
        if (selected.isEmpty()) return DRIVE_TEST_ALL_COLUMNS
        val set = selected.toSet() + REQUIRED_COLUMNS
        return DRIVE_TEST_ALL_COLUMNS.filter { it in set }
    }
}
