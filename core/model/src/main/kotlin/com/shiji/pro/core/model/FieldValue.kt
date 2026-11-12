package com.shiji.pro.core.model

/**
 * 全字段统一三态包装（设计稿 §3）。UI 必须依据 [origin] 显示对应徽标，
 * 禁止把估算值当成实测值、禁止用占位值伪装实测值。
 *
 * 不变式（由 [FieldValue] 工厂与单测强制）：
 * - [Origin.UNAVAILABLE] 时 [value] 必须为 null，[reason] 不得为空；
 * - 其余三态 [value] 必须非 null；
 * - [Origin.ESTIMATED] 时 [note] 建议写明「估算值，仅供参考」（由 UI 统一附声明兜底）。
 *
 * @param note 可选补充说明，例如换算公式、估算模型名称、N/A 的具体原因。
 */
data class FieldValue<out T> private constructor(
    val value: T?,
    val origin: Origin,
    val reason: String? = null,
    val note: String? = null,
) {
    /** UI 显示文本：N/A 一律显示字面 "N/A"，不暴露任何占位数值。 */
    fun displayText(render: (T) -> String = { it.toString() }): String =
        if (value == null) NA_TEXT else render(value)

    /** 是否可作为实测值参与统计/曲线绘制：仅 MEASURED 与 DERIVED 可以。 */
    fun isPlottable(): Boolean = value != null && origin != Origin.UNAVAILABLE

    companion object {
        const val NA_TEXT = "N/A"

        fun <T> measured(value: T, note: String? = null): FieldValue<T> =
            FieldValue(value, Origin.MEASURED, null, note)

        fun <T> derived(value: T, note: String? = null): FieldValue<T> =
            FieldValue(value, Origin.DERIVED, null, note)

        fun <T> estimated(value: T, note: String? = null): FieldValue<T> =
            FieldValue(value, Origin.ESTIMATED, null, note)

        /**
         * 取不到时统一走这里：value 强制为 null、reason 必填。
         * 这样从构造入口就杜绝「用 0 / -1 / 默认值伪装实测值」。
         */
        fun <T> unavailable(reason: String): FieldValue<T> {
            require(reason.isNotBlank()) { "N/A 必须给出原因（权限 / 机型 / 系统限制）" }
            return FieldValue(null, Origin.UNAVAILABLE, reason, null)
        }
    }
}
