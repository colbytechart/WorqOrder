package worq.order.export.csv

import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

class CsvSerializer {
    fun serialize(snapshot: ExportSnapshot): String =
        buildString {
            appendRecord(ExportSchema.headers)
            snapshot.rows.forEach { row ->
                appendRecord(row.values)
            }
        }

    private fun StringBuilder.appendRecord(fields: List<String>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) {
                append(',')
            }
            append(field.escapeCsv())
        }
        append(CRLF)
    }

    private fun String.escapeCsv(): String {
        if (none { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            return this
        }
        return buildString(length + 2) {
            append('"')
            this@escapeCsv.forEach { character ->
                if (character == '"') {
                    append("\"\"")
                } else {
                    append(character)
                }
            }
            append('"')
        }
    }

    private companion object {
        const val CRLF = "\r\n"
    }
}
