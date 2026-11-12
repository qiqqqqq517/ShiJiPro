package com.shiji.pro.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * ShiJi 主题入口：深浅两套色板在此一次性分派（§9.3 / §11.4）。
 * 组件只通过 [LocalShiJiExtendedColors] 读取扩展语义色，不得自行判断 `isSystemInDarkTheme()`。
 *
 * @param darkTheme 默认「跟随系统」；设置页显式切换时由调用方传入具体值。
 * @param dynamicColor  deliberately false：本工具需保证信号配色语义稳定，不接入 Android 12 动态取色。
 *
 * P15：补齐 `secondaryContainer / onSecondaryContainer`，使 NavigationRail 等
 * Material3 组件选中态沿用品牌色系，而非回落到系统默认紫色。
 */
private val LightColorScheme = lightColorScheme(
    primary = ShiJiPalette.Primary,
    onPrimary = ShiJiPalette.OnPrimary,
    primaryContainer = ShiJiPalette.SurfaceVariantLight,
    onPrimaryContainer = ShiJiPalette.OnSurfaceLight,
    secondary = ShiJiPalette.PrimaryLight,
    onSecondary = ShiJiPalette.OnPrimary,
    secondaryContainer = ShiJiPalette.SurfaceVariantLight,
    onSecondaryContainer = ShiJiPalette.OnSurfaceLight,
    background = ShiJiPalette.SurfaceLight,
    onBackground = ShiJiPalette.OnSurfaceLight,
    surface = ShiJiPalette.SurfaceLight,
    onSurface = ShiJiPalette.OnSurfaceLight,
    surfaceVariant = ShiJiPalette.SurfaceVariantLight,
    onSurfaceVariant = ShiJiPalette.OnSurfaceMutedLight,
    outline = ShiJiPalette.OutlineLight,
    error = ShiJiPalette.Error,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = ShiJiPalette.PrimaryLight,
    onPrimary = ShiJiPalette.SplashBackground,
    primaryContainer = ShiJiPalette.SurfaceVariantDark,
    onPrimaryContainer = ShiJiPalette.OnSurfaceDark,
    secondary = ShiJiPalette.PrimaryPale,
    onSecondary = ShiJiPalette.SplashBackground,
    secondaryContainer = ShiJiPalette.SurfaceVariantDark,
    onSecondaryContainer = ShiJiPalette.OnSurfaceDark,
    background = ShiJiPalette.SurfaceDark,
    onBackground = ShiJiPalette.OnSurfaceDark,
    surface = ShiJiPalette.SurfaceDark,
    onSurface = ShiJiPalette.OnSurfaceDark,
    surfaceVariant = ShiJiPalette.SurfaceVariantDark,
    onSurfaceVariant = ShiJiPalette.OnSurfaceMutedDark,
    outline = ShiJiPalette.OutlineDark,
    error = ShiJiPalette.Error,
    onError = Color.White,
)

val LocalShiJiExtendedColors = staticCompositionLocalOf { LightExtendedColors }

@Composable
fun ShiJiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalShiJiExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ShiJiTypography,
            content = content,
        )
    }
}

/** 组件内取扩展语义色的便捷入口。 */
object ShiJiThemeTokens {
    val extended: ShiJiExtendedColors
        @Composable get() = LocalShiJiExtendedColors.current
}
