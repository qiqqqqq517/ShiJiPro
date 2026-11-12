package com.shiji.pro.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全局字号/字重令牌（§11.4）。以 Material3 Typography 为载体，组件只引用 `ShiJiTypography.*`。
 * 不引入自定义字体文件：中英文字形由系统字体族回退，避免二进制字体资源与许可负担。
 *
 * P15 UI 统一改造：补齐原先未定义、被 Material3 默认档位接管的 `headlineSmall / titleSmall /
 * bodySmall`，消除「同一界面里 12sp 回落值与 13sp 令牌混用」的字号不一致；
 * `labelSmall` 由 11sp 提升至 12sp，满足小字号可读性与系统字体缩放的基准要求。
 */
object ShiJiTypeSize {
    val HeadlineLarge = 26.sp
    val HeadlineMedium = 21.sp
    val HeadlineSmall = 19.sp
    val TitleLarge = 18.sp
    val TitleMedium = 16.sp
    val TitleSmall = 14.sp
    val BodyLarge = 15.sp
    val BodyMedium = 13.sp
    val BodySmall = 12.sp
    val LabelLarge = 13.sp
    val LabelMedium = 12.sp
    val LabelSmall = 12.sp
    val ValueLarge = 22.sp
    val ValueMedium = 17.sp
    val ChartAxis = 10.sp
}

private fun textStyle(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size,
)

val ShiJiTypography = Typography(
    headlineLarge = textStyle(ShiJiTypeSize.HeadlineLarge, FontWeight.SemiBold),
    headlineMedium = textStyle(ShiJiTypeSize.HeadlineMedium, FontWeight.SemiBold),
    headlineSmall = textStyle(ShiJiTypeSize.HeadlineSmall, FontWeight.SemiBold),
    titleLarge = textStyle(ShiJiTypeSize.TitleLarge, FontWeight.SemiBold),
    titleMedium = textStyle(ShiJiTypeSize.TitleMedium, FontWeight.Medium),
    titleSmall = textStyle(ShiJiTypeSize.TitleSmall, FontWeight.Medium),
    bodyLarge = textStyle(ShiJiTypeSize.BodyLarge, FontWeight.Normal),
    bodyMedium = textStyle(ShiJiTypeSize.BodyMedium, FontWeight.Normal),
    bodySmall = textStyle(ShiJiTypeSize.BodySmall, FontWeight.Normal),
    labelLarge = textStyle(ShiJiTypeSize.LabelLarge, FontWeight.Medium),
    labelMedium = textStyle(ShiJiTypeSize.LabelMedium, FontWeight.Medium),
    labelSmall = textStyle(ShiJiTypeSize.LabelSmall, FontWeight.Normal),
)
