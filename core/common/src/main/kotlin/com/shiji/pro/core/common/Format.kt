package com.shiji.pro.core.common

import com.shiji.pro.core.model.FieldValue
import java.util.Locale

/**
 * 显示层数值格式化（缺陷 D4：设备页把裸字节数与全精度浮点数直接铺到界面上）。
 *
 * 纪律（务必遵守）：
 * 1. **只影响显示，不改变数据** —— 领域模型里的 Long / Double / Float 原值保持不动，
 *    Collector 严禁把格式化结果当字符串写进 FieldValue（会破坏 CSV 导出与后续统计）。
 *    格式化只允许发生在渲染侧（UI renderField）。
 * 2. **不 import 任何 android.\* 类**，保证可在 JVM 单元测试中直接跑。
 * 3. **小数点固定用 [Locale.US]**，避免中文环境把 6.11 输出成 "6,11"。
 *
 * 舍入规则（与 [FormatTest] 断言一一对应）：
 * [String.format] 的 `%f` 转换按 **HALF_UP（四舍五入，远离零）** 取位，
 * 不是银行家舍入 —— 所以 463.995 这类会进位；而 doubleShort / floatShort 在
 * 取位之后**再去掉无意义尾零**（"144.0" → "144"、"6.10" → "6.1"）。
 */
object Format {

    /** 1024 进制单位表（IEC）。 */
    private val BINARY_UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")

    /** 1000 进制单位表（SI），供 unit = "decimal" 时使用。 */
    private val DECIMAL_UNITS = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB")

    /** 字节数取不到 / 非正值时的显示回落，复用 FieldValue 的同一字面量，避免两处漂移。 */
    private const val NA = FieldValue.NA_TEXT

    /**
     * 字节数转人读串，默认 1024 进制，保留 2 位小数（不裁尾零，保证列对齐）。
     *
     * 实测样例（小米 22041216UC）：
     * - 7848685568 → `7.31 GiB`
     * - 3129905152 → `2.91 GiB`
     * - 498205687808 → `463.99 GiB`（= 463.990204 GiB，HALF_UP 到 2 位）
     * - 271640363008 → `252.98 GiB`（= 252.984802 GiB）
     *
     * @param bytes 原字节数；null 或 <= 0 视为无效，返回 null 由调用方回落 N/A，
     *              **绝不输出 "0.00 B" 之类的伪值**。
     * @param unit  "binary"（1024，IEC 后缀）或 "decimal"（1000，SI 后缀）；
     *              其它取值按 binary 处理。
     */
    fun bytesHuman(bytes: Long?, unit: String = "binary"): String? {
        if (bytes == null || bytes <= 0L) return null
        val decimal = unit.equals("decimal", ignoreCase = true)
        val base = if (decimal) 1000.0 else 1024.0
        val units = if (decimal) DECIMAL_UNITS else BINARY_UNITS

        var value = bytes.toDouble()
        var idx = 0
        while (value >= base && idx < units.size - 1) {
            value /= base
            idx++
        }
        // 不足 1 KiB：直接给整数原值，不写 "512.00 B" 这种无意义小数。
        return if (idx == 0) "$bytes ${units[0]}" else "${fixed(value, 2)} ${units[idx]}"
    }

    /**
     * Double 短格式：最多 [maxDecimals] 位小数，去掉无意义尾零。
     * 6.10598541197403 → `6.11`；144.0 → `144`；2.50 → `2.5`。
     * NaN / Infinity 返回 `N/A`（不显示伪值）。
     */
    fun doubleShort(v: Double, maxDecimals: Int = 2): String {
        if (v.isNaN() || v.isInfinite()) return NA
        return trimZeros(fixed(v, maxDecimals))
    }

    /**
     * Float 短格式：最多 [maxDecimals] 位小数，去掉无意义尾零。
     * 144.00002f → `144`（取 1 位小数得 144.0，尾零裁掉）；403.5f → `403.5`。
     * NaN / Infinity 返回 `N/A`。
     */
    fun floatShort(v: Float, maxDecimals: Int = 1): String {
        if (v.isNaN() || v.isInfinite()) return NA
        return trimZeros(fixed(v.toDouble(), maxDecimals))
    }

    /** 固定小数位（HALF_UP），统一走 [Locale.US]，全文件唯一的小数点出口。 */
    private fun fixed(v: Double, decimals: Int): String {
        val d = decimals.coerceIn(0, 9)
        return String.format(Locale.US, "%.${d}f", v)
    }

    /** 去掉无意义尾零与悬空小数点；顺带把 "-0" 归一成 "0"。 */
    private fun trimZeros(s: String): String {
        var out = s
        if (out.contains('.')) {
            while (out.endsWith("0")) out = out.dropLast(1)
            if (out.endsWith(".")) out = out.dropLast(1)
        }
        return if (out == "-0") "0" else out
    }
}

/**
 * 三态安全的显示文本：[FieldValue] 取不到（[com.shiji.pro.core.model.Origin.UNAVAILABLE]
 * 时 value 恒为 null）一律返回 "N/A"，**绝不渲染任何占位值**；
 * 有效值才交给 [render] 格式化。
 *
 * 与 [FieldValue.displayText] 的区别：后者默认 `{ it.toString() }`，正是 D4 的根因
 * （把 7848685568 原样打到界面上）；本扩展强制调用方给出可读渲染器。
 */
fun <T> FieldValue<T>.displayTextWith(render: (T) -> String): String {
    val v = value ?: return FieldValue.NA_TEXT
    return render(v)
}
