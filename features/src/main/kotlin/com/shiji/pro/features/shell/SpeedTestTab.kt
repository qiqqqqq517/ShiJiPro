package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.shiji.pro.core.common.GeoMath
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.SpeedTestConfig
import com.shiji.pro.core.model.SpeedTestResult
import com.shiji.pro.core.model.UserEndpoint
import com.shiji.pro.core.ui.component.ChartFrame
import com.shiji.pro.core.ui.component.ListFrame
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ShiJiIcon
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner
import com.shiji.pro.core.ui.component.StatusTone
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiThemeTokens
import java.util.Locale

/**
 * 测速页（P10 + P15 恒定骨架）：结果 / 曲线 / 端点 / 说明，全部区块常驻。
 *
 * §11-B4 机制保持不变：不内置第三方节点列表，端点由用户录入；就近选点需定位 + 含位置端点。
 * 端点增删、就近选点与表单校验逻辑未改动，仅把「隐藏 / 显示」改为「常驻 + 禁用 / 原地更新」。
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
    val result = state.lastSpeedTest
    val write = onSpeedConfig

    ScrollColumn(modifier) {
        PageStatusCard(
            label = "网络测速",
            cap = null,
            hasSnapshot = result != null,
            note = "端点 ${endpoints.size} 个",
        )

        SectionCard(
            title = "网络测速",
            subtitle = "Ping / Jitter / 下行 / 上行（7 项全部列出）",
            icon = ShiJiIconKind.SPEED,
        ) {
            renderStableFields(SPEED_RESULT_LABELS, result?.let { resultValues(it) }.orEmpty())
            Text(
                "服务器: ${result?.endpoint?.label ?: "N/A"} · 配置仅内存保存，进程重启不保留",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Dimen.Space2))
            ChartFrame(
                description = "实时速率曲线",
                hasData = false,
                emptyMessage = "实时速率曲线：待测速执行与采样接入后渲染；当前不绘制占位曲线",
            ) {}
            StatusBanner("端点未配置或尚未测速时，所有速率字段保持 N/A，不伪造数值")
        }

        SectionCard(
            title = "测速端点（${endpoints.size}）",
            subtitle = "自建端点优先，不内置第三方节点库",
            icon = ShiJiIconKind.LOCATION,
        ) {
            ListFrame(
                itemCount = endpoints.size,
                emptyMessage = "未配置任何端点：所有速率字段 N/A；可在下方「添加端点」录入",
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    endpoints.forEach { ep ->
                        EndpointRow(
                            endpoint = ep,
                            selected = cfg?.selectedEndpointId == ep.id,
                            onSelect = { write?.invoke((cfg ?: SpeedTestConfig()).copy(selectedEndpointId = ep.id)) },
                            onDelete = {
                                val base = cfg ?: SpeedTestConfig()
                                nearestNote = null
                                write?.invoke(
                                    base.copy(
                                        userEndpoints = base.userEndpoints.filterNot { it.id == ep.id },
                                        selectedEndpointId =
                                            if (base.selectedEndpointId == ep.id) null else base.selectedEndpointId,
                                    )
                                )
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(Dimen.Space2))
            val gnssLat = state.gnss?.fix?.latitude?.value
            val gnssLon = state.gnss?.fix?.longitude?.value
            val hasFix = gnssLat != null && gnssLon != null
            val hasLocatedEndpoint = endpoints.any { it.hasLocation }
            val nearestReady = hasFix && hasLocatedEndpoint && write != null

            Row(horizontalArrangement = Arrangement.spacedBy(Dimen.Space2)) {
                TextButton(
                    onClick = { showAddDialog = true },
                    enabled = write != null,
                    modifier = Modifier.heightIn(min = Dimen.MinTouchTarget),
                ) { Text("添加端点") }
                TextButton(
                    onClick = {
                        val lat = gnssLat
                        val lon = gnssLon
                        if (lat == null || lon == null || write == null) return@TextButton
                        val near = GeoMath.nearest(lat, lon, endpoints) ?: return@TextButton
                        write((cfg ?: SpeedTestConfig()).copy(selectedEndpointId = near.first.id))
                        nearestNote = "就近选中 ${near.first.label}，距离 ≈ " +
                            String.format(Locale.US, "%.1f", near.second / 1000.0) +
                            " km（由 GNSS 定位与端点坐标计算，估算值）"
                    },
                    enabled = nearestReady,
                    modifier = Modifier.heightIn(min = Dimen.MinTouchTarget),
                ) { Text("按位置就近选点") }
            }
            // 固定高度状态条：不可用时给出原因，执行后原地换成结果，不新增/移除卡片。
            StatusBanner(
                text = nearestNote ?: when {
                    write == null -> "N/A · 只读模式：本界面未接写回回调"
                    !hasFix -> "按位置就近选点不可用：无定位（GNSS 未输出经纬度）"
                    !hasLocatedEndpoint -> "按位置就近选点不可用：无含位置的端点（录入时可填纬度/经度）"
                    else -> "按位置就近选点可用：已有定位与含位置端点"
                },
                tone = if (nearestReady) StatusTone.SUCCESS else StatusTone.WARNING,
                fixedHeight = true,
            )
            Text(
                "写入模式: ${if (write == null) "只读（N/A）" else "可编辑"} · " +
                    "说明：配置仅内存保存（进程重启不保留）；测速上传为随机填充字节。",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        SectionCard(title = "说明", subtitle = "隐私与实现口径", icon = ShiJiIconKind.INFO) {
            StatusBanner(
                "不绑定商业测速 SDK；上传为随机填充字节（非用户数据）；出口 IP 第三方查询默认关闭。",
            )
        }
    }

    if (showAddDialog) {
        AddEndpointDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { ep ->
                showAddDialog = false
                onSpeedConfig?.let { writeFn ->
                    val base = cfg ?: SpeedTestConfig()
                    writeFn(
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

/** 测速结果字段固定清单：所有项均展示，缺值填 N/A。 */
private val SPEED_RESULT_LABELS = listOf(
    "Ping(ms)", "Jitter(ms)", "下行(Mbps)", "上行(Mbps)", "出口 IP", "运营商", "归属地",
)

private fun resultValues(r: SpeedTestResult): Map<String, FieldValue<*>> = mapOf(
    "Ping(ms)" to r.pingMs, "Jitter(ms)" to r.jitterMs,
    "下行(Mbps)" to r.downMbps, "上行(Mbps)" to r.upMbps,
    "出口 IP" to r.publicIp, "运营商" to r.carrier, "归属地" to r.region,
)

/** 单个端点行：名称 / URL / 有无位置 / 「当前」标记 + 选用、删除。 */
@Composable
private fun EndpointRow(
    endpoint: UserEndpoint,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val ext = ShiJiThemeTokens.extended
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimen.Space1),
        shape = RoundedCornerShape(Dimen.RadiusCard),
        color = ext.surfaceContainerHigh,
        contentColor = ext.onSurface,
    ) {
        Column(modifier = Modifier.padding(Dimen.Space3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShiJiIcon(
                    kind = if (endpoint.hasLocation) ShiJiIconKind.LOCATION else ShiJiIconKind.SPEED,
                    modifier = Modifier.size(Dimen.IconSmall),
                )
                Spacer(Modifier.width(Dimen.Space2))
                Text(
                    endpoint.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                // 徽标常驻（当前 / 备选）：切换选中项时行高与宽度不变，不出现忽隐忽现的小标签。
                Surface(
                    shape = RoundedCornerShape(Dimen.RadiusPill),
                    color = if (selected) ext.accent else ext.outlineSoft,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else ext.onSurfaceMuted,
                ) {
                    Text(
                        if (selected) "当前" else "备选",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Dimen.Space2, vertical = Dimen.Space1),
                    )
                }
            }
            Text(endpoint.baseUrl, style = MaterialTheme.typography.bodySmall, color = ext.onSurfaceMuted)
            // 两行固定：含坐标与不含坐标两种文案都不改变行高。
            Text(
                if (endpoint.hasLocation) "含位置：纬度 ${endpoint.latDeg}，经度 ${endpoint.lonDeg}"
                else "无位置（不参与就近选点）",
                style = MaterialTheme.typography.labelSmall,
                color = ext.onSurfaceMuted,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Dimen.Space2)) {
                TextButton(onClick = onSelect, enabled = !selected) { Text("选用") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

/**
 * 添加端点对话框：名称 / URL / 纬度(可空) / 经度(可空)。
 * 校验不通过时给出原因并禁用「添加」；校验通过后才回调 [onConfirm]。
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
                // 恒定占位：未通过时给原因，通过后原地换成可提交提示，弹窗高度不随输入跳动。
                StatusBanner(
                    text = if (allOk) "校验通过：可点击「添加」保存端点" else endpointFormError(urlOk, latOk, lonOk),
                    tone = if (allOk) StatusTone.SUCCESS else StatusTone.ERROR,
                    fixedHeight = true,
                )
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
