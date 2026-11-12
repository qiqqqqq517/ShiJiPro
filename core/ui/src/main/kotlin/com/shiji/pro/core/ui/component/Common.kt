package com.shiji.pro.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.shiji.pro.core.model.Origin
import com.shiji.pro.core.ui.theme.Dimen
import com.shiji.pro.core.ui.theme.ShiJiPalette
import com.shiji.pro.core.ui.theme.ShiJiThemeTokens
import com.shiji.pro.core.ui.theme.ShiJiTypeSize

/**
 * 复用组件集（§11.1）：统一卡片 / 标题 / 数值行 / 三态标签样式。
 * 所有 Screen 必须复用这些组件，禁止在各 feature 内重写 Card/Row 模板。
 * 颜色、字号、间距、圆角全部取自主题令牌，本文件不含硬编码色值或尺寸。
 */

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val ext = ShiJiThemeTokens.extended
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimen.RadiusCard),
        colors = CardDefaults.cardColors(
            containerColor = ext.surface,
            contentColor = ext.onSurface,
        ),
        border = BorderStroke(Dimen.DividerThickness, ext.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimen.CardElevation),
    ) {
        Column(modifier = Modifier.padding(Dimen.Space4)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ext.onSurface,
                )
                if (trailing != null) trailing()
            }
            Spacer(Modifier.height(Dimen.Space2))
            Divider(thickness = Dimen.DividerThickness, color = ext.outline)
            Spacer(Modifier.height(Dimen.Space3))
            content()
        }
    }
}

/** 数值行：左侧字段名，右侧数值 + 三态徽标。 */
@Composable
fun ValueRow(
    label: String,
    value: String,
    origin: Origin = Origin.MEASURED,
    reason: String? = null,
    modifier: Modifier = Modifier,
) {
    val ext = ShiJiThemeTokens.extended
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimen.Space1),
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
            text = if (origin == Origin.UNAVAILABLE) "N/A" else value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (origin == Origin.UNAVAILABLE) ext.onSurfaceMuted else ext.onSurface,
        )
    }
}

/** 三态标注徽标（§3）：实测 / 换算 / 估算 / N/A，颜色取自色板。 */
@Composable
fun OriginBadge(origin: Origin, reason: String? = null) {
    val (label, bg) = when (origin) {
        Origin.MEASURED -> "实测" to ShiJiPalette.OriginMeasured
        Origin.DERIVED -> "换算" to ShiJiPalette.OriginDerived
        Origin.ESTIMATED -> "估算" to ShiJiPalette.OriginEstimated
        Origin.UNAVAILABLE -> "N/A" to ShiJiPalette.OriginUnavailable
    }
    Surface(
        color = bg.copy(alpha = 0.14f),
        shape = RoundedCornerShape(Dimen.RadiusSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = bg,
            modifier = Modifier.padding(horizontal = Dimen.Space1, vertical = Dimen.BadgeVerticalPadding),
        )
    }
}

/** 空状态：无数据 / 权限拒绝 / 机型限制时的统一占位。 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val ext = ShiJiThemeTokens.extended
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimen.Space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ext.onSurfaceMuted,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Dimen.Space3))
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(actionLabel, color = ShiJiPalette.Primary)
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
