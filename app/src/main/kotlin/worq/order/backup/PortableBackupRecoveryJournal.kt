package worq.order.backup

import java.security.MessageDigest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal const val PORTABLE_BACKUP_RECOVERY_JOURNAL_VERSION = 1

enum class PortableBackupReplacementKind {
    IMPORT,
    RESTORE,
}

enum class PortableBackupRecoveryDirection {
    FORWARD,
    ROLLBACK,
}

enum class PortableBackupReplacementNextAction {
    PREPARE_ROLLING_POINT,
    APPLY_TARGET_ROOM,
    APPLY_TARGET_PREFERENCES,
    RESET_TARGET_RUNTIME,
    FINALIZE_SUCCESS,
    APPLY_ROLLBACK_ROOM,
    APPLY_ROLLBACK_PREFERENCES,
    RESET_ROLLBACK_RUNTIME,
    RESTORE_PRIOR_ROLLING_POINT,
    FINALIZE_ROLLBACK,
}

data class PortableBackupArtifact(
    val name: String,
    val byteCount: Long,
    val sha256: String,
)

internal data class PortableBackupReplacementJournal(
    val operationId: String,
    val kind: PortableBackupReplacementKind,
    val direction: PortableBackupRecoveryDirection,
    val nextAction: PortableBackupReplacementNextAction,
    val sequence: Long,
    val source: PortableBackupArtifact,
    val displaced: PortableBackupArtifact,
    val localPreferences: PortableBackupArtifact,
    val priorRestore: PortableBackupArtifact?,
    val targetOriginId: String,
    val preservedDefaultDestination: String,
)

/** Strict local JSON codec for a recovery artifact; this file is never user-supplied. */
internal object PortableBackupRecoveryJournalCodec {
    private val json =
        Json {
            isLenient = false
            ignoreUnknownKeys = false
            allowSpecialFloatingPointValues = false
        }

    fun encode(journal: PortableBackupReplacementJournal): String {
        val payload = journal.toJson()
        return JsonObject(
            payload + ("journalSha256" to JsonPrimitive(payload.sha256())),
        ).toString()
    }

    fun decode(source: String): PortableBackupReplacementJournal? =
        try {
            val root = json.parseToJsonElement(source) as? JsonObject ?: return null
            root.requireKeys(
                "schemaVersion",
                "operationId",
                "kind",
                "direction",
                "nextAction",
                "sequence",
                "source",
                "displaced",
                "localPreferences",
                "priorRestore",
                "targetOriginId",
                "preservedDefaultDestination",
                "journalSha256",
            )
            if (root.requiredInt("schemaVersion") != PORTABLE_BACKUP_RECOVERY_JOURNAL_VERSION) return null
            val operationId = root.requiredString("operationId") ?: return null
            val kind = root.requiredEnum<PortableBackupReplacementKind>("kind") ?: return null
            val direction = root.requiredEnum<PortableBackupRecoveryDirection>("direction") ?: return null
            val action = root.requiredEnum<PortableBackupReplacementNextAction>("nextAction") ?: return null
            val sequence = root.requiredLong("sequence") ?: return null
            val sourceArtifact = root.requiredArtifact("source") ?: return null
            val displaced = root.requiredArtifact("displaced") ?: return null
            val preferences = root.requiredArtifact("localPreferences") ?: return null
            val priorRestore =
                when (val encodedPrior = root["priorRestore"]) {
                    JsonNull -> null
                    is JsonObject -> encodedPrior.toArtifact() ?: return null
                    else -> return null
                }
            val targetOriginId = root.requiredString("targetOriginId") ?: return null
            val destination = root.requiredString("preservedDefaultDestination") ?: return null
            val journal = PortableBackupReplacementJournal(
                operationId = operationId,
                kind = kind,
                direction = direction,
                nextAction = action,
                sequence = sequence,
                source = sourceArtifact,
                displaced = displaced,
                localPreferences = preferences,
                priorRestore = priorRestore,
                targetOriginId = targetOriginId,
                preservedDefaultDestination = destination,
            ).takeIf(::isSane) ?: return null
            val storedChecksum = root.requiredString("journalSha256") ?: return null
            journal.takeIf { candidate ->
                SHA256.matches(storedChecksum) && candidate.toJson().sha256() == storedChecksum
            }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    fun encodeLocalPreferences(snapshot: PortableBackupLocalPreferencesSnapshot): String =
        buildJsonObject {
            put("schemaVersion", JsonPrimitive(1))
            put(
                "entries",
                buildJsonArray {
                    snapshot.entries.forEach { entry ->
                        add(
                            buildJsonObject {
                                put("name", JsonPrimitive(entry.name))
                                put("type", JsonPrimitive(entry.type.name))
                                put("value", JsonPrimitive(entry.value))
                            },
                        )
                    }
                },
            )
        }.toString()

    fun decodeLocalPreferences(source: String): PortableBackupLocalPreferencesSnapshot? =
        try {
            val root = json.parseToJsonElement(source) as? JsonObject ?: return null
            root.requireKeys("schemaVersion", "entries")
            if (root.requiredInt("schemaVersion") != 1) return null
            val entries = root["entries"] as? JsonArray ?: return null
            val decoded =
                entries.map { element ->
                    val entry = element as? JsonObject ?: return null
                    entry.requireKeys("name", "type", "value")
                    val name = entry.requiredString("name") ?: return null
                    val type = entry.requiredEnum<PortableBackupLocalPreferenceType>("type") ?: return null
                    val value = entry.requiredString("value") ?: return null
                    PortableBackupLocalPreferenceEntry(name, type, value)
                }
            PortableBackupLocalPreferencesSnapshot(decoded.sortedBy(PortableBackupLocalPreferenceEntry::name))
                .takeIf { snapshot ->
                    snapshot.entries.all { entry -> entry.name.matches(PREFERENCE_NAME) } &&
                        snapshot.entries.all(::hasValidPreferenceValue) &&
                        snapshot.entries.distinctBy(PortableBackupLocalPreferenceEntry::name).size ==
                        snapshot.entries.size
                }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun PortableBackupReplacementJournal.toJson(): JsonObject =
        buildJsonObject {
            put("schemaVersion", JsonPrimitive(PORTABLE_BACKUP_RECOVERY_JOURNAL_VERSION))
            put("operationId", JsonPrimitive(operationId))
            put("kind", JsonPrimitive(kind.name))
            put("direction", JsonPrimitive(direction.name))
            put("nextAction", JsonPrimitive(nextAction.name))
            put("sequence", JsonPrimitive(sequence))
            put("source", source.toJson())
            put("displaced", displaced.toJson())
            put("localPreferences", localPreferences.toJson())
            put("priorRestore", priorRestore?.toJson() ?: JsonNull)
            put("targetOriginId", JsonPrimitive(targetOriginId))
            put("preservedDefaultDestination", JsonPrimitive(preservedDefaultDestination))
        }

    private fun PortableBackupArtifact.toJson(): JsonObject =
        buildJsonObject {
            put("name", JsonPrimitive(name))
            put("byteCount", JsonPrimitive(byteCount))
            put("sha256", JsonPrimitive(sha256))
        }

    private fun JsonObject.requiredArtifact(name: String): PortableBackupArtifact? =
        (get(name) as? JsonObject)?.toArtifact()

    private fun JsonObject.toArtifact(): PortableBackupArtifact? {
        requireKeys("name", "byteCount", "sha256")
        val name = requiredString("name") ?: return null
        val byteCount = requiredLong("byteCount") ?: return null
        val sha256 = requiredString("sha256") ?: return null
        return PortableBackupArtifact(name, byteCount, sha256)
    }

    private fun JsonObject.requireKeys(vararg expected: String) {
        require(keys == expected.toSet())
    }

    private fun JsonObject.requiredString(name: String): String? =
        (get(name) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private fun JsonObject.requiredInt(name: String): Int? =
        (get(name) as? JsonPrimitive)?.intOrNull

    private fun JsonObject.requiredLong(name: String): Long? =
        (get(name) as? JsonPrimitive)?.longOrNull

    private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String): T? =
        requiredString(name)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } }

    private fun isSane(journal: PortableBackupReplacementJournal): Boolean =
        OPERATION_ID.matches(journal.operationId) &&
            ORIGIN_ID.matches(journal.targetOriginId) &&
            journal.sequence >= 0 &&
            journal.preservedDefaultDestination in EXPORT_DESTINATIONS &&
            listOf(journal.source, journal.displaced, journal.localPreferences)
                .plus(listOfNotNull(journal.priorRestore))
                .all(::isSaneArtifact)

    private fun isSaneArtifact(artifact: PortableBackupArtifact): Boolean =
        SAFE_FILE_NAME.matches(artifact.name) && artifact.byteCount > 0 && SHA256.matches(artifact.sha256)

    private fun hasValidPreferenceValue(entry: PortableBackupLocalPreferenceEntry): Boolean =
        when (entry.type) {
            PortableBackupLocalPreferenceType.STRING -> true
            PortableBackupLocalPreferenceType.BOOLEAN ->
                entry.value == "true" || entry.value == "false"
            PortableBackupLocalPreferenceType.INT -> entry.value.toIntOrNull() != null
            PortableBackupLocalPreferenceType.LONG -> entry.value.toLongOrNull() != null
        }

    private fun JsonObject.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private val OPERATION_ID = Regex("^[a-z0-9]{32}$")
    private val ORIGIN_ID = Regex("^[a-z0-9]{32}$")
    private val SAFE_FILE_NAME = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
    private val SHA256 = Regex("^[a-f0-9]{64}$")
    private val PREFERENCE_NAME = Regex("^[a-z0-9._-]{1,128}$")
    private val EXPORT_DESTINATIONS = setOf("CSV", "XLSX", "GOOGLE_SHEETS")
}
