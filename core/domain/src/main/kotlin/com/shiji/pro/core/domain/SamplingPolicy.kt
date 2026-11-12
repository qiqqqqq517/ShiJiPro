package com.shiji.pro.core.domain

import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.SamplingConfig

/**
 * 采样策略（设计稿 §10.6）：按采集器类型给出刷新间隔，并保证下限，避免高频耗电。
 * 纯 Kotlin，可在 JVM 单测验证。
 */
object SamplingPolicy {

    /** 各采集器允许的最小间隔（毫秒），防止用户配到 0 导致忙等。 */
    private val MIN_INTERVAL_MS: Map<CollectorKey, Long> = mapOf(
        CollectorKey.CELLULAR to 1_000L,
        CollectorKey.WIFI to 5_000L,      // 且受系统 startScan 节流约束
        CollectorKey.GNSS to 500L,
        CollectorKey.DEVICE to 5_000L,
        CollectorKey.BATTERY to 5_000L,
        CollectorKey.BLUETOOTH to 5_000L,
        CollectorKey.TRAFFIC to 5_000L,
        CollectorKey.SPEED_TEST to 1_000L,
    )

    fun intervalFor(key: CollectorKey, config: SamplingConfig): Long {
        val configured = when (key) {
            CollectorKey.CELLULAR, CollectorKey.BATTERY -> config.cellularIntervalMs
            CollectorKey.WIFI, CollectorKey.BLUETOOTH, CollectorKey.TRAFFIC -> config.wifiScanIntervalMs
            CollectorKey.GNSS -> config.gnssIntervalMs
            CollectorKey.DEVICE -> config.deviceIntervalMs
            CollectorKey.SPEED_TEST -> 1_000L
        }
        return maxOf(configured, MIN_INTERVAL_MS.getValue(key))
    }

    /** 暂停时不允许任何采集（除用户主动 refreshAll，由实现方单独放行）。 */
    fun shouldEmit(key: CollectorKey, config: SamplingConfig, nowMs: Long, lastEmitMs: Long): Boolean {
        if (config.paused) return false
        return nowMs - lastEmitMs >= intervalFor(key, config)
    }
}
