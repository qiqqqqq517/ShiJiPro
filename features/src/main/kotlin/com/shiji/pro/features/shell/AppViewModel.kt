package com.shiji.pro.features.shell

import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey

/**
 * UI 状态映射（纯函数，:features 不依赖 :data）。
 * 真实 ViewModel 位于 :app 装配层；各 Screen 只接收 [AppState] 只读渲染。
 */

/** 某采集器是否可用（不可用则显示 PermissionGate 降级提示）。 */
fun AppState.capabilityOf(key: CollectorKey): CapabilityState? = capabilities[key]

/** 是否有任意蜂窝槽数据。 */
fun AppState.hasCellularData(): Boolean = cellularSlots.isNotEmpty()

/** 分页/窗口切换的标签页标题到稳定 key 的映射。 */
fun ShiJiTab.windowKeys(): List<String> = listOf("20s", "1min", "5min")
