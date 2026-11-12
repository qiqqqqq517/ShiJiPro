package com.shiji.pro.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全局字号/字重令牌（§11.4）。以 Material3 Typography 为载体，组件只引用 `ShiJiTypography.*`。
 * 不引入自定义字体文件：中英文字形由系统字体族回退，避免二进制字体资源与许可负担。
 */
object ShiJiTypeSize {
    val HeadlineLarge = 26.sp
    val HeadlineMedium = 21.sp
    val TitleLarge = 18.sp
    val TitleMedium = 16.sp
    val BodyLarge = 15.sp
    val BodyMedium = 13.sp
    val LabelLarge = 13.sp
    val LabelMedium = 12.sp
    val LabelSmall = 11.sp
    val ValueLarge = 22.sp
    val ValueMedium = 17.sp
    val ChartAxis = 10.sp
}

val ShiJiTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = ShiJiTypeSize.HeadlineLarge,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = ShiJiTypeSize.HeadlineMedium,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = ShiJiTypeSize.TitleLarge,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = ShiJiTypeSize.TitleMedium,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = ShiJiTypeSize.BodyLarge,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = ShiJiTypeSize.BodyMedium,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = ShiJiTypeSize.LabelLarge,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = ShiJiTypeSize.LabelMedium,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = ShiJiTypeSize.LabelSmall,
    ),
)
