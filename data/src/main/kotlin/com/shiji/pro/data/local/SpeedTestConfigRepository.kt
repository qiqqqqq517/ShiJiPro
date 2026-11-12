package com.shiji.pro.data.local

import com.shiji.pro.core.domain.SpeedTestConfigRepository
import com.shiji.pro.core.model.SpeedTestConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 测速端点配置仓储（研究报告 §11-B4 机制落地，2026-11-12）。
 *
 * ⚠️ 诚实边界：与其它仓储一致，**内存存储**（进程重启不保留），
 * 设置页应显示「内存（未持久化）」，不声称已落盘。
 * 不内置任何第三方节点库：端点完全由用户录入（自建优先），合规且体积为零。
 */
class InMemorySpeedTestConfigRepository : SpeedTestConfigRepository {

    private val _config = MutableStateFlow(SpeedTestConfig())

    override fun observeConfig(): Flow<SpeedTestConfig> = _config.asStateFlow()

    override suspend fun updateConfig(config: SpeedTestConfig) {
        _config.value = config
    }
}
