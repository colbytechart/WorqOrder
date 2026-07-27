package worq.order.export.xlsx

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot

/**
 * Focused OOXML writer for WorqOrder's one-sheet, text-only export workbooks.
 *
 * The package deliberately omits formulas, macros, external links, shared strings, document
 * properties, and existing-workbook editing. Every visible value comes unchanged from the
 * canonical export snapshot.
 */
class XlsxWorkbookWriter {
    fun write(snapshot: ExportSnapshot): ByteArray {
        require(snapshot.schemaVersion == ExportSchema.VERSION) {
            "Unsupported export schema version ${snapshot.schemaVersion}"
        }
        val worksheetName = worksheetName(snapshot)
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.addXml("[Content_Types].xml", contentTypesXml())
                zip.addXml("_rels/.rels", packageRelationshipsXml())
                zip.addXml("xl/workbook.xml", workbookXml(worksheetName))
                zip.addXml(
                    "xl/_rels/workbook.xml.rels",
                    workbookRelationshipsXml(),
                )
                zip.addXml("xl/styles.xml", stylesXml())
                zip.addXml("xl/worksheets/sheet1.xml", worksheetXml(snapshot))
            }
            output.toByteArray()
        }
    }

    fun worksheetName(snapshot: ExportSnapshot): String =
        "WorqOrder_${snapshot.workDate}"

    private fun contentTypesXml(): String =
        xmlDocument(
            """
            <Types xmlns="$CONTENT_TYPES_NAMESPACE">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
            """,
        )

    private fun packageRelationshipsXml(): String =
        xmlDocument(
            """
            <Relationships xmlns="$PACKAGE_RELATIONSHIPS_NAMESPACE">
              <Relationship Id="rId1" Type="$OFFICE_DOCUMENT_RELATIONSHIP" Target="xl/workbook.xml"/>
            </Relationships>
            """,
        )

    private fun workbookXml(worksheetName: String): String =
        xmlDocument(
            """
            <workbook xmlns="$SPREADSHEET_NAMESPACE" xmlns:r="$DOCUMENT_RELATIONSHIPS_NAMESPACE">
              <sheets>
                <sheet name="${worksheetName.escapeXmlAttribute()}" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
            """,
        )

    private fun workbookRelationshipsXml(): String =
        xmlDocument(
            """
            <Relationships xmlns="$PACKAGE_RELATIONSHIPS_NAMESPACE">
              <Relationship Id="rId1" Type="$WORKSHEET_RELATIONSHIP" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="$STYLES_RELATIONSHIP" Target="styles.xml"/>
            </Relationships>
            """,
        )

    private fun stylesXml(): String =
        xmlDocument(
            """
            <styleSheet xmlns="$SPREADSHEET_NAMESPACE">
              <fonts count="2">
                <font><sz val="11"/><name val="Calibri"/><family val="2"/></font>
                <font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>
              </fonts>
              <fills count="2">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
              </fills>
              <borders count="1">
                <border><left/><right/><top/><bottom/><diagonal/></border>
              </borders>
              <cellStyleXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
              </cellStyleXfs>
              <cellXfs count="2">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
              </cellXfs>
              <cellStyles count="1">
                <cellStyle name="Normal" xfId="0" builtinId="0"/>
              </cellStyles>
              <dxfs count="0"/>
              <tableStyles count="0" defaultTableStyle="TableStyleMedium2" defaultPivotStyle="PivotStyleLight16"/>
            </styleSheet>
            """,
        )

    private fun worksheetXml(snapshot: ExportSnapshot): String {
        val rowValues =
            buildList {
                add(ExportSchema.headers)
                snapshot.rows.forEach { add(it.values) }
            }
        val lastRow = rowValues.size
        return buildString {
            append(XML_DECLARATION)
            append("<worksheet xmlns=\"")
            append(SPREADSHEET_NAMESPACE)
            append("\">")
            append("<dimension ref=\"A1:I")
            append(lastRow)
            append("\"/>")
            append("<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>")
            append("<sheetFormatPr defaultRowHeight=\"15\"/>")
            append("<sheetData>")
            rowValues.forEachIndexed { rowIndex, values ->
                val rowNumber = rowIndex + 1
                append("<row r=\"")
                append(rowNumber)
                append("\">")
                values.forEachIndexed { columnIndex, value ->
                    appendInlineStringCell(
                        reference = "${columnName(columnIndex)}$rowNumber",
                        value = value,
                        header = rowIndex == 0,
                    )
                }
                append("</row>")
            }
            append("</sheetData>")
            append("</worksheet>")
        }
    }

    private fun StringBuilder.appendInlineStringCell(
        reference: String,
        value: String,
        header: Boolean,
    ) {
        append("<c r=\"")
        append(reference)
        append("\" t=\"inlineStr\"")
        if (header) {
            append(" s=\"1\"")
        }
        append("><is><t xml:space=\"preserve\">")
        append(value.toSpreadsheetText().escapeXmlText())
        append("</t></is></c>")
    }

    private fun columnName(zeroBasedIndex: Int): String {
        require(zeroBasedIndex in 0 until ExportSchema.headers.size)
        return ('A'.code + zeroBasedIndex).toChar().toString()
    }

    private fun ZipOutputStream.addXml(
        path: String,
        contents: String,
    ) {
        val entry =
            ZipEntry(path).apply {
                time = DETERMINISTIC_ZIP_TIME_MILLIS
            }
        putNextEntry(entry)
        write(contents.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun xmlDocument(body: String): String =
        XML_DECLARATION + body.trimIndent().replace("\r\n", "\n")

    private fun String.toSpreadsheetText(): String {
        val escapedReservedSequences =
            RESERVED_ESCAPE_PATTERN.replace(this) { match ->
                "_x005F_${match.value.drop(1)}"
            }
        return buildString(escapedReservedSequences.length) {
            escapedReservedSequences.forEach { character ->
                when {
                    character == '\r' -> append("_x000D_")
                    character == '\t' || character == '\n' -> append(character)
                    character.code < 0x20 ->
                        append(
                            String.format(
                                Locale.ROOT,
                                "_x%04X_",
                                character.code,
                            ),
                        )
                    else -> append(character)
                }
            }
        }
    }

    private fun String.escapeXmlText(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun String.escapeXmlAttribute(): String =
        escapeXmlText()
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private companion object {
        const val XML_DECLARATION =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        const val CONTENT_TYPES_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/content-types"
        const val PACKAGE_RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/package/2006/relationships"
        const val SPREADSHEET_NAMESPACE =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        const val DOCUMENT_RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val OFFICE_DOCUMENT_RELATIONSHIP =
            "$DOCUMENT_RELATIONSHIPS_NAMESPACE/officeDocument"
        const val WORKSHEET_RELATIONSHIP =
            "$DOCUMENT_RELATIONSHIPS_NAMESPACE/worksheet"
        const val STYLES_RELATIONSHIP =
            "$DOCUMENT_RELATIONSHIPS_NAMESPACE/styles"
        const val DETERMINISTIC_ZIP_TIME_MILLIS = 0L
        val RESERVED_ESCAPE_PATTERN =
            Regex("""_x[0-9A-Fa-f]{4}_""")
    }
}
