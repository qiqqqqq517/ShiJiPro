package com.shiji.pro.core.domain

import com.shiji.pro.core.common.AppError
import com.shiji.pro.core.model.AppState
import com.shiji.pro.core.model.CapabilityState
import com.shiji.pro.core.model.CollectorKey
import kotlinx.coroutines.flow.Flow

/**
 * 统一数据采集接口（设计稿 §10.4：采集封装统一接口，按版本与系统能力降级读取）。
 *
 * 关键约束：
 * - 采集层实现本接口（在 :data），domain 只定义契约，不含任何 android.* 依赖；
 * - [capability] 是权限/API/机型降级的唯一出口，取不到时 available=false 且 reason 必填；
 * - [observe] 返回冷/热 Flow，由 SamplingScheduler 按配置节流；禁止在 Flow 内抛异常，
 *   失败以 [AppError] 形式体现在 CapabilityState 或快照的 FieldValue.unavailable 中。
 */
interface Collector<T> {
    val key: CollectorKey

    /** 当前能力状态（权限 / API 版本 / 机型）；实现方需在权限变化时重新发出。 */
    fun capability(): Flow<CapabilityState>

    /** 采集数据流；不可用时发出空快照而非崩溃或伪造数值。 */
    fun observe(): Flow<T>
}

/**
 * 把单个采集器的输出合并进 [AppState] 的归约器契约。
 * 由 :data 的 AppStateRepositoryImpl 注入，UI 层不参与合并逻辑。
 */
interface SnapshotReducer {
    fun reduce(current: AppState, key: CollectorKey, snapshot: Any?, nowMs: Long): AppState
}

/** 能力闸门：SDK_INT / 权限 / 机型能力的判定统一入口（实现在 :data）。 */
interface CapabilityGate {
    fun evaluate(key: CollectorKey): CapabilityState
}
