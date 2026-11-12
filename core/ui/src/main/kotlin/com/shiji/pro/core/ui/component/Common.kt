package com.shiji.pro.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.shiji.pro.core.model.Origin
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiPalette
import com.shiji.pro.core.ui.theme.ShiJiThemeTokens
import com.shiji.pro.core.ui.theme.ShiJiTypeSize
import com.shiji.pro.core.ui.theme.originBadgeBackground
import com.shiji.pro.core.ui.theme.originBadgeForeground

/**
 * 复用组件集（§11.1）：统一卡片 / 标题 / 数值行 / 三态标签样式。
 * 所有 Screen 必须复用这些组件，禁止在各 feature 内重写 Card/Row 模板。
 * 颜色、字号、间距、圆角全部取自主题令牌，本文件不含硬编码色值或尺寸。
 *
 * P15 UI 统一改造：
 * - 卡片改用 `surfaceContainer` 与背景分层，标题可带图标与副标题，形成「标题 → 说明 → 数值」三级信息层级；
 * - 数值行支持 `stacked`（大屏双列时避免长标签与数值互相挤压）、展示 N/A 原因、整体语义合并；
 * - 三态徽标文字改用深浅主题专用前景色，保证小字号对比度；
 * - 空态补齐插画 / 标题 / 操作按钮，与加载、错误态构成一致的三态语言。
 */

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ShiJiIconKind? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: Dp = Dimen.Space4,
    content: @Composable () -> Unit,
) {
    val ext = ShiJiThemeTokens.extended
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimen.RadiusCard),
        colors = CardDefaults.cardColors(
            containerColor = ext.surfaceContainer,
            contentColor = ext.onSurface,
        ),
        border = BorderStroke(Dimen.DividerThickness, ext.outlineSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimen.CardElevation),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Surface(
                            shape = RoundedCornerShape(Dimen.RadiusSmall),
                            color = ext.surfaceContainerHigh,
                            contentColor = ext.accent,
                        ) {
                            ShiJiIcon(
                                kind = icon,
                                modifier = Modifier
                                    .size(Dimen.IconMedium)
                                    .padding(Dimen.Space1),
                            )
                        }
                        Spacer(Modifier.width(Dimen.Space2))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ext.onSurface,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.onSurfaceMuted,
                            )
                        }
                    }
                }
                if (trailing != null) trailing()
            }
            Spacer(Modifier.height(Dimen.Space2))
            Divider(thickness = Dimen.DividerThickness, color = ext.outlineSoft)
            Spacer(Modifier.height(Dimen.Space3))
            content()
        }
    }
}

/**
 * 数值行：左侧字段名，右侧数值 + 三态徽标；下方一行注释位**恒定占位**。
 *
 * 注释位用于「N/A 原因」与「估算值声明」：无论该行当前有没有注释，都保留同一行高度，
 * 数据到达（N/A → 实测）时行高不变，避免整页因几十行同时「少一行」而向上跳动。
 * 注释超过一行时截断，完整文本仍在语义描述里供读屏使用。
 */
@Composable
fun ValueRow(
    label: String,
    value: String,
    origin: Origin = Origin.MEASURED,
    reason: String? = null,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val ext = ShiJiThemeTokens.extended
    val displayValue = if (origin == Origin.UNAVAILABLE) "N/A" else value
    val semantics = "$label，${originLabel(origin)}，$displayValue" +
        if (reason.isNullOrBlank()) "" else "，原因：$reason"
    // 注释 = 取不到的原因 + 估算声明；只占一行，未取到值但无原因时也不会出现空档跳变。
    val caption = buildString {
        if (!reason.isNullOrBlank()) append(reason)
        if (origin == Origin.ESTIMATED) {
            if (isNotEmpty()) append(" · ")
            append("估算值，仅供参考")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = semantics }
            .padding(vertical = Dimen.Space1),
    ) {
        if (stacked) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = ext.onSurfaceMuted)
            Spacer(Modifier.height(Dimen.Space1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OriginBadge(origin = origin, reason = reason)
                Spacer(Modifier.width(Dimen.Space2))
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (origin == Origin.UNAVAILABLE) ext.onSurfaceMuted else ext.onSurface,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Dimen.Space2))
                OriginBadge(origin = origin, reason = reason)
                Spacer(Modifier.width(Dimen.Space2))
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (origin == Origin.UNAVAILABLE) ext.onSurfaceMuted else ext.onSurface,
                )
            }
        }
        // 注释位恒定占位：用不可见占位符撑起同一行高度，实测行不会比 N/A 行矮一行。
        Text(
            text = caption.ifEmpty { "\u00A0" },
            style = MaterialTheme.typography.labelSmall,
            color = ext.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.padding(top = Dimen.Space1),
        )
    }
}

/** 三态标注徽标（§3）：实测 / 换算 / 估算 / N/A；文字前景按深浅主题分派以保证对比度。 */
@Composable
fun OriginBadge(origin: Origin, reason: String? = null) {
    val bg = originBadgeBackground(origin)
    val fg = originBadgeForeground(origin)
    Surface(
        color = bg.copy(alpha = 0.14f),
        contentColor = fg,
        shape = RoundedCornerShape(Dimen.RadiusSmall),
    ) {
        Text(
            text = originLabel(origin),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier
                .heightIn(min = Dimen.BadgeHeight)
                .padding(horizontal = Dimen.Space1, vertical = Dimen.BadgeVerticalPadding),
        )
    }
}

/** 三态标注的中文短标签（语义文本与屏幕阅读器共用同一真源）。 */
fun originLabel(origin: Origin): String = when (origin) {
    Origin.MEASURED -> "实测"
    Origin.DERIVED -> "换算"
    Origin.ESTIMATED -> "估算"
    Origin.UNAVAILABLE -> "N/A"
}

/** 空状态：无数据 / 尚未刷新 / 机型不提供时的统一占位（不填充任何伪造数值）。 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    title: String? = null,
    icon: ShiJiIconKind? = ShiJiIconKind.EMPTY,
) {
    val ext = ShiJiThemeTokens.extended
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimen.Space6, horizontal = Dimen.Space4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            StateIcon(kind = icon)
            Spacer(Modifier.height(Dimen.Space3))
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ext.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dimen.Space1))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ext.onSurfaceMuted,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Dimen.Space3))
            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = Dimen.MinTouchTarget),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 估算值声明（§13）：所有 ESTIMATED 字段附近必须显示。 */
@Composable
fun EstimateDisclaimer(modifier: Modifier = Modifier) {
    Text(
        text = "估算值，仅供参考",
        style = MaterialTheme.typography.labelSmall,
        color = ShiJiPalette.OriginEstimated,
        modifier = modifier,
    )
}

/** 图表轴文字统一样式（P6 三种图表复用的字号）。 */
@Composable
fun ChartAxisText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = ShiJiTypeSize.ChartAxis,
        color = ShiJiThemeTokens.extended.onSurfaceMuted,
        modifier = modifier,
    )
}

/** 信号质量配色（§8 评级为估算值；色板集中定义）。 */
fun qualityColor(level: Int): Color = when (level) {
    0 -> ShiJiPalette.SignalExcellent
    1 -> ShiJiPalette.SignalGood
    2 -> ShiJiPalette.SignalFair
    3 -> ShiJiPalette.SignalPoor
    else -> ShiJiPalette.SignalBad
}
