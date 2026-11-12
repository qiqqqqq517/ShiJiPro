package com.shiji.pro.data.collector.cellular

import com.shiji.pro.core.model.FieldValue

/**
 * Android Telephony 奇异值处理（设计稿 §10.4：取不到的字段显示 N/A，严禁编造数值）。
 *
 * `CellInfo.UNAVAILABLE` = Int.MAX_VALUE；个别厂商实现也会用 Int.MIN_VALUE。
 * ⚠️ **不能用 -1 或 0 当通用奇异值**：SINR(getRssnr)、EcIo、dBm、RSRQ 的
 * 合法值本身可以是负数，0 也是合法测量值。只把 MAX/MIN 视作「取不到」。
 *
 * 扩展函数放在顶层，供各 Parser 直接 import 使用。
 */
object Sentinels {
    const val UNAVAILABLE = Int.MAX_VALUE
    const val UNKNOWN_MAC = "02:00:00:00:00:00"
    const val UNKNOWN_SSID = "<unknown ssid>"
}

fun Int?.isUnknownInt(): Boolean =
    this == null || this == Int.MAX_VALUE || this == Int.MIN_VALUE

fun Long?.isUnknownLong(): Boolean =
    this == null || this == Long.MAX_VALUE || this == Long.MIN_VALUE

fun unknownReason(field: String): String = "$field 取不到（CellInfo.UNAVAILABLE / 机型未上报）"

/** 把可能为奇异值的 Int 包装成三态字段；奇异值一律 N/A，不落任何占位数值。 */
fun Int?.toField(field: String): FieldValue<Int> =
    if (isUnknownInt()) FieldValue.unavailable(unknownReason(field)) else FieldValue.measured(this!!)

fun Long?.toLongField(field: String): FieldValue<Long> =
    if (isUnknownLong()) FieldValue.unavailable(unknownReason(field)) else FieldValue.measured(this!!)

fun Float?.toFloatField(field: String): FieldValue<Float> =
    if (this == null || this.isNaN() || this.isInfinite()) {
        FieldValue.unavailable(unknownReason(field))
    } else FieldValue.measured(this)

fun String?.toTextField(field: String): FieldValue<String> =
    if (isNullOrBlank() || this == Sentinels.UNKNOWN_MAC || this == Sentinels.UNKNOWN_SSID) {
        FieldValue.unavailable("$field 取不到或系统已脱敏")
    } else FieldValue.measured(this!!)
