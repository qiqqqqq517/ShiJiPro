package com.shiji.pro.core.model

/**
 * 数据字段来源标注（设计稿 §3）。所有界面字段必须携带本标注，禁止用估算值伪装成实测值。
 *
 * - [MEASURED]：系统公开 API / 本地传感器直接返回的原始测量值。
 * - [DERIVED]：由实测字段经确定性公式或查表得出（如 ECI = TAC<<16|CI、EARFCN→频段）。
 * - [ESTIMATED]：模型推算（如基站距离、质量评级），界面必须附「估算值，仅供参考」。
 * - [UNAVAILABLE]：取不到；界面显示 N/A 并给出原因，严禁伪造。
 *
 * P0 先落此枚举作为跨模块共享契约；P1 在 :core:model 补齐 FieldValue<T> 与其余领域模型。
 */
enum class Origin {
    MEASURED,
    DERIVED,
    ESTIMATED,
    UNAVAILABLE,
}
