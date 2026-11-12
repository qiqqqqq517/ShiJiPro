package com.shiji.pro.core.model

/**
 * 测速端点配置（研究报告 §11-B4「节点库 + 就近选点」的机制落地，2026-11-12 冻结）。
 *
 * 边界：**不内置任何第三方服务器列表**（Cellular-Pro 那 7119 条节点是第三方数据，
 * 许可/归属未验证，不复制）。端点由用户自行录入（自建优先），
 * 只有当两端都有经纬度时才做「就近选点」，否则按选择顺序使用，不猜。
 */
data class UserEndpoint(
    val id: String,
    val label: String,
    /** 形如 `https://speed.example.com/`（末尾斜杠可有可无）。 */
    val baseUrl: String,
    /** 可选：用于就近选点的纬度（WGS-84）。 */
    val latDeg: Double? = null,
    /** 可选：可选：用于就近选点的经度（WGS-84）。 */
    val lonDeg: Double? = null,
) {
    /** 是否具备就近选点所需的位置。 */
    val hasLocation: Boolean get() = latDeg != null && lonDeg != null
}

/** 端点的测速能力契约（供 UI 显示「端点是否可用」）。 */
data class SpeedTestConfig(
    val userEndpoints: List<UserEndpoint> = emptyList(),
    /** 当前选中的端点 id；null = 使用自建占位端点（速率字段全部 N/A）。 */
    val selectedEndpointId: String? = null,
) {
    fun selectedOrNull(): UserEndpoint? = userEndpoints.firstOrNull { it.id == selectedEndpointId }
}
