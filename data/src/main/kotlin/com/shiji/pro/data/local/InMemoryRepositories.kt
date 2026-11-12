package com.shiji.pro.data.local

import com.shiji.pro.core.common.AppResult
import com.shiji.pro.core.common.CsvCodec
import com.shiji.pro.core.common.CsvColumns
import com.shiji.pro.core.common.Masking
import com.shiji.pro.core.model.AppSettings
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.DriveTestPoint
import com.shiji.pro.core.model.LogEntry
import com.shiji.pro.core.model.LogType
import com.shiji.pro.core.model.SamplingConfig
import com.shiji.pro.core.model.SpeedTestResult
import com.shiji.pro.core.domain.AppStateRepository
import com.shiji.pro.core.domain.DriveTestRepository
import com.shiji.pro.core.domain.LogRepository
import com.shiji.pro.core.domain.NmeaRepository
import com.shiji.pro.core.domain.SpeedTestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * P5 本地存储实现：进程内 StateFlow 版本（无 Room/KSP 依赖，避免 AGP 9 + Kotlin 2.4 的注解处理器兼容风险）。
 *
 * ⚠️ 诚实边界：当前为**内存存储**，进程重启后数据不保留。
 * 持久化留待后续接入 Room（设计稿 §8 的 Room 表结构已定义于领域模型），
 * 设置页「本地存储路径」当前应显示「内存（未持久化）」而非声称已落盘。
 */
class InMemoryAppStateRepository : AppStateRepository {
    private val _state = MutableStateFlow(AppState.empty(System.currentTimeMillis()))
    private val _config = MutableStateFlow(SamplingConfig())
    private val _settings = MutableStateFlow(AppSettings())

    override fun observeState(): Flow<AppState> = _state.asStateFlow()
    override suspend fun refreshAll(): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setPaused(paused: Boolean) {
        _state.update { it.copy(samplingPaused = paused) }
    }
    override fun observeSamplingConfig(): Flow<SamplingConfig> = _config.asStateFlow()
    override suspend fun updateSamplingConfig(config: SamplingConfig) { _config.value = config }
    override fun observeSettings(): Flow<AppSettings> = _settings.asStateFlow()
    override suspend fun updateSettings(settings: AppSettings) { _settings.value = settings }

    /** 供 SamplingScheduler 合并快照。 */
    fun publish(reducer: (AppState) -> AppState) { _state.update(reducer) }
}

class InMemoryLogRepository : LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override fun observeLogs(types: Set<LogType>?, fromMs: Long?): Flow<List<LogEntry>> =
        _logs.map { list ->
            list.filter { (types == null || it.type in types) && (fromMs == null || it.timestampMs >= fromMs) }
        }
    override suspend fun append(entry: LogEntry) {
        _logs.update { (it + entry).takeLast(10_000) }
    }
    override suspend fun clear(types: Set<LogType>?) {
        _logs.update { list -> if (types == null) emptyList() else list.filter { it.type !in types } }
    }
    override suspend fun exportCsv(types: Set<LogType>?, fromMs: Long?): AppResult<String> {
        val list = _logs.value.filter {
            (types == null || it.type in types) && (fromMs == null || it.timestampMs >= fromMs)
        }
        val csv = CsvCodec.encode(
            listOf("id", "timestampMs", "type", "slot", "maskedPayload", "note"),
            list.map { listOf(it.id, it.timestampMs.toString(), it.type.name, it.slot?.toString(), it.maskedPayload, it.note) },
        )
        return AppResult.Success(csv)
    }
}

class InMemorySpeedTestRepository : SpeedTestRepository {
    private val _history = MutableStateFlow<List<SpeedTestResult>>(emptyList())
    override fun observeHistory(): Flow<List<SpeedTestResult>> = _history.asStateFlow()
    override suspend fun save(result: SpeedTestResult) { _history.update { it + result } }
    override suspend fun exportCsv(): AppResult<String> {
        val csv = CsvCodec.encode(
            listOf("timestampMs", "endpoint", "pingMs", "jitterMs", "downMbps", "upMbps", "publicIp", "carrier", "region"),
            _history.value.map {
                listOf(
                    it.timestampMs.toString(), it.endpoint.label,
                    it.pingMs.displayText(), it.jitterMs.displayText(),
                    it.downMbps.displayText(), it.upMbps.displayText(),
                    it.publicIp.displayText(), it.carrier.displayText(), it.region.displayText(),
                )
            },
        )
        return AppResult.Success(csv)
    }
}

class InMemoryDriveTestRepository : DriveTestRepository {
    private val _points = MutableStateFlow<List<DriveTestPoint>>(emptyList())
    override fun observePoints(): Flow<List<DriveTestPoint>> = _points.asStateFlow()
    override suspend fun add(point: DriveTestPoint) { _points.update { it + point } }
    override suspend fun clear() { _points.value = emptyList() }

    /** 全列导出（行为不变）：列名与顺序仍以 [CsvColumns.DRIVE_TEST_ALL_COLUMNS] 为唯一真源。 */
    override suspend fun exportCsv(): AppResult<String> = exportCsvWith(CsvColumns.DRIVE_TEST_ALL_COLUMNS)

    /**
     * 按勾选列导出（研究报告 §11-A3「CSV 字段可选」）。
     *
     * 列集由 [CsvColumns.effective] 决定：并入必需列（`timestampMs` / `latitude` / `longitude`）、
     * 丢弃未知列名、按全量列顺序去重。单元格映射见 [cellOf]，与 [exportCsv] 的表头一一对应。
     */
    override suspend fun exportCsvWith(columns: List<String>): AppResult<String> {
        val effective = CsvColumns.effective(columns)
        val csv = CsvCodec.encode(
            effective,
            _points.value.map { p -> effective.map { cellOf(p, it) } },
        )
        return AppResult.Success(csv)
    }

    /**
     * 单列取值：列名 → `DriveTestPoint` 字段的显示文本。
     *
     * 三态口径沿用 `FieldValue.displayText()`：取不到一律 "N/A"，不导出 0 / -1 占位；
     * 列名集合由 [CsvColumns.effective] 保证只来自 [CsvColumns.DRIVE_TEST_ALL_COLUMNS]，
     * `else` 分支仅为防御（导出空串而非崩溃）。
     */
    private fun cellOf(p: DriveTestPoint, column: String): String = when (column) {
        "id" -> p.id
        "timestampMs" -> p.timestampMs.toString()
        "latitude" -> p.latitude.toString()
        "longitude" -> p.longitude.toString()
        "slot" -> p.slotIndex?.toString() ?: ""
        "rsrp" -> p.rsrp.displayText()
        "rsrq" -> p.rsrq.displayText()
        "sinr" -> p.sinr.displayText()
        "band" -> p.band.displayText()
        "earfcn" -> p.earfcn.displayText()
        "wifiRssi" -> p.wifiRssi.displayText()
        "mode" -> p.mode.name
        "note" -> p.note ?: ""
        else -> ""
    }
}

class InMemoryNmeaRepository : NmeaRepository {
    private val _sentences = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    override fun observeSentences(): Flow<List<Pair<Long, String>>> = _sentences.asStateFlow()
    override suspend fun append(timestampMs: Long, sentence: String) {
        _sentences.update { (it + (timestampMs to sentence)).takeLast(50_000) }
    }
    override suspend fun clear() { _sentences.value = emptyList() }
    override suspend fun exportText(): AppResult<String> {
        val text = _sentences.value.joinToString("\n") { (t, s) -> "$t,$s" }
        return AppResult.Success(text)
    }
}

/** 敏感字段在写入日志前统一脱敏（默认路径）。 */
fun LogEntry.withMaskedPayload(): LogEntry = copy(maskedPayload = Masking.mask(maskedPayload))
