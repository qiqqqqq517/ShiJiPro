package com.shiji.pro.core.model

/**
 * 趋势曲线采样点（P14：把「空图占位」接成真实曲线）。
 *
 * 与 `core:common/Sampling.Point` 同构，但放在 `:core:model` 是因为要进 [AppState]
 * （模块依赖方向是 common → model，model 不能反向依赖 common）。
 * 采样降频仍由 common 的 `Sampling.downsample/withinWindow` 完成。
 */
data class TrendPoint(val timestampMs: Long, val value: Double)
