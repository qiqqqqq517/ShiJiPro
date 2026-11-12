package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.Origin
import com.shiji.pro.core.ui.component.EstimateDisclaimer
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ValueRow
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiThemeTokens

/**
 * P0 骨架页：用统一复用组件渲染「卡槽 → SIM/运营商 → 服务小区 → 邻区」分层。
 * 目的有二：① 让 P0 可运行、可截图核验布局；② 给 P7 的真实 Screen 提供组件用法样板。
 * 所有数值此刻均为占位且明确标 N/A（§10.4：取不到的字段显示 N/A，严禁编造数值）——
 * 此处 value 传 "—" 且 origin=UNAVAILABLE，UI 会强制显示 N/A，不显示任何伪造测量值。
 */
@Composable
fun SkeletonScreen(tab: ShiJiTab, modifier: Modifier = Modifier) {
    val ext = ShiJiThemeTokens.extended
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimen.Space4),
        verticalArrangement = Arrangement.spacedBy(Dimen.Space4),
    ) {
        Text(
            text = "P0 骨架页：本标签的采集与图表将在对应阶段实现",
            style = MaterialTheme.typography.labelMedium,
            color = ShiJiThemeTokens.extended.onSurfaceMuted,
        )

        when (tab) {
            ShiJiTab.SLOT1, ShiJiTab.SLOT2 -> SlotSkeleton(tab)
            else -> GenericSkeleton(tab)
        }

        // 三态标注图例：每个标签都展示，确保「实测/换算/估算/N/A」口径全局可见
        SectionCard(title = "数值标注说明") {
            LegendRow("实测", "系统 API / 传感器直接返回", Origin.MEASURED)
            LegendRow("换算", "由实测值经公式或查表得出", Origin.DERIVED)
            LegendRow("估算", "模型推算，非实测值", Origin.ESTIMATED)
            LegendRow("N/A", "取不到（权限 / 机型 / 系统限制）", Origin.UNAVAILABLE)
            Spacer(Modifier.height(Dimen.Space2))
            EstimateDisclaimer()
        }

        Spacer(Modifier.height(Dimen.Space4))
    }
}

@Composable
private fun SlotSkeleton(tab: ShiJiTab) {
    val slotLabel = if (tab == ShiJiTab.SLOT1) "Slot 0" else "Slot 1"

    SectionCard(title = "SIM / 运营商（$slotLabel）") {
        ValueRow("运营商名称", "—", Origin.UNAVAILABLE, "采集层未接入（P3）")
        ValueRow("MCC / MNC", "—", Origin.UNAVAILABLE, "采集层未接入（P3）")
        ValueRow("IMSI", "—", Origin.UNAVAILABLE, "Android 10+ 普通应用通常取不到")
        ValueRow("ICCID", "—", Origin.UNAVAILABLE, "Android 10+ 普通应用通常取不到")
        ValueRow("数据网络类型", "—", Origin.UNAVAILABLE)
        ValueRow("漫游状态", "—", Origin.UNAVAILABLE)
    }

    SectionCard(title = "服务小区") {
        ValueRow("小区类型", "—", Origin.UNAVAILABLE)
        ValueRow("TAC / PCI / ECI", "—", Origin.UNAVAILABLE)
        ValueRow("RSRP / RSRQ / SINR", "—", Origin.UNAVAILABLE)
        ValueRow("EARFCN / Band", "—", Origin.UNAVAILABLE)
    }

    SectionCard(title = "邻区") {
        ValueRow("邻区数量", "—", Origin.UNAVAILABLE, "多数机型/系统不提供邻区信息")
    }

    SectionCard(title = "信号强度趋势") {
        ValueRow("20s / 1min / 5min 窗口", "—", Origin.UNAVAILABLE)
    }
}

@Composable
private fun GenericSkeleton(tab: ShiJiTab) {
    SectionCard(title = tab.label) {
        ValueRow("数据", "—", Origin.UNAVAILABLE, "采集层未接入（P4–P9）")
    }
}

@Composable
private fun LegendRow(name: String, desc: String, origin: Origin) {
    ValueRow(label = "$name · $desc", value = "", origin = origin)
}
