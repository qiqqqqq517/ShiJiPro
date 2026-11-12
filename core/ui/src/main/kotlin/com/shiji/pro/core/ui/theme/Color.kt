package com.shiji.pro.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ShiJi 全局色板（§9.3：主色 / 强调色 / 信号配色 / 网络制式配色 / 三态标注色统一定义）。
 * 本文件是颜色的唯一真源；任何 Screen / 组件不得再写 `Color(0xFF...)` 字面量。
 * 深浅主题的语义分派统一收敛到 [ShiJiExtendedColors] 与 Theme.kt 的单一 `if (darkTheme)` 分支。
 *
 * P15 UI 统一改造：新增「表面分层 / 状态容器 / 骨架屏 / 徽标前景」四类语义色。
 * 徽标文字不再直接复用信号原色（浅底 14% alpha 上对比度不足），改由深浅两套专用前景保证可读性。
 */
object ShiJiPalette {
    // ---- 品牌主色 ----
    val Primary = Color(0xFF0F766E)          // teal-700
    val PrimaryLight = Color(0xFF14B8A6)   // teal-500 强调
    val PrimaryPale = Color(0xFF5EEAD4)     // teal-300 暗色前景
    val OnPrimary = Color(0xFFFFFFFF)

    // ---- 中性 / 背景 ----
    val SplashBackground = Color(0xFF0B1220)
    val OnSplash = Color(0xFF5EEAD4)
    val OnSplashMuted = Color(0xFFA9C2BE)
    val SurfaceLight = Color(0xFFF7FAF9)
    val SurfaceDark = Color(0xFF111820)
    val SurfaceVariantLight = Color(0xFFE3EFEC)
    val SurfaceVariantDark = Color(0xFF1C2A31)
    val OutlineLight = Color(0xFFB8CCC7)
    val OutlineDark = Color(0xFF3A5058)
    val OnSurfaceLight = Color(0xFF10231F)
    val OnSurfaceDark = Color(0xFFE4F1EE)
    val OnSurfaceMutedLight = Color(0xFF557069)
    val OnSurfaceMutedDark = Color(0xFF9FB8B2)

    // ---- 信号质量评级：优秀 / 良好 / 一般 / 较差 / 极差（§8 评级为估算值） ----
    val SignalExcellent = Color(0xFF16A34A)
    val SignalGood = Color(0xFF65A30D)
    val SignalFair = Color(0xFFCA8A04)
    val SignalPoor = Color(0xFFEA580C)
    val SignalBad = Color(0xFFDC2626)

    // ---- 网络制式配色（§2.4 基站地图） ----
    val Net5G = Color(0xFF7C3AED)   // 紫
    val Net4G = Color(0xFF2563EB)   // 蓝
    val Net3G = Color(0xFFEA580C)   // 橙
    val Net2G = Color(0xFFDC2626)   // 红

    // ---- 数据三态标注（§3：实测 / 换算 / 估算 / N/A） ----
    val OriginMeasured = Color(0xFF16A34A)
    val OriginDerived = Color(0xFF2563EB)
    val OriginEstimated = Color(0xFFD97706)
    val OriginUnavailable = Color(0xFF64748B)

    // ---- GNSS 星座配色（§4.6 卫星天空图） ----
    val ConstellationGps = Color(0xFF2563EB)
    val ConstellationBeidou = Color(0xFFDC2626)
    val ConstellationGlonass = Color(0xFF7C3AED)
    val ConstellationQzss = Color(0xFF0891B2)
    val ConstellationGalileo = Color(0xFF16A34A)
    val ConstellationSbas = Color(0xFFCA8A04)
    val ConstellationUnknown = Color(0xFF64748B)

    // ---- 危险 / 提示 ----
    val Error = Color(0xFFDC2626)
    val Warning = Color(0xFFD97706)

    // ---- P15：表面分层（卡片与应用栏从背景中抬升，形成清晰信息层级） ----
    val SurfaceContainerLight = Color(0xFFFFFFFF)
    val SurfaceContainerDark = Color(0xFF18232B)
    val SurfaceContainerHighLight = Color(0xFFE8F3F0)
    val SurfaceContainerHighDark = Color(0xFF213039)
    val OutlineSoftLight = Color(0xFFE1EDE9)
    val OutlineSoftDark = Color(0xFF2B3C44)

    // ---- P15：骨架屏 ----
    val SkeletonBaseLight = Color(0xFFE2ECE9)
    val SkeletonShineLight = Color(0xFFF3F9F7)
    val SkeletonBaseDark = Color(0xFF22313A)
    val SkeletonShineDark = Color(0xFF2D3F48)

    // ---- P15：状态容器（错误 / 警告 / 成功 / 说明），前景色保证 >= 4.5:1 ----
    val ErrorContainerLight = Color(0xFFFDECEC)
    val OnErrorContainerLight = Color(0xFF932020)
    val ErrorContainerDark = Color(0xFF4A1D1D)
    val OnErrorContainerDark = Color(0xFFFFCBCB)
    val WarningContainerLight = Color(0xFFFEF2DF)
    val OnWarningContainerLight = Color(0xFF854500)
    val WarningContainerDark = Color(0xFF4A3212)
    val OnWarningContainerDark = Color(0xFFFFDCA6)
    val SuccessContainerLight = Color(0xFFE7F5EB)
    val OnSuccessContainerLight = Color(0xFF145C2C)
    val SuccessContainerDark = Color(0xFF143A22)
    val OnSuccessContainerDark = Color(0xFFB9EFC8)
    val InfoContainerLight = Color(0xFFE7F0FB)
    val OnInfoContainerLight = Color(0xFF1B4A85)
    val InfoContainerDark = Color(0xFF17314F)
    val OnInfoContainerDark = Color(0xFFC6DEFF)

    // ---- P15：三态徽标文字（底色为同语义色 14% alpha，深浅各一套可读前景） ----
    val OriginMeasuredTextLight = Color(0xFF15803D)
    val OriginDerivedTextLight = Color(0xFF1D4ED8)
    val OriginEstimatedTextLight = Color(0xFFB45309)
    val OriginUnavailableTextLight = Color(0xFF475569)
    val OriginMeasuredTextDark = Color(0xFF86EFAC)
    val OriginDerivedTextDark = Color(0xFF93C5FD)
    val OriginEstimatedTextDark = Color(0xFFFCD34D)
    val OriginUnavailableTextDark = Color(0xFFCBD5E1)
}

/**
 * MaterialTheme 标准色槽之外的扩展语义色，供复用组件通过 [LocalShiJiExtendedColors] 读取。
 * 深浅两套实例在 Theme.kt 里一次性构造，避免组件内部各自判断主题。
 */
data class ShiJiExtendedColors(
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val outline: Color,
    val outlineSoft: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val splashBackground: Color,
    val onSplash: Color,
    val onSplashMuted: Color,
    val skeletonBase: Color,
    val skeletonShine: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val originTextMeasured: Color,
    val originTextDerived: Color,
    val originTextEstimated: Color,
    val originTextUnavailable: Color,
    val chartGrid: Color,
)

val LightExtendedColors = ShiJiExtendedColors(
    surface = ShiJiPalette.SurfaceLight,
    surfaceContainer = ShiJiPalette.SurfaceContainerLight,
    surfaceContainerHigh = ShiJiPalette.SurfaceContainerHighLight,
    outline = ShiJiPalette.OutlineLight,
    outlineSoft = ShiJiPalette.OutlineSoftLight,
    onSurface = ShiJiPalette.OnSurfaceLight,
    onSurfaceMuted = ShiJiPalette.OnSurfaceMutedLight,
    accent = ShiJiPalette.Primary,
    splashBackground = ShiJiPalette.SplashBackground,
    onSplash = ShiJiPalette.OnSplash,
    onSplashMuted = ShiJiPalette.OnSplashMuted,
    skeletonBase = ShiJiPalette.SkeletonBaseLight,
    skeletonShine = ShiJiPalette.SkeletonShineLight,
    errorContainer = ShiJiPalette.ErrorContainerLight,
    onErrorContainer = ShiJiPalette.OnErrorContainerLight,
    warningContainer = ShiJiPalette.WarningContainerLight,
    onWarningContainer = ShiJiPalette.OnWarningContainerLight,
    successContainer = ShiJiPalette.SuccessContainerLight,
    onSuccessContainer = ShiJiPalette.OnSuccessContainerLight,
    infoContainer = ShiJiPalette.InfoContainerLight,
    onInfoContainer = ShiJiPalette.OnInfoContainerLight,
    originTextMeasured = ShiJiPalette.OriginMeasuredTextLight,
    originTextDerived = ShiJiPalette.OriginDerivedTextLight,
    originTextEstimated = ShiJiPalette.OriginEstimatedTextLight,
    originTextUnavailable = ShiJiPalette.OriginUnavailableTextLight,
    chartGrid = ShiJiPalette.OutlineSoftLight,
)

val DarkExtendedColors = ShiJiExtendedColors(
    surface = ShiJiPalette.SurfaceDark,
    surfaceContainer = ShiJiPalette.SurfaceContainerDark,
    surfaceContainerHigh = ShiJiPalette.SurfaceContainerHighDark,
    outline = ShiJiPalette.OutlineDark,
    outlineSoft = ShiJiPalette.OutlineSoftDark,
    onSurface = ShiJiPalette.OnSurfaceDark,
    onSurfaceMuted = ShiJiPalette.OnSurfaceMutedDark,
    accent = ShiJiPalette.PrimaryLight,
    splashBackground = ShiJiPalette.SplashBackground,
    onSplash = ShiJiPalette.OnSplash,
    onSplashMuted = ShiJiPalette.OnSplashMuted,
    skeletonBase = ShiJiPalette.SkeletonBaseDark,
    skeletonShine = ShiJiPalette.SkeletonShineDark,
    errorContainer = ShiJiPalette.ErrorContainerDark,
    onErrorContainer = ShiJiPalette.OnErrorContainerDark,
    warningContainer = ShiJiPalette.WarningContainerDark,
    onWarningContainer = ShiJiPalette.OnWarningContainerDark,
    successContainer = ShiJiPalette.SuccessContainerDark,
    onSuccessContainer = ShiJiPalette.OnSuccessContainerDark,
    infoContainer = ShiJiPalette.InfoContainerDark,
    onInfoContainer = ShiJiPalette.OnInfoContainerDark,
    originTextMeasured = ShiJiPalette.OriginMeasuredTextDark,
    originTextDerived = ShiJiPalette.OriginDerivedTextDark,
    originTextEstimated = ShiJiPalette.OriginEstimatedTextDark,
    originTextUnavailable = ShiJiPalette.OriginUnavailableTextDark,
    chartGrid = ShiJiPalette.OutlineSoftDark,
)

/** 按三态取徽标文字色（底色仍用 [originBadgeBackground]）。 */
@androidx.compose.runtime.Composable
fun originBadgeForeground(origin: com.shiji.pro.core.model.Origin): Color {
    val ext = LocalShiJiExtendedColors.current
    return when (origin) {
        com.shiji.pro.core.model.Origin.MEASURED -> ext.originTextMeasured
        com.shiji.pro.core.model.Origin.DERIVED -> ext.originTextDerived
        com.shiji.pro.core.model.Origin.ESTIMATED -> ext.originTextEstimated
        com.shiji.pro.core.model.Origin.UNAVAILABLE -> ext.originTextUnavailable
    }
}

/** 三态徽标底色用原语义色（保持在色板唯一真源内，不在组件里写色值）。 */
fun originBadgeBackground(origin: com.shiji.pro.core.model.Origin): Color = when (origin) {
    com.shiji.pro.core.model.Origin.MEASURED -> ShiJiPalette.OriginMeasured
    com.shiji.pro.core.model.Origin.DERIVED -> ShiJiPalette.OriginDerived
    com.shiji.pro.core.model.Origin.ESTIMATED -> ShiJiPalette.OriginEstimated
    com.shiji.pro.core.model.Origin.UNAVAILABLE -> ShiJiPalette.OriginUnavailable
}
