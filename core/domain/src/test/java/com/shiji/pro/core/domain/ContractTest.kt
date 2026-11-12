package com.shiji.pro.core.domain

import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.SamplingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 领域契约不变式单测（P2）。 */
class ContractTest {

    @Test
    fun `SamplingPolicy 保证最小间隔 不会被配成 0 导致忙等`() {
        val config = SamplingConfig(
            cellularIntervalMs = 0L,
            wifiScanIntervalMs = 0L,
            gnssIntervalMs = 0L,
            deviceIntervalMs = 0L,
        )
        assertTrue(SamplingPolicy.intervalFor(CollectorKey.CELLULAR, config) >= 1_000L)
        assertTrue(SamplingPolicy.intervalFor(CollectorKey.WIFI, config) >= 5_000L)
        assertTrue(SamplingPolicy.intervalFor(CollectorKey.GNSS, config) >= 500L)
    }

    @Test
    fun `暂停时不允许任何采集器发数据`() {
        val config = SamplingConfig(paused = true)
        CollectorKey.entries.forEach { key ->
            assertFalse(
                "$key 在暂停态不应发数据",
                SamplingPolicy.shouldEmit(key, config, nowMs = 100_000L, lastEmitMs = 0L),
            )
        }
    }

    @Test
    fun `未达间隔不发射 达到间隔才发射`() {
        val config = SamplingConfig(paused = false)
        assertFalse(SamplingPolicy.shouldEmit(CollectorKey.CELLULAR, config, 1_500L, 1_000L))
        assertTrue(SamplingPolicy.shouldEmit(CollectorKey.CELLULAR, config, 3_000L, 1_000L))
    }

    @Test
    fun `CapabilityState 不可用时必须带原因`() {
        val denied = CapabilityState(
            key = CollectorKey.CELLULAR,
            available = false,
            reason = "READ_PHONE_STATE 未授予",
            requiredPermission = "READ_PHONE_STATE",
        )
        assertFalse(denied.available)
        assertTrue(denied.reason!!.contains("未授予"))
    }

    @Test
    fun `AppState 空态不含任何伪造数值`() {
        val state = AppState.empty(1234L)
        assertEquals(1234L, state.timestampMs)
        assertTrue(state.cellularSlots.isEmpty())
        assertTrue(state.capabilities.isEmpty())
        assertTrue(state.cellularSlots[0] == null)
    }

    @Test
    fun `8 个采集器标识齐全`() {
        assertEquals(8, CollectorKey.entries.size)
    }
}
