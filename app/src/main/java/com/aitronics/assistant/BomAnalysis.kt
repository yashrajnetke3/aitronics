package com.aitronics.assistant

import java.math.BigDecimal
import java.util.Locale

/** CSV only. Keep part identifiers case-sensitive and quantities separate by unit. */
internal object BomAnalysis {
    private const val maxRows = 10000
    fun analyze(input: String): String {
        require(input.length <= 1024 * 1024) { "Use a CSV smaller than 1 MiB." }
        val rows = rows(input.removePrefix("\uFEFF")).iterator()
        require(rows.hasNext()) { "The CSV is empty." }
        val header = rows.next().map { it.trim().lowercase(Locale.ROOT).replace('_', ' ') }
        fun column(vararg aliases: String, optional: Boolean = false): Int {
            val matches = header.indices.filter { header[it] in aliases }
            require(matches.size == 1 || (optional && matches.isEmpty())) {
                "Use one column for ${aliases.first()}."
            }
            return matches.firstOrNull() ?: -1
        }
        val part = column("part number", "part no", "mpn", "part")
        val quantity = column("quantity", "qty")
        val unit = column("unit", "uom", optional = true)
        val totals = linkedMapOf<Pair<String,String>, BigDecimal>()
        var count = 0
        val numeric = Regex("""\d{1,12}(?:\.\d{1,6})?""")
        while (rows.hasNext()) {
            val row = rows.next()
            count++
            require(count <= maxRows) { "Limit the CSV to $maxRows item rows." }
            require(row.size == header.size) { "Item $count has the wrong number of fields." }
            val id = row[part].trim()
            val raw = row[quantity].trim()
            require(id.isNotEmpty() && id.length <= 200) { "Item $count needs a valid part number." }
            require(numeric.matches(raw)) { "Item $count needs a nonnegative decimal quantity." }
            val key = id to if (unit >= 0) row[unit].trim() else ""
            totals[key] = (totals[key] ?: BigDecimal.ZERO) + raw.toBigDecimal()
        }
        require(count > 0) { "The CSV has no item rows." }
        return buildString {
            append("$count item rows · ${totals.size} part/unit groups")
            for ((key, total) in totals.entries.take(100)) {
                append("\n").append(key.first).append("  ·  ").append(total.stripTrailingZeros().toPlainString())
                append(" ").append(key.second.ifEmpty { "(unit unspecified)" })
            }
            if (totals.size > 100) append("\nShowing the first 100 groups.")
        }
    }

    private fun rows(text: String): Sequence<List<String>> = sequence {
        val fields = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        while (i < text.length) {
            val ch = text[i++]
            if (quoted) {
                if (ch != '"') value.append(ch)
                else if (i < text.length && text[i] == '"') { value.append('"'); i++ }
                else { quoted = false; closed = true }
            } else when (ch) {
                ',' -> { fields.add(value.toString()); value.clear(); closed = false }
                '\r', '\n' -> {
                    if (ch == '\r' && i < text.length && text[i] == '\n') i++
                    if (fields.isNotEmpty() || value.isNotBlank() || closed) yield(fields.toList() + value.toString())
                    fields.clear(); value.clear(); closed = false
                }
                '"' -> { require(value.isEmpty() && !closed) { "Unexpected quotation mark in CSV." }; quoted = true }
                else -> { require(!closed) { "Unexpected text after a quoted field." }; value.append(ch) }
            }
            require(fields.size <= 128 && value.length <= 8192) { "CSV field or column limit exceeded." }
        }
        require(!quoted) { "Unclosed quotation mark in CSV." }
        if (fields.isNotEmpty() || value.isNotBlank() || closed) yield(fields.toList() + value.toString())
    }
}
