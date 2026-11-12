package com.shiji.pro.core.common

/**
 * 敏感字段脱敏（设计稿 §10.5 隐私合规）。
 *
 * 规则：默认只保留末 4 位，其余用 • 替代；短串（≤4 位）全遮蔽。
 * IMSI / ICCID / 电话号码 / 序列号 / MAC 均走此口径。
 * 日志与 CSV 导出默认写入脱敏值；只有用户显式勾选「导出明文」才允许传 raw=true，
 * 且该开关在 UI 上必须二次确认（P10 实现）。
 */
object Masking {

    const val MASK_CHAR = '•'

    fun mask(raw: String?, keepTail: Int = 4): String {
        if (raw.isNullOrBlank()) return ""
        val value = raw.trim()
        if (value.length <= keepTail) return MASK_CHAR.toString().repeat(value.length)
        val visible = value.takeLast(keepTail)
        return MASK_CHAR.toString().repeat(value.length - keepTail) + visible
    }

    /** 只显示国家码 + 末 4 位，中间全部遮蔽（用于 IMSI/ICCID 的 UI 展示）。 */
    fun maskKeepPrefixAndTail(raw: String?, prefix: Int = 3, tail: Int = 4): String {
        if (raw.isNullOrBlank()) return ""
        val value = raw.trim()
        if (value.length <= prefix + tail) return MASK_CHAR.toString().repeat(value.length)
        return value.take(prefix) +
            MASK_CHAR.toString().repeat(value.length - prefix - tail) +
            value.takeLast(tail)
    }

    /** MAC / BSSID 脱敏：保留前三段厂商前缀，其余段用 •• 替代。 */
    fun maskMac(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val parts = raw.trim().split(':')
        if (parts.size != 6) return mask(raw)
        return "${parts[0]}:${parts[1]}:${parts[2]}:••:••:••"
    }
}
