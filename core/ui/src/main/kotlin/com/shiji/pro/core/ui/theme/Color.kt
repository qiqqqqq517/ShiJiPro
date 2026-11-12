package com.shiji.pro.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ShiJi 全局色板（§9.3：主色 / 强调色 / 信号配色 / 网络制式配色 / 三态标注色统一定义）。
 * 本文件是颜色的唯一真源；任何 Screen / 组件不得再写 `Color(0xFF...)` 字面量。
 * 深浅主题的语义分派统一收敛到 [ShiJiExtendedColors] 与 Theme.kt 的单一 `if (darkTheme)` 分支。
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
}

/**
 * MaterialTheme 标准色槽之外的扩展语义色，供复用组件（P6）通过 [LocalShiJiExtendedColors] 读取。
 * 深浅两套实例在 Theme.kt 里一次性构造，避免组件内部各自判断主题。
 */
data class ShiJiExtendedColors(
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val splashBackground: Color,
    val onSplash: Color,
    val onSplashMuted: Color,
)

val LightExtendedColors = ShiJiExtendedColors(
    surface = ShiJiPalette.SurfaceLight,
    surfaceVariant = ShiJiPalette.SurfaceVariantLight,
    outline = ShiJiPalette.OutlineLight,
    onSurface = ShiJiPalette.OnSurfaceLight,
    onSurfaceMuted = ShiJiPalette.OnSurfaceMutedLight,
    splashBackground = ShiJiPalette.SplashBackground,
    onSplash = ShiJiPalette.OnSplash,
    onSplashMuted = ShiJiPalette.OnSplashMuted,
)

val DarkExtendedColors = ShiJiExtendedColors(
    surface = ShiJiPalette.SurfaceDark,
    surfaceVariant = ShiJiPalette.SurfaceVariantDark,
    outline = ShiJiPalette.OutlineDark,
    onSurface = ShiJiPalette.OnSurfaceDark,
    onSurfaceMuted = ShiJiPalette.OnSurfaceMutedDark,
    splashBackground = ShiJiPalette.SplashBackground,
    onSplash = ShiJiPalette.OnSplash,
    onSplashMuted = ShiJiPalette.OnSplashMuted,
)
