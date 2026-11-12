package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner
import com.shiji.pro.core.ui.component.StatusTone
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiThemeTokens

/** 顶栏「双卡对比」覆盖层：两槽面板与状态条常驻，缺卡时显示 N/A 而不是隐藏。 */
@Composable
fun DualSimCompare(state: AppState, modifier: Modifier = Modifier) {
    val a = state.cellularSlots[0]
    val b = state.cellularSlots[1]
    ScrollColumn(modifier) {
        PageStatusCard("双卡对比", null, hasSnapshot = a != null || b != null)
        SectionCard(
            title = "双卡对比",
            subtitle = "两卡槽当前 RSRP 与信号评级并排展示",
            icon = ShiJiIconKind.COMPARE,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimen.Space3),
                verticalAlignment = Alignment.Top,
            ) {
                SimComparePanel(
                    label = "卡槽1",
                    rsrp = a?.signalDbm?.displayText() ?: "N/A",
                    grade = a?.qualityGrade?.displayText() ?: "N/A",
                    modifier = Modifier.weight(1f),
                )
                SimComparePanel(
                    label = "卡槽2",
                    rsrp = b?.signalDbm?.displayText() ?: "N/A",
                    grade = b?.qualityGrade?.displayText() ?: "N/A",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Dimen.Space2))
            StatusBanner(
                text = when {
                    a == null && b == null -> "两个卡槽均无数据：尚未刷新、未插卡或机型未提供；字段保持 N/A"
                    b == null -> "卡槽2 无活跃 SIM：机型/系统未提供或仅单卡；字段保持 N/A"
                    a == null -> "卡槽1 无活跃 SIM：机型/系统未提供或仅单卡；字段保持 N/A"
                    else -> "两卡槽均有快照；评级为估算值，仅供参考"
                },
                tone = if (a != null && b != null) StatusTone.SUCCESS else StatusTone.WARNING,
                fixedHeight = true,
            )
        }
    }
}

@Composable
private fun SimComparePanel(
    label: String,
    rsrp: String,
    grade: String,
    modifier: Modifier = Modifier,
) {
    val ext = ShiJiThemeTokens.extended
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimen.RadiusCard),
        color = ext.surfaceContainerHigh,
        contentColor = ext.onSurface,
    ) {
        Column(modifier = Modifier.padding(Dimen.Space3)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimen.Space2))
            Text("RSRP", style = MaterialTheme.typography.labelSmall, color = ext.onSurfaceMuted)
            Text(rsrp, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimen.Space2))
            Text("评级（估算）", style = MaterialTheme.typography.labelSmall, color = ext.onSurfaceMuted)
            Text(grade, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
