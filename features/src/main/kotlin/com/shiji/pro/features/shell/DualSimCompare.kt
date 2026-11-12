package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.ui.component.SectionCard

/** 顶栏「双卡对比」覆盖层：两槽 RSRP/评级并排。 */
@Composable
fun DualSimCompare(state: AppState, modifier: Modifier = Modifier) {
    ScrollColumn(modifier) {
        SectionCard("双卡对比") {
            val a = state.cellularSlots[0]
            val b = state.cellularSlots[1]
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("卡槽1", style = MaterialTheme.typography.titleMedium)
                    Text("RSRP ${a?.signalDbm?.displayText() ?: "N/A"} · ${a?.qualityGrade?.displayText() ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f)) {
                    Text("卡槽2", style = MaterialTheme.typography.titleMedium)
                    Text("RSRP ${b?.signalDbm?.displayText() ?: "N/A"} · ${b?.qualityGrade?.displayText() ?: "N/A"}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (b == null) Text("卡槽2 无活跃 SIM：机型/系统未提供或仅单卡",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
