package worq.order.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Strict JSON codec for the two logical archive documents. ZIP creation/reading is deliberately
 * deferred to Milestone 51; this class has no file-system or Android dependency.
 */
object PortableBackupJsonCodec {
    private val json =
        Json {
            isLenient = false
            ignoreUnknownKeys = false
            allowSpecialFloatingPointValues = false
        }

    fun encodeManifest(manifest: PortableBackupManifestV1): String =
        manifest.toJson().toString()

    fun encodeData(data: PortableBackupDataV1): String =
        data.toJson().toString()

    fun decodeManifest(source: String): PortableBackupDecodeResult<PortableBackupManifestV1> =
        decode { parse(source).toManifest() }

    fun decodeData(source: String): PortableBackupDecodeResult<PortableBackupDataV1> =
        decode { parse(source).toData() }

    private fun <T> decode(block: () -> T): PortableBackupDecodeResult<T> =
        try {
            PortableBackupDecodeResult.Success(block())
        } catch (error: CodecFailure) {
            PortableBackupDecodeResult.Invalid(error.code, error.message.orEmpty())
        } catch (error: SerializationException) {
            PortableBackupDecodeResult.Invalid(
                PortableBackupDecodeError.MALFORMED_JSON,
                "The JSON document could not be parsed.",
            )
        } catch (error: IllegalArgumentException) {
            PortableBackupDecodeResult.Invalid(
                PortableBackupDecodeError.MALFORMED_JSON,
                "The JSON document could not be parsed.",
            )
        }

    private fun parse(source: String): JsonObject {
        if (source.length.toLong() > PortableBackupLimits.MAX_EXPANDED_BYTES) {
            fail(
                PortableBackupDecodeError.RESOURCE_LIMIT,
                "The JSON document exceeds the expanded-size limit.",
            )
        }
        StrictJsonStructureGuard(source).validate()
        return json.parseToJsonElement(source).requireObject("$")
    }

    private fun PortableBackupManifestV1.toJson(): JsonObject =
        buildJsonObject {
            putString("productId", productId)
            putInt("backupFormatVersion", backupFormatVersion)
            putString("producerVersionName", producerVersionName)
            putInt("producerVersionCode", producerVersionCode)
            putLong("createdAtEpochMs", createdAtEpochMs)
            putInt("dataModelVersion", dataModelVersion)
            putString("dataEncoding", dataEncoding)
            putString("compression", compression)
            putLong("dataByteCount", dataByteCount)
            putString("dataSha256", dataSha256)
        }

    private fun PortableBackupDataV1.toJson(): JsonObject =
        buildJsonObject {
            putInt("dataModelVersion", dataModelVersion)
            putArray("clients", clients.map { it.toJson() })
            putArray("consultants", consultants.map { it.toJson() })
            putArray("tags", tags.map { it.toJson() })
            putArray("tasks", tasks.map { it.toJson() })
            put("settings", settings.toJson())
            putNullableObject("selection", selection?.toJson())
        }

    private fun PortableBackupClientV1.toJson(): JsonObject =
        buildJsonObject {
            putString("id", id)
            putString("name", name)
            putString("canonicalName", canonicalName)
            putBoolean("isActive", isActive)
            putLong("createdAtEpochMs", createdAtEpochMs)
            putLong("updatedAtEpochMs", updatedAtEpochMs)
            putNullableLong("archivedAtEpochMs", archivedAtEpochMs)
        }

    private fun PortableBackupConsultantV1.toJson(): JsonObject =
        buildJsonObject {
            putString("id", id)
            putString("name", name)
            putString("canonicalName", canonicalName)
            putBoolean("isActive", isActive)
            putLong("createdAtEpochMs", createdAtEpochMs)
            putLong("updatedAtEpochMs", updatedAtEpochMs)
            putNullableLong("archivedAtEpochMs", archivedAtEpochMs)
        }

    private fun PortableBackupTagV1.toJson(): JsonObject =
        buildJsonObject {
            putString("id", id)
            putString("category", category)
            putString("text", text)
            putString("normalizedText", normalizedText)
            putLong("createdAtEpochMs", createdAtEpochMs)
            putLong("updatedAtEpochMs", updatedAtEpochMs)
        }

    private fun PortableBackupTaskV1.toJson(): JsonObject =
        buildJsonObject {
            putString("id", id)
            putString("seriesId", seriesId)
            putString("clientId", clientId)
            putNullableString("consultantId", consultantId)
            putString("consultantNameSnapshot", consultantNameSnapshot)
            putString("description", description)
            putString("hardwareSoftwarePurchases", hardwareSoftwarePurchases)
            putString("workType", workType)
            putNullableString("billingStatus", billingStatus)
            putNullableString("mileage", mileage)
            putString("notes", notes)
            putLong("workDateEpochDay", workDateEpochDay)
            putString("zoneId", zoneId)
            putLong("createdAtEpochMs", createdAtEpochMs)
            putLong("updatedAtEpochMs", updatedAtEpochMs)
            putArray("tagSnapshots", tagSnapshots.map { it.toJson() })
            putArray("intervals", intervals.map { it.toJson() })
        }

    private fun PortableBackupTaskTagSnapshotV1.toJson(): JsonObject =
        buildJsonObject {
            putString("id", id)
            putString("category", category)
            putString("text", text)
            putNullableString("sourceTagId", sourceTagId)
            putInt("selectionOrder", selectionOrder)
            putLong("createdAtEpochMs", createdAtEpochMs)
        }

    private fun PortableBackupIntervalV1.toJson(): JsonObject =
        buildJsonObject {
            putString("id", id)
            putString("taskId", taskId)
            putLong("startEpochMs", startEpochMs)
            putNullableLong("stopEpochMs", stopEpochMs)
            putBoolean("wasManuallyEdited", wasManuallyEdited)
            putLong("createdAtEpochMs", createdAtEpochMs)
            putLong("updatedAtEpochMs", updatedAtEpochMs)
        }

    private fun PortableBackupSettingsV1.toJson(): JsonObject =
        buildJsonObject {
            putString("themeMode", themeMode)
            putString("timeZoneMode", timeZoneMode)
            putNullableString("manualZoneId", manualZoneId)
            putString("defaultExportDestination", defaultExportDestination)
            putNullableObject("lastExportAttempt", lastExportAttempt?.toJson())
            putNullableString("selectedConsultantId", selectedConsultantId)
            putString("landscapeHandedness", landscapeHandedness)
        }

    private fun PortableBackupLastExportAttemptV1.toJson(): JsonObject =
        buildJsonObject {
            putString("destination", destination)
            putLong("workDateEpochDay", workDateEpochDay)
            putLong("attemptedAtEpochMs", attemptedAtEpochMs)
            putString("outcome", outcome)
            putNullableString("errorCategory", errorCategory)
        }

    private fun PortableBackupSelectionV1.toJson(): JsonObject =
        buildJsonObject {
            putString("taskId", taskId)
            putString("seriesId", seriesId)
            putLong("selectedOnEpochDay", selectedOnEpochDay)
            putString("selectedInZoneId", selectedInZoneId)
        }

    private fun JsonObject.toManifest(): PortableBackupManifestV1 {
        requireKeys(
            "$",
            "productId",
            "backupFormatVersion",
            "producerVersionName",
            "producerVersionCode",
            "createdAtEpochMs",
            "dataModelVersion",
            "dataEncoding",
            "compression",
            "dataByteCount",
            "dataSha256",
        )
        return PortableBackupManifestV1(
            productId = requiredString("productId", "$"),
            backupFormatVersion = requiredInt("backupFormatVersion", "$"),
            producerVersionName = requiredString("producerVersionName", "$"),
            producerVersionCode = requiredInt("producerVersionCode", "$"),
            createdAtEpochMs = requiredLong("createdAtEpochMs", "$"),
            dataModelVersion = requiredInt("dataModelVersion", "$"),
            dataEncoding = requiredString("dataEncoding", "$"),
            compression = requiredString("compression", "$"),
            dataByteCount = requiredLong("dataByteCount", "$"),
            dataSha256 = requiredString("dataSha256", "$"),
        )
    }

    private fun JsonObject.toData(): PortableBackupDataV1 {
        requireKeys(
            "$",
            "dataModelVersion",
            "clients",
            "consultants",
            "tags",
            "tasks",
            "settings",
            "selection",
        )
        return PortableBackupDataV1(
            dataModelVersion = requiredInt("dataModelVersion", "$"),
            clients = requiredArray("clients", "$").mapIndexed { index, value ->
                value.requireObject("$.clients[$index]").toClient("$.clients[$index]")
            },
            consultants = requiredArray("consultants", "$").mapIndexed { index, value ->
                value.requireObject("$.consultants[$index]").toConsultant("$.consultants[$index]")
            },
            tags = requiredArray("tags", "$").mapIndexed { index, value ->
                value.requireObject("$.tags[$index]").toTag("$.tags[$index]")
            },
            tasks = requiredArray("tasks", "$").mapIndexed { index, value ->
                value.requireObject("$.tasks[$index]").toTask("$.tasks[$index]")
            },
            settings = requiredObject("settings", "$").toSettings("$.settings"),
            selection = nullableObject("selection", "$")?.toSelection("$.selection"),
        )
    }

    private fun JsonObject.toClient(path: String): PortableBackupClientV1 {
        requireKeys(path, *DIRECTORY_KEYS)
        return PortableBackupClientV1(
            id = requiredString("id", path),
            name = requiredString("name", path),
            canonicalName = requiredString("canonicalName", path),
            isActive = requiredBoolean("isActive", path),
            createdAtEpochMs = requiredLong("createdAtEpochMs", path),
            updatedAtEpochMs = requiredLong("updatedAtEpochMs", path),
            archivedAtEpochMs = nullableLong("archivedAtEpochMs", path),
        )
    }

    private fun JsonObject.toConsultant(path: String): PortableBackupConsultantV1 {
        requireKeys(path, *DIRECTORY_KEYS)
        return PortableBackupConsultantV1(
            id = requiredString("id", path),
            name = requiredString("name", path),
            canonicalName = requiredString("canonicalName", path),
            isActive = requiredBoolean("isActive", path),
            createdAtEpochMs = requiredLong("createdAtEpochMs", path),
            updatedAtEpochMs = requiredLong("updatedAtEpochMs", path),
            archivedAtEpochMs = nullableLong("archivedAtEpochMs", path),
        )
    }

    private fun JsonObject.toTag(path: String): PortableBackupTagV1 {
        requireKeys(path, "id", "category", "text", "normalizedText", "createdAtEpochMs", "updatedAtEpochMs")
        return PortableBackupTagV1(
            id = requiredString("id", path),
            category = requiredString("category", path),
            text = requiredString("text", path),
            normalizedText = requiredString("normalizedText", path),
            createdAtEpochMs = requiredLong("createdAtEpochMs", path),
            updatedAtEpochMs = requiredLong("updatedAtEpochMs", path),
        )
    }

    private fun JsonObject.toTask(path: String): PortableBackupTaskV1 {
        requireKeys(path, *TASK_KEYS)
        return PortableBackupTaskV1(
            id = requiredString("id", path),
            seriesId = requiredString("seriesId", path),
            clientId = requiredString("clientId", path),
            consultantId = nullableString("consultantId", path),
            consultantNameSnapshot = requiredString("consultantNameSnapshot", path),
            description = requiredString("description", path),
            hardwareSoftwarePurchases = requiredString("hardwareSoftwarePurchases", path),
            workType = requiredString("workType", path),
            billingStatus = nullableString("billingStatus", path),
            mileage = nullableString("mileage", path),
            notes = requiredString("notes", path),
            workDateEpochDay = requiredLong("workDateEpochDay", path),
            zoneId = requiredString("zoneId", path),
            createdAtEpochMs = requiredLong("createdAtEpochMs", path),
            updatedAtEpochMs = requiredLong("updatedAtEpochMs", path),
            tagSnapshots = requiredArray("tagSnapshots", path).mapIndexed { index, value ->
                value.requireObject("$path.tagSnapshots[$index]").toSnapshot("$path.tagSnapshots[$index]")
            },
            intervals = requiredArray("intervals", path).mapIndexed { index, value ->
                value.requireObject("$path.intervals[$index]").toInterval("$path.intervals[$index]")
            },
        )
    }

    private fun JsonObject.toSnapshot(path: String): PortableBackupTaskTagSnapshotV1 {
        requireKeys(path, "id", "category", "text", "sourceTagId", "selectionOrder", "createdAtEpochMs")
        return PortableBackupTaskTagSnapshotV1(
            id = requiredString("id", path),
            category = requiredString("category", path),
            text = requiredString("text", path),
            sourceTagId = nullableString("sourceTagId", path),
            selectionOrder = requiredInt("selectionOrder", path),
            createdAtEpochMs = requiredLong("createdAtEpochMs", path),
        )
    }

    private fun JsonObject.toInterval(path: String): PortableBackupIntervalV1 {
        requireKeys(path, "id", "taskId", "startEpochMs", "stopEpochMs", "wasManuallyEdited", "createdAtEpochMs", "updatedAtEpochMs")
        return PortableBackupIntervalV1(
            id = requiredString("id", path),
            taskId = requiredString("taskId", path),
            startEpochMs = requiredLong("startEpochMs", path),
            stopEpochMs = nullableLong("stopEpochMs", path),
            wasManuallyEdited = requiredBoolean("wasManuallyEdited", path),
            createdAtEpochMs = requiredLong("createdAtEpochMs", path),
            updatedAtEpochMs = requiredLong("updatedAtEpochMs", path),
        )
    }

    private fun JsonObject.toSettings(path: String): PortableBackupSettingsV1 {
        requireKeys(path, *SETTINGS_KEYS)
        return PortableBackupSettingsV1(
            themeMode = requiredString("themeMode", path),
            timeZoneMode = requiredString("timeZoneMode", path),
            manualZoneId = nullableString("manualZoneId", path),
            defaultExportDestination = requiredString("defaultExportDestination", path),
            lastExportAttempt = nullableObject("lastExportAttempt", path)?.toLastExportAttempt("$path.lastExportAttempt"),
            selectedConsultantId = nullableString("selectedConsultantId", path),
            landscapeHandedness = requiredString("landscapeHandedness", path),
        )
    }

    private fun JsonObject.toLastExportAttempt(path: String): PortableBackupLastExportAttemptV1 {
        requireKeys(path, "destination", "workDateEpochDay", "attemptedAtEpochMs", "outcome", "errorCategory")
        return PortableBackupLastExportAttemptV1(
            destination = requiredString("destination", path),
            workDateEpochDay = requiredLong("workDateEpochDay", path),
            attemptedAtEpochMs = requiredLong("attemptedAtEpochMs", path),
            outcome = requiredString("outcome", path),
            errorCategory = nullableString("errorCategory", path),
        )
    }

    private fun JsonObject.toSelection(path: String): PortableBackupSelectionV1 {
        requireKeys(path, "taskId", "seriesId", "selectedOnEpochDay", "selectedInZoneId")
        return PortableBackupSelectionV1(
            taskId = requiredString("taskId", path),
            seriesId = requiredString("seriesId", path),
            selectedOnEpochDay = requiredLong("selectedOnEpochDay", path),
            selectedInZoneId = requiredString("selectedInZoneId", path),
        )
    }

    private fun JsonElement.requireObject(path: String): JsonObject =
        this as? JsonObject ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path must be an object.")

    private fun JsonObject.requiredObject(name: String, path: String): JsonObject =
        (this[name] ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name is missing."))
            .requireObject("$path.$name")

    private fun JsonObject.nullableObject(name: String, path: String): JsonObject? {
        val value = this[name] ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name is missing.")
        return if (value is JsonNull) null else value.requireObject("$path.$name")
    }

    private fun JsonObject.requiredArray(name: String, path: String): JsonArray =
        this[name] as? JsonArray
            ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be an array.")

    private fun JsonObject.requiredString(name: String, path: String): String {
        val value = this[name] as? JsonPrimitive
            ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be a string.")
        if (!value.isString) {
            fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be a string.")
        }
        return value.content
    }

    private fun JsonObject.nullableString(name: String, path: String): String? {
        val value = this[name] ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name is missing.")
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive
            ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be a string or null.")
        if (!primitive.isString) {
            fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be a string or null.")
        }
        return primitive.contentOrNull
    }

    private fun JsonObject.requiredLong(name: String, path: String): Long {
        val invalidValueError =
            if (name.endsWith("EpochMs")) {
                PortableBackupDecodeError.INVALID_INSTANT
            } else {
                PortableBackupDecodeError.INVALID_SHAPE
            }
        val value = this[name] as? JsonPrimitive
            ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be an integer.")
        return value.longOrNull
            ?.takeIf { !value.isString }
            ?: fail(invalidValueError, "$path.$name must be an epoch-millisecond integer.")
    }

    private fun JsonObject.requiredInt(name: String, path: String): Int =
        requiredLong(name, path)
            .takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
            ?.toInt()
            ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be a 32-bit integer.")

    private fun JsonObject.nullableLong(name: String, path: String): Long? {
        val value = this[name] ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name is missing.")
        if (value is JsonNull) return null
        val invalidValueError =
            if (name.endsWith("EpochMs")) {
                PortableBackupDecodeError.INVALID_INSTANT
            } else {
                PortableBackupDecodeError.INVALID_SHAPE
            }
        val primitive = value as? JsonPrimitive
            ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be an integer or null.")
        return primitive.longOrNull
            ?.takeIf { !primitive.isString }
            ?: fail(invalidValueError, "$path.$name must be an epoch-millisecond integer or null.")
    }

    private fun JsonObject.requiredBoolean(name: String, path: String): Boolean {
        val value = this[name] as? JsonPrimitive
            ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be a boolean.")
        return value.booleanOrNull
            ?.takeIf { !value.isString }
            ?: fail(PortableBackupDecodeError.INVALID_SHAPE, "$path.$name must be a boolean.")
    }

    private fun JsonObject.requireKeys(path: String, vararg expected: String) {
        if (path == "$" && "activeTimer" in keys) {
            fail(
                PortableBackupDecodeError.ACTIVE_TIMER_PRESENT,
                "A portable backup must not contain an active timer.",
            )
        }
        if (keys != expected.toSet()) {
            fail(PortableBackupDecodeError.INVALID_SHAPE, "$path has unsupported or missing fields.")
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putString(name: String, value: String) {
        put(name, JsonPrimitive(value))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(name: String, value: String?) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putBoolean(name: String, value: Boolean) {
        put(name, JsonPrimitive(value))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putLong(name: String, value: Long) {
        put(name, JsonPrimitive(value))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putInt(name: String, value: Int) {
        put(name, JsonPrimitive(value))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableLong(name: String, value: Long?) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableObject(name: String, value: JsonObject?) {
        put(name, value ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putArray(name: String, value: List<JsonObject>) {
        put(name, JsonArray(value))
    }

    private fun fail(error: PortableBackupDecodeError, detail: String): Nothing =
        throw CodecFailure(error, detail)

    private class CodecFailure(
        val code: PortableBackupDecodeError,
        override val message: String,
    ) : IllegalArgumentException(message)

    /**
     * Rejects duplicate object keys before the DOM parser can collapse them and caps adversarial
     * nesting. The JSON parser remains authoritative for full token/number grammar validation.
     */
    private class StrictJsonStructureGuard(
        private val source: String,
    ) {
        private var index = 0

        fun validate() {
            skipWhitespace()
            parseValue(depth = 0)
            skipWhitespace()
            if (index != source.length) malformed("Unexpected trailing JSON content.")
        }

        private fun parseValue(depth: Int) {
            if (depth > MAX_JSON_NESTING) {
                throw CodecFailure(
                    PortableBackupDecodeError.RESOURCE_LIMIT,
                    "The JSON document is nested too deeply.",
                )
            }
            skipWhitespace()
            when (source.getOrNull(index)) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> parseString()
                null -> malformed("Unexpected end of JSON.")
                else -> parsePrimitive()
            }
        }

        private fun parseObject(depth: Int) {
            expect('{')
            skipWhitespace()
            if (consume('}')) return
            val keys = mutableSetOf<String>()
            while (true) {
                skipWhitespace()
                if (source.getOrNull(index) != '"') malformed("JSON object key must be a string.")
                val key = parseString()
                if (!keys.add(key)) {
                    throw CodecFailure(
                        PortableBackupDecodeError.INVALID_SHAPE,
                        "JSON object contains duplicate key '$key'.",
                    )
                }
                skipWhitespace()
                expect(':')
                parseValue(depth)
                skipWhitespace()
                when {
                    consume('}') -> return
                    consume(',') -> Unit
                    else -> malformed("JSON object requires a comma or closing brace.")
                }
            }
        }

        private fun parseArray(depth: Int) {
            expect('[')
            skipWhitespace()
            if (consume(']')) return
            while (true) {
                parseValue(depth)
                skipWhitespace()
                when {
                    consume(']') -> return
                    consume(',') -> Unit
                    else -> malformed("JSON array requires a comma or closing bracket.")
                }
            }
        }

        private fun parsePrimitive() {
            val start = index
            while (index < source.length && source[index] !in PRIMITIVE_DELIMITERS) {
                index += 1
            }
            if (start == index) malformed("JSON value is missing.")
        }

        private fun parseString(): String {
            expect('"')
            return buildString {
                while (true) {
                    val character = source.getOrNull(index++) ?: malformed("Unterminated JSON string.")
                    when {
                        character == '"' -> return@buildString
                        character == '\\' -> append(parseEscape())
                        character.code < 0x20 -> malformed("JSON string contains a control character.")
                        else -> append(character)
                    }
                }
            }
        }

        private fun parseEscape(): Char {
            val escaped = source.getOrNull(index++) ?: malformed("Unterminated JSON escape.")
            return when (escaped) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > source.length) malformed("Incomplete Unicode escape.")
                    val value = source.substring(index, index + 4).toIntOrNull(16)
                        ?: malformed("Invalid Unicode escape.")
                    index += 4
                    value.toChar()
                }
                else -> malformed("Invalid JSON escape.")
            }
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index] in JSON_WHITESPACE) index += 1
        }

        private fun expect(expected: Char) {
            if (!consume(expected)) malformed("Expected '$expected'.")
        }

        private fun consume(expected: Char): Boolean =
            if (source.getOrNull(index) == expected) {
                index += 1
                true
            } else {
                false
            }

        private fun malformed(detail: String): Nothing =
            throw CodecFailure(PortableBackupDecodeError.MALFORMED_JSON, detail)

        private companion object {
            const val MAX_JSON_NESTING = 128
            val JSON_WHITESPACE = setOf(' ', '\t', '\n', '\r')
            val PRIMITIVE_DELIMITERS = JSON_WHITESPACE + setOf(',', ']', '}')
        }
    }

    private val DIRECTORY_KEYS =
        arrayOf(
            "id",
            "name",
            "canonicalName",
            "isActive",
            "createdAtEpochMs",
            "updatedAtEpochMs",
            "archivedAtEpochMs",
        )

    private val TASK_KEYS =
        arrayOf(
            "id",
            "seriesId",
            "clientId",
            "consultantId",
            "consultantNameSnapshot",
            "description",
            "hardwareSoftwarePurchases",
            "workType",
            "billingStatus",
            "mileage",
            "notes",
            "workDateEpochDay",
            "zoneId",
            "createdAtEpochMs",
            "updatedAtEpochMs",
            "tagSnapshots",
            "intervals",
        )

    private val SETTINGS_KEYS =
        arrayOf(
            "themeMode",
            "timeZoneMode",
            "manualZoneId",
            "defaultExportDestination",
            "lastExportAttempt",
            "selectedConsultantId",
            "landscapeHandedness",
        )
}
