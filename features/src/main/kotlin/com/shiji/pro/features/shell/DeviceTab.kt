package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.common.Format
import com.shiji.pro.core.common.displayTextWith
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.Origin
import com.shiji.pro.core.ui.component.EstimateDisclaimer
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.theme.Dimen

/** 设备页（P9）：电池 / 屏幕 / 内存存储 / 硬件 / 系统 / 传感器。D4 修复：字节与浮点走 Format。 */
@Composable
internal fun DeviceTab(state: AppState, modifier: Modifier) {
    val snap = state.device
    ScrollColumn(modifier) {
        if (snap == null) { SectionCard("设备") { Text("尚未刷新", style = MaterialTheme.typography.bodyMedium) }; return@ScrollColumn }
        SectionCard("电池") {
            renderFields(
                "电量(%)" to snap.battery.percent, "状态" to snap.battery.status,
                "健康度" to snap.battery.health, "温度(℃)" to snap.battery.temperatureCelsius,
                "电压(mV)" to snap.battery.voltageMv, "充电方式" to snap.battery.plugged,
                "电池技术" to snap.battery.technology,
            )
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("屏幕") {
            Text("分辨率 ${snap.screen.widthPx}×${snap.screen.heightPx} · ${snap.screen.densityDpi} dpi",
                style = MaterialTheme.typography.bodyMedium)
            renderFieldFmt("尺寸(英寸)", snap.screen.sizeInches) { Format.doubleShort(it) }
            renderFieldFmt("刷新率(Hz)", snap.screen.refreshRateHz) { Format.floatShort(it) }
            EstimateDisclaimer()  // 尺寸为换算值
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("内存与存储") {
            renderFieldFmt("RAM 总量", snap.memory.ramTotalBytes) { bytesOrNa(it) }
            renderFieldFmt("RAM 可用", snap.memory.ramAvailBytes) { bytesOrNa(it) }
            renderFieldFmt("ROM 总量", snap.memory.romTotalBytes) { bytesOrNa(it) }
            renderFieldFmt("ROM 可用", snap.memory.romAvailBytes) { bytesOrNa(it) }
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("硬件") {
            renderFields(
                "型号" to snap.hardware.model, "厂商" to snap.hardware.manufacturer,
                "芯片" to snap.hardware.socModel, "主板" to snap.hardware.board,
                "GPU 渲染器" to snap.hardware.gpuRenderer,
            )
            Text("CPU 架构: ${snap.hardware.abis.joinToString(", ")} · 核心数 ${snap.hardware.coreCount}",
                style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("系统") {
            renderFields(
                "Android" to snap.system.release, "安全补丁" to snap.system.securityPatch,
                "内核" to snap.system.kernel, "序列号" to snap.system.serial,
            )
            Text("SDK ${snap.system.sdkInt}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("本应用网络开销（TrafficStats）") {
            val t = state.traffic
            if (t == null) {
                Text("尚未刷新（读取本应用 UID 流量）", style = MaterialTheme.typography.bodyMedium)
            } else {
                renderFieldFmt("发送", t.txBytes) { bytesOrNa(it) }
                renderFieldFmt("接收", t.rxBytes) { bytesOrNa(it) }
                t.note?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(Modifier.height(Dimen.Space3))
        SectionCard("传感器（${snap.sensors.size}）") {
            LazyColumn(modifier = Modifier.height(Dimen.ChartHeight)) {
                items(snap.sensors, key = { "${it.type}-${it.name}" }) { s ->
                    Text("${s.name} · ${s.vendor} · type=${s.type} · 量程=${s.maxRange}",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * 带格式化渲染器的字段行（缺陷 D4）：字节数 / 浮点数走 core:common 的 [Format]，
 * 不再把 7848685568、6.10598541197403、144.00002 这类原值直接铺到界面上。
 * 三态语义不变：UNAVAILABLE 一律显示 N/A，格式化只作用于有效值。
 */
@Composable
private fun <T> renderFieldFmt(label: String, value: FieldValue<T>, render: (T) -> String) {
    androidx.compose.foundation.layout.Row {
        com.shiji.pro.core.ui.component.ValueRow(
            label = label,
            value = value.displayTextWith(render),
            origin = value.origin,
            reason = value.reason,
        )
    }
    if (value.origin == Origin.ESTIMATED) EstimateDisclaimer()
}

/** 字节数显示：原值 <=0 或取不到时回落 N/A（[Format.bytesHuman] 返回 null）。 */
private fun bytesOrNa(v: Long?): String = Format.bytesHuman(v) ?: FieldValue.NA_TEXT
