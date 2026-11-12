package com.shiji.pro.core.common

import com.shiji.pro.core.model.UserEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GeoMath 单测（§11-B4 验收：≥4 例，含跨 ±180°）。
 *
 * 口径：距离按 Haversine + 平均地球半径 6371008.8 m；容差按任务书给出的窗口，
 * 不追求与椭球（WGS-84 geodesic）逐米一致——就近选点只需要公里级正确。
 */
class GeoMathTest {

    private fun ep(id: String, latDeg: Double?, lonDeg: Double?) = UserEndpoint(
        id = id,
        label = id,
        baseUrl = "https://$id.example/",
        latDeg = latDeg,
        lonDeg = lonDeg,
    )

    @Test
    fun `北京到上海约 1067 公里`() {
        val d = GeoMath.haversineMeters(39.9042, 116.4074, 31.2304, 121.4737)
        // 1,067,000 m ± 15 km
        assertTrue("实际距离 $d m 超出 1,067,000 ± 15,000 窗口", d in 1_052_000.0..1_082_000.0)
    }

    @Test
    fun `同点距离为 0`() {
        val d = GeoMath.haversineMeters(39.9042, 116.4074, 39.9042, 116.4074)
        assertEquals(0.0, d, 1e-9)
    }

    @Test
    fun `距离对称`() {
        val ab = GeoMath.haversineMeters(39.9042, 116.4074, 31.2304, 121.4737)
        val ba = GeoMath.haversineMeters(31.2304, 121.4737, 39.9042, 116.4074)
        assertEquals(ab, ba, 1e-9)
    }

    @Test
    fun `跨 180 度经线赤道两点约 111 公里`() {
        // 赤道 179.5° ↔ -179.5°，经度差 1°（地理上相邻，不是绕地球 359°）
        val d = GeoMath.haversineMeters(0.0, 179.5, 0.0, -179.5)
        assertTrue("实际距离 $d m 超出 111,000 ± 2,000 窗口", d in 109_000.0..113_000.0)
    }

    @Test
    fun `就近选点忽略无位置端点`() {
        val endpoints = listOf(
            ep("a", null, null),          // 无位置：必须被忽略
            ep("b", 31.2304, 121.4737),   // 上海
            ep("c", 39.9042, 116.4074),   // 北京（离查询点更近）
        )
        val near = GeoMath.nearest(39.0, 116.0, endpoints)
        assertTrue("就近选点不应返回 null", near != null)
        assertEquals("c", near!!.first.id)
        assertTrue("距离应在地面公里量级", near.second in 50_000.0..200_000.0)
    }

    @Test
    fun `空列表与全无位置返回 null`() {
        assertNull(GeoMath.nearest(39.0, 116.0, emptyList()))
        assertNull(
            GeoMath.nearest(
                39.0, 116.0,
                listOf(ep("a", null, null), ep("b", null, 121.0), ep("c", 31.0, null)),
            )
        )
    }
}
