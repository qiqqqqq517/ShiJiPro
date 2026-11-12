package com.shiji.pro.core.common

/**
 * RFC 4180 CSV 编解码（设计稿 §8 导出模块，纯 Kotlin 无第三方依赖）。
 *
 * - 字段含逗号 / 双引号 / CR / LF 时用双引号包裹，内部双引号转义为两个双引号；
 * - 输出 UTF-8；BOM 由调用方（SAF 写入侧）按需添加，本类不擅自加 BOM；
 * - decode 支持 CRLF / LF 与引号内换行，用于「导出后回读自检」单测。
 */
object CsvCodec {

    fun encode(header: List<String>, rows: List<List<String?>>): String {
        val sb = StringBuilder()
        sb.append(encodeRow(header)).append('\n')
        rows.forEach { sb.append(encodeRow(it)).append('\n') }
        return sb.toString()
    }

    fun encodeRow(fields: List<String?>): String =
        fields.joinToString(",") { field -> escape(field) }

    private fun escape(field: String?): String {
        if (field == null) return ""
        val needsQuote = field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        if (!needsQuote) return field
        return "\"${field.replace("\"", "\"\"")}\""
    }

    /** 解析 UTF-8 CSV 文本；用于导出文件回读自检。 */
    fun decode(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    when (c) {
                        '"' -> {
                            if (i + 1 < text.length && text[i + 1] == '"') {
                                cell.append('"'); i++
                            } else inQuotes = false
                        }
                        else -> cell.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(cell.toString()); cell.clear() }
                c == '\r' -> { /* CRLF 的 CR 忽略，由 LF 收行 */ }
                c == '\n' -> {
                    row.add(cell.toString()); cell.clear()
                    result.add(row.toList()); row.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            result.add(row.toList())
        }
        return result
    }
}
