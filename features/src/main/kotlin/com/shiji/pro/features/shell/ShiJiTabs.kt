package com.shiji.pro.features.shell

import com.shiji.pro.core.model.Origin

/**
 * 七个主标签的稳定契约（§2.4）。
 * 用枚举而非字符串列表：标签 id 稳定，便于后续 Navigation / 状态保存 / 报表引用。
 * label 用字面量而非 R.string：本模块为 library，避免跨模块资源 id 引用；
 * 中英文实际显示由 :app 在 P10 通过 locale 配置注入（当前为中文默认）。
 * 顶栏动作（双卡对比 / 日志中心 / 设置）不占主标签，由 [TopBarAction] 承载。
 */
enum class ShiJiTab(val label: String, val route: String) {
    SLOT1("卡槽1", "slot1"),
    SLOT2("卡槽2", "slot2"),
    WIFI("WiFi", "wifi"),
    LOCATION("位置", "location"),
    DEVICE("设备", "device"),
    DRIVE_TEST("覆盖测试", "drivetest"),
    SPEED_TEST("测速", "speedtest"),
}

/** 顶栏动作（不占 7 个主标签）。 */
enum class TopBarAction(val label: String) {
    REFRESH("刷新"),
    COMPARE_SIMS("双卡对比"),
    LOG_CENTER("日志中心"),
    SETTINGS("设置"),
}

/** 页面骨架的数据契约：P0 用占位，P7–P10 由各 feature 的 ViewModel 提供真实 UiState。 */
data class SkeletonField(
    val label: String,
    val value: String,
    val origin: Origin,
)

data class SkeletonSection(
    val title: String,
    val fields: List<SkeletonField>,
)
