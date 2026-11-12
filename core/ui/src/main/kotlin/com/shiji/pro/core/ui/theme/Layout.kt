package com.shiji.pro.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * P15 响应式布局契约。
 *
 * 不引入 `material3-window-size-class` 依赖（本机离线构建，版本目录未缓存该制品），
 * 改用 `LocalConfiguration.screenWidthDp` 单点判定；窗口自由缩放 / 旋转 / 分屏时配置变更会触发重组。
 *
 * - COMPACT：手机竖屏，顶部可滚动标签行 + 单列内容。
 * - MEDIUM ：平板 / 小窗桌面，左侧导航栏 + 单列限宽内容。
 * - EXPANDED：桌面 / 大屏，左侧导航栏 + 双列字段与分栏设置页。
 */
enum class WindowLayout { COMPACT, MEDIUM, EXPANDED }

object ShiJiBreakpoints {
    const val MEDIUM_DP = 600
    const val EXPANDED_DP = 840
}

val WindowLayout.isWide: Boolean get() = this != WindowLayout.COMPACT
val WindowLayout.useNavigationRail: Boolean get() = this != WindowLayout.COMPACT
val WindowLayout.twoColumn: Boolean get() = this == WindowLayout.EXPANDED

@Composable
fun rememberWindowLayout(): WindowLayout {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    return remember(widthDp) {
        when {
            widthDp >= ShiJiBreakpoints.EXPANDED_DP -> WindowLayout.EXPANDED
            widthDp >= ShiJiBreakpoints.MEDIUM_DP -> WindowLayout.MEDIUM
            else -> WindowLayout.COMPACT
        }
    }
}
