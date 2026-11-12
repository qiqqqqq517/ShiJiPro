package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.Origin
import com.shiji.pro.core.model.WifiConnection
import com.shiji.pro.core.ui.chart.ChannelChart
import com.shiji.pro.core.ui.component.ChartFrame
import com.shiji.pro.core.ui.component.ListFrame
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner
import com.shiji.pro.core.ui.component.StatusTone

/** WiFi 页（P7 + P15 恒定骨架）：已连接 / 网络详情 / 信道占用图 / 周边热点，4 张卡永远存在。 */
@Composable
internal fun WifiTab(state: AppState, modifier: Modifier) {
    val cap = state.capabilityOf(CollectorKey.WIFI)
    val snap = state.wifi
    val connection = snap?.connection
    val accessPoints = snap?.accessPoints.orEmpty()
    ScrollColumn(modifier) {
        PageStatusCard("WiFi", cap, hasSnapshot = snap != null)

        SectionCard(
            title = "已连接",
            subtitle = "SSID / 信号 / 链路速率 / 地址（13 项全部列出）",
            icon = ShiJiIconKind.WIFI,
        ) {
            Text(
                "连接状态: ${if (connection == null) "未连接 / N/A" else "已关联"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            renderStableFields(WIFI_CONNECTION_LABELS, connection?.let { wifiValues(it) }.orEmpty())
            // 真机回归（2026-11-12）：未关联 WLAN 时不产出 connection，避免把系统哨兵值
            // -127dBm / -1MHz / -1Mbps 标成「实测」，也不把蜂窝链路地址当 WiFi 信息展示。
            StatusBanner(
                snap?.scanNote ?: "N/A · 尚无扫描/连接判定说明；点顶栏「刷新」后原地更新",
                tone = if (connection == null) StatusTone.WARNING else StatusTone.INFO,
                fixedHeight = true,
            )
        }

        SectionCard(
            title = "网络详情",
            subtitle = "DNS 取自当前活动链路（固定 1 行，不随扫描结果增删）",
            icon = ShiJiIconKind.INFO,
        ) {
            val dnsReady = connection != null && connection.dns.isNotEmpty()
            renderStableRows(
                labels = listOf("DNS"),
                values = mapOf(
                    "DNS" to FieldDisplay(
                        text = if (dnsReady) connection!!.dns.joinToString(", ") else FieldValue.NA_TEXT,
                        origin = if (dnsReady) Origin.MEASURED else Origin.UNAVAILABLE,
                        reason = if (dnsReady) null else "未关联 WLAN 或系统未返回 DNS",
                    ),
                ),
                reason = "未关联 WLAN：DNS 属于蜂窝链路，本页保持 N/A",
            )
            StatusBanner(
                "未连接 WLAN 时，IP / 网关 / DNS 属于蜂窝链路，不在本页展示；" +
                    "相应字段保持 N/A，不使用哨兵值填充。",
                fixedHeight = true,
            )
        }

        SectionCard(
            title = "信道占用图（2.4 / 5 / 6 GHz）",
            subtitle = "按信道聚合周边热点",
            icon = ShiJiIconKind.COMPARE,
        ) {
            ChartFrame(
                description = "2.4 / 5 / 6 GHz 信道占用图",
                hasData = accessPoints.isNotEmpty(),
                emptyMessage = "暂无扫描结果：无可绘制的热点数据，图表区域保持占位",
            ) {
                ChannelChart(
                    accessPoints,
                    description = "2.4 / 5 / 6 GHz 信道占用图，共 ${accessPoints.size} 个热点",
                )
            }
            StatusBanner("不同颜色区分热点；长空白信道区间省略显示")
        }

        SectionCard(
            title = "周边热点（${accessPoints.size}）",
            subtitle = "按信号强度从高到低",
            icon = ShiJiIconKind.CELL,
        ) {
            ListFrame(
                itemCount = accessPoints.size,
                emptyMessage = "扫描结果为空或系统限制了扫描频率",
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    accessPoints.sortedByDescending { it.rssiDbm }.forEach { ap ->
                        Text(
                            "${ap.ssid.displayText()}  ${ap.bssid}  ${ap.rssiDbm} dBm  ch${ap.channel.displayText()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** 已连接字段固定清单：所有项均展示，缺值填 N/A。 */
private val WIFI_CONNECTION_LABELS = listOf(
    "SSID", "BSSID", "信号(dBm)", "频率(MHz)", "信道", "链路速率(Mbps)", "安全类型",
    "IPv4", "IPv6", "网关", "子网掩码", "广播地址", "本机 MAC",
)

private fun wifiValues(c: WifiConnection): Map<String, FieldValue<*>> = mapOf(
    "SSID" to c.ssid, "BSSID" to c.bssid, "信号(dBm)" to c.rssiDbm,
    "频率(MHz)" to c.frequencyMhz, "信道" to c.channel,
    "链路速率(Mbps)" to c.linkSpeedMbps, "安全类型" to c.security,
    "IPv4" to c.ipV4, "IPv6" to c.ipV6, "网关" to c.gateway,
    "子网掩码" to c.subnetMask, "广播地址" to c.broadcast, "本机 MAC" to c.localMac,
)
