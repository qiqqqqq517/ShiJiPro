package com.shiji.pro.features.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shiji.pro.core.model.AppState

/**
 * 7 标签的**唯一分派入口**（结构拆分后主体在各标签自己的文件里）：
 *
 * | 标签 | 文件 |
 * |---|---|
 * | 卡槽1 / 卡槽2 | `CellularTab.kt` |
 * | WiFi | `WifiTab.kt` |
 * | 位置 | `LocationTab.kt` |
 * | 设备 | `DeviceTab.kt` |
 * | 覆盖测试 | `DriveTestTab.kt` |
 * | 测速 | `SpeedTestTab.kt` |
 * | （顶栏）双卡对比 | `DualSimCompare.kt` |
 * | 公共渲染件 | `ScreenCommon.kt` |
 *
 * 全部为无状态只读渲染：只接收 [AppState]，不触碰任何采集 API；
 * 取不到的字段统一 N/A + 原因，估算值附声明。
 */
@Composable
fun TabScreen(
    tab: ShiJiTab,
    state: AppState,
    modifier: Modifier = Modifier,
    /** 测速端点配置写回（§11-B4，仅 SPEED_TEST 用；null = 只读展示）。 */
    onSpeedConfig: ((com.shiji.pro.core.model.SpeedTestConfig) -> Unit)? = null,
) {
    when (tab) {
        ShiJiTab.SLOT1 -> CellularTab(state, 0, modifier)
        ShiJiTab.SLOT2 -> CellularTab(state, 1, modifier)
        ShiJiTab.WIFI -> WifiTab(state, modifier)
        ShiJiTab.LOCATION -> LocationTab(state, modifier)
        ShiJiTab.DEVICE -> DeviceTab(state, modifier)
        ShiJiTab.DRIVE_TEST -> DriveTestTab(state, modifier)
        ShiJiTab.SPEED_TEST -> SpeedTestTab(state, modifier, onSpeedConfig)
    }
}
