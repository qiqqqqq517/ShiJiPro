package com.shiji.pro.core.domain

import com.shiji.pro.core.common.AppResult
import com.shiji.pro.core.model.AppSettings
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.DriveTestPoint
import com.shiji.pro.core.model.LogEntry
import com.shiji.pro.core.model.LogType
import com.shiji.pro.core.model.SamplingConfig
import com.shiji.pro.core.model.SpeedTestConfig
import com.shiji.pro.core.model.SpeedTestResult
import kotlinx.coroutines.flow.Flow

/**
 * 仓储接口（定义在 domain，实现在 :data）。
 * AppStateRepository 是**唯一数据源**：UI 只 observeState()，不订阅各 Collector 的 Flow。
 */

interface AppStateRepository {
    /** 单一数据源状态流。 */
    fun observeState(): Flow<AppState>

    /** 立即刷新一次所有可用采集器（用户点「刷新」时调用）。 */
    suspend fun refreshAll(): AppResult<Unit>

    /** 暂停 / 恢复后台采集（§10.6 可暂停、可配置刷新间隔）。 */
    suspend fun setPaused(paused: Boolean)

    fun observeSamplingConfig(): Flow<SamplingConfig>
    suspend fun updateSamplingConfig(config: SamplingConfig)

    fun observeSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
}

/** 统一日志中心（§8）。 */
interface LogRepository {
    fun observeLogs(types: Set<LogType>? = null, fromMs: Long? = null): Flow<List<LogEntry>>
    suspend fun append(entry: LogEntry)
    suspend fun clear(types: Set<LogType>? = null)
    suspend fun exportCsv(types: Set<LogType>?, fromMs: Long?): AppResult<String>
}

/** 测速历史（P9）。 */
interface SpeedTestRepository {
    fun observeHistory(): Flow<List<SpeedTestResult>>
    suspend fun save(result: SpeedTestResult)
    suspend fun exportCsv(): AppResult<String>
}

/** 路测点（P9）。 */
interface DriveTestRepository {
    fun observePoints(): Flow<List<DriveTestPoint>>
    suspend fun add(point: DriveTestPoint)
    suspend fun clear()
    suspend fun exportCsv(): AppResult<String>

    /**
     * 按指定列导出（研究报告 §11-A3「CSV 字段可选」）。
     * 默认实现退回全列导出，具体列过滤由 :data 实现覆盖；列名常量见 `core:common/CsvColumns`。
     */
    suspend fun exportCsvWith(columns: List<String>): AppResult<String> = exportCsv()
}

/** 测速端点配置（研究报告 §11-B4：节点库 + 就近选点的机制落地；数据由用户录入）。 */
interface SpeedTestConfigRepository {
    fun observeConfig(): Flow<SpeedTestConfig>
    suspend fun updateConfig(config: SpeedTestConfig)
}

/** NMEA 原始日志（P8）。 */
interface NmeaRepository {
    fun observeSentences(): Flow<List<Pair<Long, String>>>
    suspend fun append(timestampMs: Long, sentence: String)
    suspend fun clear()
    suspend fun exportText(): AppResult<String>
}
