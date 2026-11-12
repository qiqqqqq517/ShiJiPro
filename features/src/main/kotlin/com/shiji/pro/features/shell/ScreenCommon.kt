package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.Origin
import com.shiji.pro.core.ui.component.EstimateDisclaimer
import com.shiji.pro.core.ui.component.SectionCard
import com.shiji.pro.core.ui.component.ValueRow
import com.shiji.pro.core.ui.theme.Dimen

/**
 * 各标签共用的只读渲染件（原 FeatureScreens.kt 的私有辅助函数，2026-11-12 结构拆分）。
 *
 * 拆分原则：**一个标签一个文件**（CellularTab/WifiTab/LocationTab/DeviceTab/DriveTestTab/SpeedTestTab），
 * 公共渲染骨架集中在本文件，避免 7 个标签挤在 400 行的单文件里。
 * 全部为无状态只读渲染：只读 [com.shiji.pro.core.model.AppState]，不触碰任何采集 API。
 */

/** 统一的滚动列容器（页面级 padding + 2dp 行距）。 */
@Composable
internal fun ScrollColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimen.Space4),
        verticalArrangement = Arrangement.spacedBy(Dimen.Space2),
    ) { content() }
}

/** 权限/能力降级提示卡：拒绝权限时本模块降级显示，不影响其它模块，不崩溃。 */
@Composable
internal fun PermissionGate(cap: CapabilityState) {
    SectionCard("需要授权") {
        Text(cap.reason ?: "权限未授予", style = MaterialTheme.typography.bodyMedium)
        cap.requiredPermission?.let { Text("权限: $it", style = MaterialTheme.typography.labelSmall) }
        Text("拒绝权限时本模块降级显示，不影响其它模块，不崩溃。",
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun renderFields(vararg pairs: Pair<String, FieldValue<*>>) {
    pairs.forEach { (label, value) -> renderField(label, value) }
}

/** 单字段行：三态徽标 + 原因；估算值追加「仅供参考」声明。 */
@Composable
internal fun renderField(label: String, value: FieldValue<*>) {
    ValueRow(
        label = label,
        value = value.displayText(),
        origin = value.origin,
        reason = value.reason,
    )
    if (value.origin == Origin.ESTIMATED) EstimateDisclaimer()
}
