package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CollectorKey
import com.shiji.pro.core.ui.chart.ChannelChart
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.theme.Dimen

/** WiFi 页（P7）：已连接信息 / 网络详情 / 信道占用图 / 周边热点。真机 D2 修复说明见采集器。 */
@Composable
internal fun WifiTab(state: AppState, modifier: Modifier) {
    val cap = state.capabilityOf(CollectorKey.WIFI)
    ScrollColumn(modifier) {
        if (cap?.available == false) { PermissionGate(cap); return@ScrollColumn }
        val snap = state.wifi
        if (snap == null) { SectionCard("WiFi") { Text("尚未刷新或无 WiFi 连接", style = MaterialTheme.typography.bodyMedium) }; return@ScrollColumn }
        val c = snap.connection
        if (c == null) {
            // 真机回归（2026-11-12）：本机 WiFi 未连接（只开热点 / 走蜂窝）时，
            // 旧实现把系统哨兵值 -127dBm / -1MHz / -1Mbps 标成「实测」，
            // 还把蜂窝链路的 IP/网关/DNS 当 WiFi 信息展示。采集器现在未连接时直接不产出
            // connection，并把判定依据放进 scanNote，本页据此显示，不渲染任何地址字段。
            SectionCard("WiFi 未连接") {
                Text(snap.scanNote
                        ?: "当前设备未关联任何 WLAN 网络（SupplicantState 非 COMPLETED 或 networkId = -1）。",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Dimen.Space2))
                Text("因此本页不显示信号强度、频率、信道、链路速率，也不显示 IP / 网关 / DNS —— " +
                    "这些地址信息属于蜂窝链路，在「蜂窝」与「测速」页查看。",
                    style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(Dimen.Space3))
        } else {
            SectionCard("已连接") {
                renderFields(
                    "SSID" to c.ssid, "BSSID" to c.bssid, "信号(dBm)" to c.rssiDbm,
                    "频率(MHz)" to c.frequencyMhz, "信道" to c.channel,
                    "链路速率(Mbps)" to c.linkSpeedMbps, "安全类型" to c.security,
                    "IPv4" to c.ipV4, "IPv6" to c.ipV6, "网关" to c.gateway,
                    "子网掩码" to c.subnetMask, "广播地址" to c.broadcast, "本机 MAC" to c.localMac,
                )
            }
            Spacer(Modifier.height(Dimen.Space3))
            SectionCard("网络详情") {
                Text("DNS: ${if (c.dns.isEmpty()) "N/A" else c.dns.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Dimen.Space3))
        }
        SectionCard("信道占用图（2.4 / 5 / 6 GHz）") {
            ChannelChart(snap.accessPoints)
            Text("不同颜色区分热点；长空白信道区间省略显示",
                style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("周边热点（${snap.accessPoints.size}）") {
            snap.accessPoints.sortedByDescending { it.rssiDbm }.forEach { ap ->
                Text("${ap.ssid.displayText()}  ${ap.bssid}  ${ap.rssiDbm} dBm  ch${ap.channel.displayText()}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            // 未连接时 scanNote 装的是「WiFi 未连接」判定依据，已由上方说明卡完整展示，
            // 这里只在已连接时补扫描提示，避免同一句话出现两遍。
            if (c != null) snap.scanNote?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}
