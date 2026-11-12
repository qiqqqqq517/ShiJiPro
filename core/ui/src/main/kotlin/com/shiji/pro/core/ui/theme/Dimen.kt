package com.shiji.pro.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局尺寸令牌（§11.4：间距 / 圆角 / 海拔统一定义，Screen 内禁止写 .dp 字面量）。
 * 设计基准 4dp 栅格。
 */
object Dimen {
    // ---- 间距 ----
    val Space0 = 0.dp
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 20.dp
    val Space6 = 24.dp
    val Space8 = 32.dp
    val Space10 = 40.dp
    val Space12 = 48.dp

    // ---- 圆角 ----
    val RadiusSmall = 6.dp
    val RadiusCard = 12.dp
    val RadiusSheet = 20.dp
    val RadiusPill = 999.dp

    // ---- 组件尺寸 ----
    val CardElevation = 1.dp
    val DividerThickness = 1.dp
    val IconSmall = 16.dp
    val IconMedium = 24.dp
    val IconLarge = 40.dp
    val BadgeHeight = 20.dp
    val BadgeVerticalPadding = 1.dp
    val ChartHeight = 160.dp
    val MinTouchTarget = 48.dp

    // ---- 圆环 / 描边 ----
    val StrokeThin = 1.dp
    val StrokeNormal = 2.dp
    val StrokeBold = 4.dp
}
