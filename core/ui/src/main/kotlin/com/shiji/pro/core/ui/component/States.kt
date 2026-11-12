package com.shiji.pro.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiExtendedColors
import com.shiji.pro.core.ui.theme.ShiJiThemeTokens

/**
 * P15 统一状态组件：加载 / 错误 / 信息提示。
 *
 * 设计约束：
 * 1. 只呈现状态，不产生数据 —— 空态与错误态绝不填充任何占位测量值（沿用 §10.4 口径）。
 * 2. 全部颜色取自主题扩展语义色，深浅主题自动适配。
 * 3. 状态块整体合并语义，屏幕阅读器一次读出完整结论，不逐条朗读装饰元素。
 */
enum class StatusTone { INFO, SUCCESS, WARNING, ERROR }

@Composable
private fun StatusTone.pair(ext: ShiJiExtendedColors): Pair<Color, Color> = when (this) {
    StatusTone.INFO -> ext.infoContainer to ext.onInfoContainer
    StatusTone.SUCCESS -> ext.successContainer to ext.onSuccessContainer
    StatusTone.WARNING -> ext.warningContainer to ext.onWarningContainer
    StatusTone.ERROR -> ext.errorContainer to ext.onErrorContainer
}

private fun StatusTone.icon(): ShiJiIconKind = when (this) {
    StatusTone.INFO -> ShiJiIconKind.INFO
    StatusTone.SUCCESS -> ShiJiIconKind.SUCCESS
    StatusTone.WARNING -> ShiJiIconKind.WARNING
    StatusTone.ERROR -> ShiJiIconKind.ERROR
}

/** 圆形状态插画：自绘图标落在低饱和语义容器上，避免引入图片资源。 */
@Composable
fun StateIcon(
    kind: ShiJiIconKind,
    tone: StatusTone = StatusTone.INFO,
    modifier: Modifier = Modifier,
) {
    val (container, content) = tone.pair(ShiJiThemeTokens.extended)
    Surface(
        modifier = modifier.size(Dimen.StateIconSize),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ShiJiIcon(kind = kind, modifier = Modifier.size(Dimen.IconMedium))
        }
    }
}

/**
 * 首次加载态：进度指示 + 骨架卡片。
 * `state.capabilities` 尚未到达时由装配层显示；骨架只表达「结构将出现」，不表达任何数值。
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = "正在获取数据…",
    skeletonCount: Int = 3,
) {
    val ext = ShiJiThemeTokens.extended
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimen.Space4)
            .clearAndSetSemantics { contentDescription = message },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimen.Space4),
    ) {
        Spacer(Modifier.height(Dimen.Space4))
        CircularProgressIndicator(color = ext.accent)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = ext.onSurfaceMuted)
        repeat(skeletonCount) { index -> SkeletonCard(index = index) }
    }
}

@Composable
private fun SkeletonCard(index: Int) {
    val ext = ShiJiThemeTokens.extended
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimen.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = ext.surfaceContainer, contentColor = ext.onSurface),
        border = androidx.compose.foundation.BorderStroke(Dimen.DividerThickness, ext.outlineSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimen.Space0),
    ) {
        Column(
            modifier = Modifier.padding(Dimen.Space4),
            verticalArrangement = Arrangement.spacedBy(Dimen.Space3),
        ) {
            SkeletonLine(widthFraction = if (index == 0) 0.42f else 0.32f, height = Dimen.SkeletonLineHeight)
            SkeletonLine(widthFraction = 0.92f, height = Dimen.SkeletonLineHeight)
            SkeletonLine(widthFraction = 0.68f, height = Dimen.SkeletonLineHeight)
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: androidx.compose.ui.unit.Dp) {
    val ext = ShiJiThemeTokens.extended
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(Dimen.RadiusSmall))
            .background(ext.skeletonBase)
            .border(Dimen.StrokeThin, ext.skeletonShine, RoundedCornerShape(Dimen.RadiusSmall)),
    )
}

/**
 * 统一错误 / 降级态：权限被拒、机型不支持、采集失败等。
 * 与 EmptyState 的区别在于带明确「问题结论」与可选重试动作。
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val ext = ShiJiThemeTokens.extended
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimen.RadiusCard),
        colors = CardDefaults.cardColors(
            containerColor = ext.errorContainer,
            contentColor = ext.onErrorContainer,
        ),
        border = androidx.compose.foundation.BorderStroke(Dimen.DividerThickness, ext.outlineSoft),
    ) {
        Column(
            modifier = Modifier.padding(Dimen.Space4),
            verticalArrangement = Arrangement.spacedBy(Dimen.Space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShiJiIcon(
                    kind = ShiJiIconKind.ERROR,
                    modifier = Modifier.size(Dimen.IconLarge),
                    tint = ext.onErrorContainer,
                )
                Spacer(Modifier.width(Dimen.Space3))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall)
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.height(Dimen.MinTouchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = ext.onErrorContainer),
                ) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/**
 * 轻量信息条：用于「内存存储 / 估算口径 / 功能边界」等需要持续可见的说明。
 *
 * @param fixedHeight 数据驱动的状态条设为 true：固定 48dp 高、最多两行，
 *        状态文字变化时布局高度不变，避免「突然跳出来」；完整文本保留在语义描述中供读屏使用。
 */
@Composable
fun StatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.INFO,
    icon: ShiJiIconKind = tone.icon(),
    fixedHeight: Boolean = false,
) {
    val (container, content) = tone.pair(ShiJiThemeTokens.extended)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimen.RadiusCard),
        color = container,
        contentColor = content,
    ) {
        Row(
            // 恒定高度用 minHeight 而非固定高度：大字号下两行文字不会被裁切，行数仍固定为 2。
            modifier = Modifier
                .heightIn(min = Dimen.MinTouchTarget)
                .padding(horizontal = Dimen.Space3, vertical = Dimen.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShiJiIcon(kind = icon, modifier = Modifier.size(Dimen.IconSmall))
            Spacer(Modifier.width(Dimen.Space2))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                minLines = if (fixedHeight) 2 else 1,
                maxLines = if (fixedHeight) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { contentDescription = text },
            )
        }
    }
}

/**
 * 固定高度图表框：无论有无数据都占用 [Dimen.ChartHeight]。
 * 无数据时在框内显示原因（不画伪造曲线），有数据时原地替换成真实图表，
 * 避免空态卡片与图表卡片高度不同造成的布局跳变。
 */
@Composable
fun ChartFrame(
    description: String,
    hasData: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    chart: @Composable () -> Unit,
) {
    val ext = ShiJiThemeTokens.extended
    Box(
        modifier = modifier.fillMaxWidth().height(Dimen.ChartHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (hasData) {
            chart()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimen.Space4)
                    .clearAndSetSemantics { contentDescription = "$description，$emptyMessage" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShiJiIcon(
                    kind = ShiJiIconKind.EMPTY,
                    modifier = Modifier.size(Dimen.IconLarge),
                    tint = ext.onSurfaceMuted,
                )
                Spacer(Modifier.height(Dimen.Space2))
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
            }
        }
    }
}

/**
 * 固定高度列表框：条目数量变化不改变卡片高度。
 * 空列表显示 N/A 占位（字段项不隐藏）；有数据时由调用方在框内自行滚动。
 */
@Composable
fun ListFrame(
    itemCount: Int,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ext = ShiJiThemeTokens.extended
    Box(
        modifier = modifier.fillMaxWidth().height(Dimen.ChartHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (itemCount == 0) {
            Text(
                text = "N/A · $emptyMessage",
                style = MaterialTheme.typography.bodySmall,
                color = ext.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Dimen.Space4),
            )
        } else {
            content()
        }
    }
}
