package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shiji.pro.core.common.GeoMath
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.SpeedTestConfig
import com.shiji.pro.core.model.UserEndpoint
import com.shiji.pro.core.ui.chart.TrendChart
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.theme.Dimen
import java.util.Locale

/**
 * 测速页（P10）：结果 / 曲线 / **测速端点管理 + 就近选点** / 说明。
 *
 * §11-B4 机制落地（2026-11-12）：**不内置任何第三方节点列表**，端点由用户录入（自建优先），
 * 只有端点填了经纬度且 GNSS 有定位时才能「按位置就近选点」，距离为 Haversine 估算值。
 * 本页不发起任何联网探测：录入的端点只进内存配置，是否可达由测速执行时判定。
 *
 * @param onSpeedConfig 端点配置写回回调（冻结契约）；null = 只读模式，不渲染任何编辑入口。
 */
@Composable
internal fun SpeedTestTab(
    state: AppState,
    modifier: Modifier,
    onSpeedConfig: ((SpeedTestConfig) -> Unit)? = null,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var nearestNote by remember { mutableStateOf<String?>(null) }

    val cfg = state.speedConfig
    val endpoints = cfg?.userEndpoints.orEmpty()

    ScrollColumn(modifier) {
        val r = state.lastSpeedTest
        SectionCard("网络测速") {
            if (r == null) {
                Text("尚未测速。端点默认未配置（自建占位），点击顶栏刷新不会伪造速率。",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                renderFields(
                    "Ping(ms)" to r.pingMs, "Jitter(ms)" to r.jitterMs,
                    "下行(Mbps)" to r.downMbps, "上行(Mbps)" to r.upMbps,
                    "出口 IP" to r.publicIp, "运营商" to r.carrier, "归属地" to r.region,
                )
                Text("服务器: ${r.endpoint.label}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Dimen.Space2))
            TrendChart(points = emptyList())
            Text("实时速率曲线：待测速执行与采样接入后渲染", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("测速端点") {
            if (endpoints.isEmpty()) {
                Text(
                    "未配置任何端点：所有速率字段 N/A（端点由用户录入，自建优先；不内置第三方节点库）",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val write = onSpeedConfig
            if (write == null) {
                Spacer(Modifier.height(Dimen.Space1))
                Text("只读模式：本界面未接写回回调", style = MaterialTheme.typography.bodySmall)
            } else {
                endpoints.forEach { ep ->
                    EndpointRow(
                        endpoint = ep,
                        selected = cfg?.selectedEndpointId == ep.id,
                        onSelect = { write((cfg ?: SpeedTestConfig()).copy(selectedEndpointId = ep.id)) },
                        onDelete = {
                            val base = cfg ?: SpeedTestConfig()
                            // 端点集合变化后旧的「就近选中 X」提示不再可靠，一并清掉。
                            nearestNote = null
                            write(
                                base.copy(
                                    userEndpoints = base.userEndpoints.filterNot { it.id == ep.id },
                                    // 删除当前选中项时必须清空选择，避免指向不存在的 id。
                                    selectedEndpointId =
                                        if (base.selectedEndpointId == ep.id) null else base.selectedEndpointId,
                                )
                            )
                        },
                    )
                }

                Spacer(Modifier.height(Dimen.Space1))
                // 就近选点的可用条件：GNSS 有经纬度 + 至少一个端点带位置。
                val gnssLat = state.gnss?.fix?.latitude?.value
                val gnssLon = state.gnss?.fix?.longitude?.value
                val hasFix = gnssLat != null && gnssLon != null
                val hasLocatedEndpoint = endpoints.any { it.hasLocation }
                val nearestReady = hasFix && hasLocatedEndpoint

                Row(horizontalArrangement = Arrangement.spacedBy(Dimen.Space2)) {
                    TextButton(onClick = { showAddDialog = true }) { Text("添加端点") }
                    TextButton(
                        onClick = {
                            val lat = gnssLat
                            val lon = gnssLon
                            if (lat == null || lon == null) return@TextButton
                            val near = GeoMath.nearest(lat, lon, endpoints) ?: return@TextButton
                            write((cfg ?: SpeedTestConfig()).copy(selectedEndpointId = near.first.id))
                            nearestNote = "就近选中 ${near.first.label}，距离 ≈ " +
                                String.format(Locale.US, "%.1f", near.second / 1000.0) +
                                " km（由 GNSS 定位与端点坐标计算，估算值）"
                        },
                        enabled = nearestReady,
                    ) { Text("按位置就近选点") }
                }
                if (!nearestReady) {
                    Text(
                        if (!hasFix) "按位置就近选点不可用：无定位（GNSS 未输出经纬度）"
                        else "按位置就近选点不可用：无含位置的端点（录入端点时可填纬度/经度）",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                nearestNote?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }

            Spacer(Modifier.height(Dimen.Space1))
            Text(
                "说明：配置仅内存保存（进程重启不保留）；测速上传为随机填充字节。",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("说明") {
            Text("不绑定商业测速 SDK；上传为随机填充字节（非用户数据）；出口 IP 第三方查询默认关闭。",
                style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showAddDialog) {
        AddEndpointDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { ep ->
                showAddDialog = false
                onSpeedConfig?.let { write ->
                    val base = cfg ?: SpeedTestConfig()
                    write(
                        base.copy(
                            userEndpoints = base.userEndpoints + ep,
                            // 原先未选中任何端点时，新端点自动成为当前端点；已有选择则不抢。
                            selectedEndpointId = base.selectedEndpointId ?: ep.id,
                        )
                    )
                }
            },
        )
    }
}

/** 单个端点行：label / baseUrl / 有无位置 / 「当前」标记 + 选用、删除。 */
@Composable
private fun EndpointRow(
    endpoint: UserEndpoint,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(endpoint.label, style = MaterialTheme.typography.bodyMedium)
                if (selected) {
                    Spacer(Modifier.width(Dimen.Space1))
                    Text(
                        "当前",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(endpoint.baseUrl, style = MaterialTheme.typography.bodySmall)
            Text(
                if (endpoint.hasLocation) "📍 纬度 ${endpoint.latDeg}，经度 ${endpoint.lonDeg}"
                else "无位置（不参与就近选点）",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimen.Space2)) {
            TextButton(onClick = onSelect, enabled = !selected) { Text("选用") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}

/**
 * 添加端点对话框：名称 / URL / 纬度(可空) / 经度(可空)。
 * 校验不通过时在框内红字给出原因，并禁用「添加」；校验通过后才回调 [onConfirm]。
 */
@Composable
private fun AddEndpointDialog(
    onDismiss: () -> Unit,
    onConfirm: (UserEndpoint) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var lonText by remember { mutableStateOf("") }

    val urlOk = isValidHttpUrl(url)
    val latParsed = latText.trim().toDoubleOrNull()
    val lonParsed = lonText.trim().toDoubleOrNull()
    val latOk = latText.isBlank() || (latParsed != null && latParsed in -90.0..90.0)
    val lonOk = lonText.isBlank() || (lonParsed != null && lonParsed in -180.0..180.0)
    val allOk = urlOk && latOk && lonOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加测速端点") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimen.Space2),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL（http:// 或 https://）") },
                    singleLine = true,
                    isError = !urlOk,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it },
                    label = { Text("纬度（-90..90，可留空）") },
                    singleLine = true,
                    isError = !latOk,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { lonText = it },
                    label = { Text("经度（-180..180，可留空）") },
                    singleLine = true,
                    isError = !lonOk,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!allOk) {
                    Text(
                        endpointFormError(urlOk, latOk, lonOk),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = allOk,
                onClick = {
                    onConfirm(
                        UserEndpoint(
                            id = "user-" + System.currentTimeMillis(),
                            label = name.trim().ifEmpty { "未命名端点" },
                            baseUrl = url.trim(),
                            latDeg = latParsed,
                            lonDeg = lonParsed,
                        )
                    )
                },
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** URL 校验：必须以 http:// 或 https:// 开头，且主机部分非空（端口不影响判定）。 */
private fun isValidHttpUrl(raw: String): Boolean {
    val trimmed = raw.trim()
    val afterScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
        else -> return false
    }
    if (afterScheme.isEmpty()) return false
    // authority = 协议后到第一个 '/'；host = authority 去掉 ":port"
    val authority = afterScheme.substringBefore('/')
    val host = authority.substringBefore(':')
    return host.isNotBlank()
}

/** 对话框内的红字原因（只列当前失败的校验项）。 */
private fun endpointFormError(urlOk: Boolean, latOk: Boolean, lonOk: Boolean): String =
    listOfNotNull(
        if (!urlOk) "URL 必须以 http:// 或 https:// 开头且主机非空" else null,
        if (!latOk) "纬度需为 -90..90 的数字（可留空）" else null,
        if (!lonOk) "经度需为 -180..180 的数字（可留空）" else null,
    ).joinToString("；")
