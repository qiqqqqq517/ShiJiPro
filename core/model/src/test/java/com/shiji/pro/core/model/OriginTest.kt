package com.shiji.pro.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 领域模型层单测（P0）：三态标注枚举是跨模块共享契约，必须稳定。
 * P1 落地 FieldValue<T> 后在此文件补充三态包装与 N/A 不伪造数值的判据。
 */
class OriginTest {

    @Test
    fun `origin 恰好四种取值且名称稳定`() {
        val names = Origin.entries.map { it.name }
        assertEquals(listOf("MEASURED", "DERIVED", "ESTIMATED", "UNAVAILABLE"), names)
    }

    @Test
    fun `UNAVAILABLE 是唯一允许显示不可用占位的来源`() {
        // §10.4：取不到的字段一律 N/A，禁止用估算值伪装成实测值。
        assertEquals(Origin.UNAVAILABLE, Origin.valueOf("UNAVAILABLE"))
        assert(Origin.entries.count { it == Origin.UNAVAILABLE } == 1)
    }
}
