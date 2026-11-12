package com.shiji.pro.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局尺寸令牌（§11.4：间距 / 圆角 / 海拔统一定义，Screen 内禁止写 .dp 字面量）。
 * 设计基准 4dp 栅格。
 *
 * P15 UI 统一改造：新增大屏内容限宽、导航栏宽度、状态插画与进度条尺寸。
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

    // ---- P15：响应式与状态组件 ----
    /** 大屏内容最大宽度：超宽窗口下文本行不再被拉到不可读的长度。 */
    val ContentMaxWidth = 900.dp
    /** 大屏左侧导航栏宽度。 */
    val RailWidth = 88.dp
    /** 状态插画（空 / 错误 / 加载）的直径。 */
    val StateIconSize = 52.dp
    /** 骨架屏占位条高度。 */
    val SkeletonLineHeight = 12.dp
    /** 刷新进度条高度。 */
    val ProgressHeight = 3.dp
}
