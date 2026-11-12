package com.shiji.pro.core.domain

import com.shiji.pro.core.common.AppResult
import com.shiji.pro.core.model.AppSettings
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.DriveTestPoint
import com.shiji.pro.core.model.LogEntry
import com.shiji.pro.core.model.LogType
import com.shiji.pro.core.model.SamplingConfig
import com.shiji.pro.core.model.SpeedTestResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * UseCase 集（设计稿 §分层架构）：把仓储访问与领域规则封装成单一职责的可调用对象。
 * ViewModel 只依赖这些 UseCase，不直接依赖 Repository 或 Collector。
 */

class ObserveAppStateUseCase(private val repo: AppStateRepository) {
    operator fun invoke(): Flow<AppState> = repo.observeState()
}

class RefreshAllUseCase(private val repo: AppStateRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repo.refreshAll()
}

class ManageSamplingUseCase(private val repo: AppStateRepository) {
    suspend fun pause() = repo.setPaused(true)
    suspend fun resume() = repo.setPaused(false)
    fun observeConfig(): Flow<SamplingConfig> = repo.observeSamplingConfig()
    suspend fun updateConfig(config: SamplingConfig) = repo.updateSamplingConfig(config)
}

class ManageSettingsUseCase(private val repo: AppStateRepository) {
    fun observe(): Flow<AppSettings> = repo.observeSettings()
    suspend fun update(settings: AppSettings) = repo.updateSettings(settings)
}

/** 观察某采集器的能力降级状态，供 UI 的 PermissionGate 使用。 */
class ObserveCapabilityUseCase(private val repo: AppStateRepository) {
    operator fun invoke(key: CollectorKey): Flow<CapabilityState?> =
        repo.observeState().map { it.capabilities[key] }
}

class LogCenterUseCase(private val repo: LogRepository) {
    fun observe(types: Set<LogType>? = null, fromMs: Long? = null): Flow<List<LogEntry>> =
        repo.observeLogs(types, fromMs)
    suspend fun append(entry: LogEntry) = repo.append(entry)
    suspend fun clear(types: Set<LogType>? = null) = repo.clear(types)
    suspend fun exportCsv(types: Set<LogType>?, fromMs: Long?): AppResult<String> =
        repo.exportCsv(types, fromMs)
}

class SpeedTestHistoryUseCase(private val repo: SpeedTestRepository) {
    fun observe(): Flow<List<SpeedTestResult>> = repo.observeHistory()
    suspend fun save(result: SpeedTestResult) = repo.save(result)
    suspend fun exportCsv(): AppResult<String> = repo.exportCsv()
}

class DriveTestUseCase(private val repo: DriveTestRepository) {
    fun observe(): Flow<List<DriveTestPoint>> = repo.observePoints()
    suspend fun add(point: DriveTestPoint) = repo.add(point)
    suspend fun clear() = repo.clear()
    suspend fun exportCsv(): AppResult<String> = repo.exportCsv()
}

class NmeaUseCase(private val repo: NmeaRepository) {
    fun observe(): Flow<List<Pair<Long, String>>> = repo.observeSentences()
    suspend fun append(timestampMs: Long, sentence: String) = repo.append(timestampMs, sentence)
    suspend fun clear() = repo.clear()
    suspend fun exportText(): AppResult<String> = repo.exportText()
}
