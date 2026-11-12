package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.shiji.pro.core.common.Format
import com.shiji.pro.core.common.displayTextWith
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.BatteryInfo
import com.shiji.pro.core.model.DeviceSnapshot
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.MemoryInfo
import com.shiji.pro.core.model.SystemInfo
import com.shiji.pro.core.model.HardwareInfo
import com.shiji.pro.core.ui.component.ListFrame
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner

/** 设备页（P9 + P15 恒定骨架）：电池 / 屏幕 / 内存存储 / 硬件 / 系统 / 流量 / 传感器，7 张卡永远存在。 */
@Composable
internal fun DeviceTab(state: AppState, modifier: Modifier) {
    val snap = state.device
    val traffic = state.traffic
    val sensors = snap?.sensors.orEmpty()
    ScrollColumn(modifier) {
        PageStatusCard("设备信息", null, hasSnapshot = snap != null)

        SectionCard(title = "电池", subtitle = "来自 ACTION_BATTERY_CHANGED 粘性广播", icon = ShiJiIconKind.DEVICE) {
            renderStableFields(BATTERY_LABELS, snap?.let { batteryValues(it.battery) }.orEmpty())
        }

        SectionCard(title = "屏幕", subtitle = "分辨率为实测，物理尺寸为换算值", icon = ShiJiIconKind.DEVICE) {
            Text(
                "分辨率 ${snap?.screen?.let { "${it.widthPx}×${it.heightPx}" } ?: "N/A"} · " +
                    "${snap?.screen?.densityDpi ?: "N/A"} dpi",
                style = MaterialTheme.typography.bodyMedium,
            )
            renderStableRows(SCREEN_LABELS, snap?.let { screenValues(it) }.orEmpty())
            // 估算声明由 ValueRow 的固定注释位逐行给出，此处不再重复追加。
        }

        SectionCard(title = "内存与存储", subtitle = "RAM / ROM 总量与可用量", icon = ShiJiIconKind.DRIVE) {
            renderStableRows(MEMORY_LABELS, snap?.let { memoryValues(it.memory) }.orEmpty())
        }

        SectionCard(title = "硬件", subtitle = "型号 / 厂商 / 芯片 / ABI", icon = ShiJiIconKind.DEVICE) {
            renderStableFields(HARDWARE_LABELS, snap?.let { hardwareValues(it.hardware) }.orEmpty())
            // 固定两行：ABI 列表长度随机型变化，行数固定后不会把下方内容顶下去。
            Text(
                "CPU 架构: ${snap?.hardware?.abis?.joinToString(", ")?.ifEmpty { "N/A" } ?: "N/A"} · " +
                    "核心数 ${snap?.hardware?.coreCount ?: "N/A"}",
                style = MaterialTheme.typography.bodyMedium,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SectionCard(title = "系统", subtitle = "Android 版本 / 补丁 / 内核", icon = ShiJiIconKind.INFO) {
            renderStableFields(SYSTEM_LABELS, snap?.let { systemValues(it.system) }.orEmpty())
            Text("SDK ${snap?.system?.sdkInt ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard(title = "本应用网络开销（TrafficStats）", subtitle = "仅统计本应用 UID", icon = ShiJiIconKind.SPEED) {
            renderStableRows(
                TRAFFIC_LABELS,
                buildMap {
                    traffic?.txBytes?.let { put("发送", displayOf(it) { bytesOrNa(it) }) }
                    traffic?.rxBytes?.let { put("接收", displayOf(it) { bytesOrNa(it) }) }
                },
            )
            StatusBanner(traffic?.note ?: "N/A · 尚未读取本应用 UID 流量", fixedHeight = true)
        }

        SectionCard(title = "传感器（${sensors.size}）", subtitle = "系统传感器全量列表", icon = ShiJiIconKind.LOGS) {
            ListFrame(
                itemCount = sensors.size,
                emptyMessage = "系统未返回传感器列表或尚未刷新",
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    sensors.forEach { s ->
                        Text(
                            "${s.name} · ${s.vendor} · type=${s.type} · 量程=${s.maxRange}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

// ---- 固定字段清单 ----
private val BATTERY_LABELS = listOf(
    "电量(%)", "状态", "健康度", "温度(℃)", "电压(mV)", "充电方式", "电池技术",
)
private val SCREEN_LABELS = listOf("尺寸(英寸)", "刷新率(Hz)")
private val MEMORY_LABELS = listOf("RAM 总量", "RAM 可用", "ROM 总量", "ROM 可用")
private val HARDWARE_LABELS = listOf("型号", "厂商", "芯片", "主板", "GPU 渲染器")
private val SYSTEM_LABELS = listOf("Android", "安全补丁", "内核", "序列号")
private val TRAFFIC_LABELS = listOf("发送", "接收")

private fun batteryValues(b: BatteryInfo): Map<String, FieldValue<*>> = mapOf(
    "电量(%)" to b.percent, "状态" to b.status, "健康度" to b.health,
    "温度(℃)" to b.temperatureCelsius, "电压(mV)" to b.voltageMv,
    "充电方式" to b.plugged, "电池技术" to b.technology,
)

private fun hardwareValues(h: HardwareInfo): Map<String, FieldValue<*>> = mapOf(
    "型号" to h.model, "厂商" to h.manufacturer, "芯片" to h.socModel,
    "主板" to h.board, "GPU 渲染器" to h.gpuRenderer,
)

private fun systemValues(s: SystemInfo): Map<String, FieldValue<*>> = mapOf(
    "Android" to s.release, "安全补丁" to s.securityPatch,
    "内核" to s.kernel, "序列号" to s.serial,
)

/** 带格式化器的字段：保留原 origin / reason，只替换显示文本，不把格式化值误标成实测。 */
private fun <T> displayOf(value: FieldValue<T>, render: (T) -> String): FieldDisplay =
    FieldDisplay(value.displayTextWith(render), value.origin, value.reason)

private fun screenValues(s: DeviceSnapshot): Map<String, FieldDisplay> = mapOf(
    "尺寸(英寸)" to displayOf(s.screen.sizeInches) { Format.doubleShort(it) },
    "刷新率(Hz)" to displayOf(s.screen.refreshRateHz) { Format.floatShort(it) },
)

private fun memoryValues(m: MemoryInfo): Map<String, FieldDisplay> = mapOf(
    "RAM 总量" to displayOf(m.ramTotalBytes) { bytesOrNa(it) },
    "RAM 可用" to displayOf(m.ramAvailBytes) { bytesOrNa(it) },
    "ROM 总量" to displayOf(m.romTotalBytes) { bytesOrNa(it) },
    "ROM 可用" to displayOf(m.romAvailBytes) { bytesOrNa(it) },
)

/** 字节数显示：原值 <=0 或取不到时回落 N/A（[Format.bytesHuman] 返回 null）。 */
private fun bytesOrNa(v: Long?): String = Format.bytesHuman(v) ?: FieldValue.NA_TEXT
