package worq.order.backup

import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes the logical data document directly to an [OutputStream]. It intentionally avoids building
 * a second JSON tree or a giant UTF-8 byte array while producing an archive.
 */
class PortableBackupStreamingJsonWriter {
    suspend fun writeData(
        data: PortableBackupDataV1,
        output: OutputStream,
    ) {
        val writer =
            BufferedWriter(
                OutputStreamWriter(
                    CancellationCheckingOutputStream(
                        delegate = output,
                        coroutineContext = currentCoroutineContext(),
                    ),
                    StandardCharsets.UTF_8,
                ),
            )
        writeObject(writer) {
            integer("dataModelVersion", data.dataModelVersion)
            array("clients", data.clients, ::writeClient)
            array("consultants", data.consultants, ::writeConsultant)
            array("tags", data.tags, ::writeTag)
            array("tasks", data.tasks, ::writeTask)
            objectValue("settings") { writeSettings(writer, data.settings) }
            nullableObject("selection", data.selection) { selection ->
                writeSelection(writer, selection)
            }
        }
        writer.flush()
    }

    private fun writeClient(
        writer: BufferedWriter,
        value: PortableBackupClientV1,
    ) {
        writeObject(writer) {
            string("id", value.id)
            string("name", value.name)
            string("canonicalName", value.canonicalName)
            boolean("isActive", value.isActive)
            long("createdAtEpochMs", value.createdAtEpochMs)
            long("updatedAtEpochMs", value.updatedAtEpochMs)
            nullableLong("archivedAtEpochMs", value.archivedAtEpochMs)
        }
    }

    private fun writeConsultant(
        writer: BufferedWriter,
        value: PortableBackupConsultantV1,
    ) {
        writeObject(writer) {
            string("id", value.id)
            string("name", value.name)
            string("canonicalName", value.canonicalName)
            boolean("isActive", value.isActive)
            long("createdAtEpochMs", value.createdAtEpochMs)
            long("updatedAtEpochMs", value.updatedAtEpochMs)
            nullableLong("archivedAtEpochMs", value.archivedAtEpochMs)
        }
    }

    private fun writeTag(
        writer: BufferedWriter,
        value: PortableBackupTagV1,
    ) {
        writeObject(writer) {
            string("id", value.id)
            string("category", value.category)
            string("text", value.text)
            string("normalizedText", value.normalizedText)
            long("createdAtEpochMs", value.createdAtEpochMs)
            long("updatedAtEpochMs", value.updatedAtEpochMs)
        }
    }

    private fun writeTask(
        writer: BufferedWriter,
        value: PortableBackupTaskV1,
    ) {
        writeObject(writer) {
            string("id", value.id)
            string("seriesId", value.seriesId)
            string("clientId", value.clientId)
            nullableString("consultantId", value.consultantId)
            string("consultantNameSnapshot", value.consultantNameSnapshot)
            string("description", value.description)
            string("hardwareSoftwarePurchases", value.hardwareSoftwarePurchases)
            string("workType", value.workType)
            nullableString("billingStatus", value.billingStatus)
            nullableString("mileage", value.mileage)
            string("notes", value.notes)
            long("workDateEpochDay", value.workDateEpochDay)
            string("zoneId", value.zoneId)
            long("createdAtEpochMs", value.createdAtEpochMs)
            long("updatedAtEpochMs", value.updatedAtEpochMs)
            array("tagSnapshots", value.tagSnapshots, ::writeSnapshot)
            array("intervals", value.intervals, ::writeInterval)
        }
    }

    private fun writeSnapshot(
        writer: BufferedWriter,
        value: PortableBackupTaskTagSnapshotV1,
    ) {
        writeObject(writer) {
            string("id", value.id)
            string("category", value.category)
            string("text", value.text)
            nullableString("sourceTagId", value.sourceTagId)
            integer("selectionOrder", value.selectionOrder)
            long("createdAtEpochMs", value.createdAtEpochMs)
        }
    }

    private fun writeInterval(
        writer: BufferedWriter,
        value: PortableBackupIntervalV1,
    ) {
        writeObject(writer) {
            string("id", value.id)
            string("taskId", value.taskId)
            long("startEpochMs", value.startEpochMs)
            nullableLong("stopEpochMs", value.stopEpochMs)
            boolean("wasManuallyEdited", value.wasManuallyEdited)
            long("createdAtEpochMs", value.createdAtEpochMs)
            long("updatedAtEpochMs", value.updatedAtEpochMs)
        }
    }

    private fun writeSettings(
        writer: BufferedWriter,
        value: PortableBackupSettingsV1,
    ) {
        writeObject(writer) {
            string("themeMode", value.themeMode)
            string("timeZoneMode", value.timeZoneMode)
            nullableString("manualZoneId", value.manualZoneId)
            string("defaultExportDestination", value.defaultExportDestination)
            nullableObject("lastExportAttempt", value.lastExportAttempt) { attempt ->
                writeLastExportAttempt(writer, attempt)
            }
            nullableString("selectedConsultantId", value.selectedConsultantId)
            string("landscapeHandedness", value.landscapeHandedness)
        }
    }

    private fun writeLastExportAttempt(
        writer: BufferedWriter,
        value: PortableBackupLastExportAttemptV1,
    ) {
        writeObject(writer) {
            string("destination", value.destination)
            long("workDateEpochDay", value.workDateEpochDay)
            long("attemptedAtEpochMs", value.attemptedAtEpochMs)
            string("outcome", value.outcome)
            nullableString("errorCategory", value.errorCategory)
        }
    }

    private fun writeSelection(
        writer: BufferedWriter,
        value: PortableBackupSelectionV1,
    ) {
        writeObject(writer) {
            string("taskId", value.taskId)
            string("seriesId", value.seriesId)
            long("selectedOnEpochDay", value.selectedOnEpochDay)
            string("selectedInZoneId", value.selectedInZoneId)
        }
    }

    private fun writeObject(
        writer: BufferedWriter,
        block: JsonObjectWriter.() -> Unit,
    ) {
        writer.write("{")
        JsonObjectWriter(writer).block()
        writer.write("}")
    }

    private fun <T> writeArray(
        writer: BufferedWriter,
        values: List<T>,
        writeValue: (BufferedWriter, T) -> Unit,
    ) {
        writer.write("[")
        values.forEachIndexed { index, value ->
            if (index != 0) writer.write(",")
            writeValue(writer, value)
        }
        writer.write("]")
    }

    private inner class JsonObjectWriter(
        private val writer: BufferedWriter,
    ) {
        private var hasPreviousField = false

        fun string(name: String, fieldValue: String) = value(name) { quoted(fieldValue) }

        fun nullableString(name: String, fieldValue: String?) =
            value(name) { if (fieldValue == null) nullValue() else quoted(fieldValue) }

        fun boolean(name: String, fieldValue: Boolean) = value(name) { writer.write(fieldValue.toString()) }

        fun integer(name: String, fieldValue: Int) = value(name) { writer.write(fieldValue.toString()) }

        fun long(name: String, fieldValue: Long) = value(name) { writer.write(fieldValue.toString()) }

        fun nullableLong(name: String, fieldValue: Long?) =
            value(name) { if (fieldValue == null) nullValue() else writer.write(fieldValue.toString()) }

        fun objectValue(name: String, writeObject: () -> Unit) = value(name, writeObject)

        fun <T> nullableObject(
            name: String,
            fieldValue: T?,
            writeObject: (T) -> Unit,
        ) = value(name) { if (fieldValue == null) nullValue() else writeObject(fieldValue) }

        fun <T> array(
            name: String,
            values: List<T>,
            writeValue: (BufferedWriter, T) -> Unit,
        ) = value(name) { writeArray(writer, values, writeValue) }

        private fun value(
            name: String,
            writeValue: () -> Unit,
        ) {
            if (hasPreviousField) writer.write(",")
            hasPreviousField = true
            quoted(name)
            writer.write(":")
            writeValue()
        }

        private fun quoted(value: String) {
            writer.write(JsonPrimitive(value).toString())
        }

        private fun nullValue() {
            writer.write("null")
        }
    }

    /**
     * BufferedWriter emits chunks, so this checks cancellation without retaining the JSON payload
     * or requiring every DTO serializer helper to become suspend.
     */
    private class CancellationCheckingOutputStream(
        private val delegate: OutputStream,
        private val coroutineContext: CoroutineContext,
    ) : OutputStream() {
        override fun write(value: Int) {
            coroutineContext.ensureActive()
            delegate.write(value)
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            coroutineContext.ensureActive()
            delegate.write(bytes, offset, length)
        }

        override fun flush() {
            coroutineContext.ensureActive()
            delegate.flush()
        }
    }
}
