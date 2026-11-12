package com.shiji.pro.features.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.FieldValue
import com.shiji.pro.core.model.Origin
import com.shiji.pro.core.ui.component.EmptyState
import com.shiji.pro.core.ui.component.ShiJiIconKind
import com.shiji.pro.core.ui.component.StatusBanner
import com.shiji.pro.core.ui.component.StatusTone
import com.shiji.pro.core.ui.component.ValueRow
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.isWide
import com.shiji.pro.core.ui.theme.rememberWindowLayout
import com.shiji.pro.core.ui.theme.twoColumn

/**
 * 各标签共用的只读渲染件。
 *
 * P15 第二轮（恒定骨架）：
 * - 页面卡片数量、顺序、字段行数均固定；检测不到数据时用 `FieldValue.unavailable` 占位成 N/A，
 *   **不因取不到而隐藏字段**，数据到达后只是「原地换值」，不整页替换、不增删卡片；
 * - 状态条固定高度，随「加载 / 就绪 / 降级」改变文字与语气，但不改变布局高度；
 * - 图表与列表用固定高度框，避免空态与有数据态高度不同造成的跳动。
 *
 * 仍严格保持：所有 Screen 只读 AppState，不触碰任何采集 API；N/A 不伪造数值。
 */

/** 由装配层提供的「重新采集」动作（不触碰 ViewModel）。 */
val LocalRetryAction = compositionLocalOf<(() -> Unit)?> { null }

/** 统一的滚动列容器（响应式：宽屏限宽居中 + 更大间距）。 */
@Composable
internal fun ScrollColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val layout = rememberWindowLayout()
    val horizontal = if (layout.isWide) Dimen.Space6 else Dimen.Space4
    val spacing = if (layout.isWide) Dimen.Space4 else Dimen.Space3
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimen.ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal, vertical = Dimen.Space4),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            content()
        }
    }
}

/** 取不到的字段统一占位：N/A + 原因，绝不写入 0 / -1 / 空串伪装实测值。 */
internal fun na(reason: String = NA_REASON): FieldValue<Any> = FieldValue.unavailable(reason)

/** 默认原因控制在单行内可读（过长会被注释位截断，完整语义仍在读屏描述里）。 */
internal const val NA_REASON = "未取得（权限 / 机型 / 系统限制，或尚未刷新）"

/**
 * 页面状态条：**始终存在**、固定高度，只改变文字与语气。
 * 用于替代原先「权限不可用 / 快照为空就 return，整页只剩一张卡」的做法。
 */
@Composable
internal fun PageStatusCard(
    label: String,
    cap: CapabilityState?,
    hasSnapshot: Boolean,
    note: String? = null,
) {
    val tone = when {
        cap?.available == false -> StatusTone.ERROR
        !hasSnapshot -> StatusTone.WARNING
        else -> StatusTone.SUCCESS
    }
    val text = when {
        cap?.available == false ->
            "$label 不可用：${cap.reason ?: "权限未授予或机型不支持"}；下方字段全部保留并以 N/A 展示"
        !hasSnapshot ->
            "$label 暂无快照：所有字段以 N/A 占位，数据到达后原地更新，不整页替换"
        else ->
            "$label 已就绪；N/A 字段表示权限 / 机型 / 系统限制，不填充任何占位数值" +
                (note?.let { " · $it" } ?: "")
    }
    val icon = when (tone) {
        StatusTone.ERROR -> ShiJiIconKind.PERMISSION
        StatusTone.WARNING -> ShiJiIconKind.INFO
        else -> ShiJiIconKind.SUCCESS
    }
    StatusBanner(text = text, tone = tone, icon = icon, fixedHeight = true)
}

/** 兼容旧调用点：只呈现降级状态，不再作为整页早退的理由。 */
@Composable
internal fun PermissionGate(cap: CapabilityState) {
    StatusBanner(
        text = "${cap.reason ?: "权限未授予"} · 相关字段保持 N/A，不影响其它模块",
        tone = StatusTone.ERROR,
        icon = ShiJiIconKind.PERMISSION,
        fixedHeight = true,
    )
}

/** 统一「暂无数据」占位（仅用于确实无法列出条目的辅助场景）。 */
@Composable
internal fun NoDataState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ShiJiIconKind = ShiJiIconKind.EMPTY,
) {
    val retry = LocalRetryAction.current
    EmptyState(
        message = message,
        modifier = modifier,
        title = title,
        icon = icon,
        actionLabel = if (retry != null) "刷新" else null,
        onAction = retry,
    )
}

/**
 * 字段的显示态：文本 + 三态 + 原因。
 * 与 `FieldValue` 解耦，便于「带格式化器的字段」（字节数 / 浮点）在保留 origin 语义的同时
 * 参与稳定字段块，不把格式化值误标成实测。
 */
internal data class FieldDisplay(
    val text: String,
    val origin: Origin,
    val reason: String?,
)

private fun FieldValue<*>.toDisplay(): FieldDisplay = FieldDisplay(displayText(), origin, reason)

/**
 * 稳定字段块：按固定标签清单渲染，缺少的字段用 N/A 补齐。
 * 保证「所有项均展示」，且行数不随采集结果变化。
 */
@Composable
internal fun renderStableFields(
    labels: List<String>,
    values: Map<String, FieldValue<*>>,
    reason: String = NA_REASON,
) {
    renderStableRows(labels, values.mapValues { (_, v) -> v.toDisplay() }, reason)
}

/** 稳定字段块（显示态版本，供带格式化器的字段使用）。 */
@Composable
internal fun renderStableRows(
    labels: List<String>,
    values: Map<String, FieldDisplay>,
    reason: String = NA_REASON,
) {
    val rows = labels.map { label ->
        label to (values[label] ?: FieldDisplay(FieldValue.NA_TEXT, Origin.UNAVAILABLE, reason))
    }
    renderDisplayFields(rows)
}

/** 稳定字段块的渲染入口（宽屏两列 / 窄屏单列），行数恒定。 */
@Composable
internal fun renderFields(vararg pairs: Pair<String, FieldValue<*>>) {
    renderDisplayFields(pairs.map { (label, value) -> label to value.toDisplay() })
}

@Composable
private fun renderDisplayFields(rows: List<Pair<String, FieldDisplay>>) {
    if (!rememberWindowLayout().twoColumn) {
        rows.forEach { (label, d) -> renderFieldDisplay(label, d) }
        return
    }
    // EXPANDED：字段两两成行，半宽内改为「字段名在上、数值在下」，避免长标签挤压数值。
    rows.chunked(2).forEach { chunk ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimen.Space4),
        ) {
            chunk.forEach { (label, d) ->
                Box(modifier = Modifier.weight(1f)) { renderFieldDisplay(label, d, stacked = true) }
            }
            if (chunk.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun renderFieldDisplay(label: String, d: FieldDisplay, stacked: Boolean = false) {
    // 估算声明已并入 ValueRow 的固定注释位，不再额外追加一行（避免数据到达时行数变化）。
    ValueRow(
        label = label,
        value = d.text,
        origin = d.origin,
        reason = d.reason,
        stacked = stacked,
    )
}

/** 单字段行：三态徽标 + 原因；估算值追加「仅供参考」声明。 */
@Composable
internal fun renderField(label: String, value: FieldValue<*>, stacked: Boolean = false) {
    renderFieldDisplay(label, value.toDisplay(), stacked)
}
