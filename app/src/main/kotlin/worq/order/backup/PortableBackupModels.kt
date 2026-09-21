package worq.order.backup

/**
 * The logical, versioned payload used inside a portable WorqOrder backup. It deliberately
 * contains no Room/DataStore files, Android objects, OAuth state, connected-sheet metadata, or
 * installation-local export origin.
 */
const val PORTABLE_BACKUP_PRODUCT_ID = "worq.order"
const val PORTABLE_BACKUP_FORMAT_VERSION = 1
const val PORTABLE_BACKUP_DATA_MODEL_VERSION = 7

object PortableBackupLimits {
    const val MAX_COMPRESSED_BYTES = 100L * 1024L * 1024L
    const val MAX_EXPANDED_BYTES = 500L * 1024L * 1024L
    const val MAX_TASK_TEXT_CODE_POINTS = 999
    const val MAX_TAG_CODE_POINTS = 400
    const val MAX_ID_LENGTH = 256
}

data class PortableBackupManifestV1(
    val productId: String,
    val backupFormatVersion: Int,
    val producerVersionName: String,
    val producerVersionCode: Int,
    val createdAtEpochMs: Long,
    val dataModelVersion: Int,
    val dataEncoding: String,
    val compression: String,
    val dataByteCount: Long,
    val dataSha256: String,
)

data class PortableBackupDataV1(
    val dataModelVersion: Int,
    val clients: List<PortableBackupClientV1>,
    val consultants: List<PortableBackupConsultantV1>,
    val tags: List<PortableBackupTagV1>,
    val tasks: List<PortableBackupTaskV1>,
    val settings: PortableBackupSettingsV1,
    val selection: PortableBackupSelectionV1?,
)

data class PortableBackupClientV1(
    val id: String,
    val name: String,
    val canonicalName: String,
    val isActive: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val archivedAtEpochMs: Long?,
)

data class PortableBackupConsultantV1(
    val id: String,
    val name: String,
    val canonicalName: String,
    val isActive: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val archivedAtEpochMs: Long?,
)

data class PortableBackupTagV1(
    val id: String,
    val category: String,
    val text: String,
    val normalizedText: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class PortableBackupTaskV1(
    val id: String,
    val seriesId: String,
    val clientId: String,
    val consultantId: String?,
    val consultantNameSnapshot: String,
    val description: String,
    val hardwareSoftwarePurchases: String,
    val workType: String,
    val billingStatus: String?,
    val mileage: String?,
    val notes: String,
    val workDateEpochDay: Long,
    val zoneId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val tagSnapshots: List<PortableBackupTaskTagSnapshotV1>,
    val intervals: List<PortableBackupIntervalV1>,
)

data class PortableBackupTaskTagSnapshotV1(
    val id: String,
    val category: String,
    val text: String,
    val sourceTagId: String?,
    val selectionOrder: Int,
    val createdAtEpochMs: Long,
)

data class PortableBackupIntervalV1(
    val id: String,
    val taskId: String,
    val startEpochMs: Long,
    val stopEpochMs: Long?,
    val wasManuallyEdited: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class PortableBackupSettingsV1(
    val themeMode: String,
    val timeZoneMode: String,
    val manualZoneId: String?,
    val defaultExportDestination: String,
    val lastExportAttempt: PortableBackupLastExportAttemptV1?,
    val selectedConsultantId: String?,
    val landscapeHandedness: String,
)

data class PortableBackupLastExportAttemptV1(
    val destination: String,
    val workDateEpochDay: Long,
    val attemptedAtEpochMs: Long,
    val outcome: String,
    val errorCategory: String?,
)

data class PortableBackupSelectionV1(
    val taskId: String,
    val seriesId: String,
    val selectedOnEpochDay: Long,
    val selectedInZoneId: String,
)

/** The bounded decoder reports a stable, user-safe reason without throwing into UI code. */
sealed interface PortableBackupDecodeResult<out T> {
    data class Success<T>(val value: T) : PortableBackupDecodeResult<T>

    data class Invalid(
        val error: PortableBackupDecodeError,
        val detail: String,
    ) : PortableBackupDecodeResult<Nothing>
}

enum class PortableBackupDecodeError {
    MALFORMED_JSON,
    INVALID_SHAPE,
    INVALID_INSTANT,
    RESOURCE_LIMIT,
    ACTIVE_TIMER_PRESENT,
}

sealed interface PortableBackupManifestValidationResult {
    data object Current : PortableBackupManifestValidationResult

    data class UpgradeRequired(
        val fromFormatVersion: Int,
    ) : PortableBackupManifestValidationResult

    data class UnsupportedFutureVersion(
        val formatVersion: Int,
    ) : PortableBackupManifestValidationResult

    data class Invalid(
        val failures: Set<PortableBackupValidationFailure>,
    ) : PortableBackupManifestValidationResult
}

sealed interface PortableBackupDataValidationResult {
    data class Valid(
        val data: PortableBackupDataV1,
    ) : PortableBackupDataValidationResult

    data class Invalid(
        val failures: Set<PortableBackupValidationFailure>,
    ) : PortableBackupDataValidationResult
}

enum class PortableBackupValidationFailure {
    PRODUCT_MARKER,
    MANIFEST_ENCODING,
    MANIFEST_COMPRESSION,
    MANIFEST_DATA_SIZE,
    MANIFEST_CHECKSUM,
    UNSUPPORTED_DATA_MODEL,
    DUPLICATE_ID,
    INVALID_ID,
    INVALID_NAME,
    INVALID_TAG,
    TAG_LIMIT,
    TEXT_LIMIT,
    INVALID_MILEAGE,
    INVALID_ENUM,
    INVALID_ZONE_ID,
    INVALID_DATE,
    INVALID_INSTANT,
    BROKEN_REFERENCE,
    INVALID_SELECTION,
    DUPLICATE_SNAPSHOT,
    OPEN_INTERVAL,
    INVALID_INTERVAL,
    ACTIVE_TIMER_PRESENT,
}

/** A future logical format must provide an explicit upgrader rather than being guessed. */
interface PortableBackupFormatUpgrader {
    val fromFormatVersion: Int

    /**
     * Decodes and upgrades one known older logical format directly to the current DTO. Keeping
     * the old payload encoded prevents the current decoder from guessing an obsolete shape.
     */
    fun upgradeToCurrent(
        manifest: PortableBackupManifestV1,
        encodedDataJson: String,
    ): PortableBackupUpgradeResult
}

sealed interface PortableBackupUpgradeResult {
    data class Upgraded(
        val manifest: PortableBackupManifestV1,
        val data: PortableBackupDataV1,
    ) : PortableBackupUpgradeResult

    data class Unsupported(
        val fromFormatVersion: Int,
    ) : PortableBackupUpgradeResult
}

/**
 * Resolves exactly one explicitly registered older-format adapter to the current DTO. Archive I/O
 * and replacement are intentionally out of scope for Milestone 50.
 */
class PortableBackupUpgraderRegistry(
    private val upgraders: Set<PortableBackupFormatUpgrader> = emptySet(),
) {
    fun upgradeToCurrent(
        manifest: PortableBackupManifestV1,
        encodedDataJson: String,
    ): PortableBackupUpgradeResult =
        upgraders
            .singleOrNull { it.fromFormatVersion == manifest.backupFormatVersion }
            ?.upgradeToCurrent(manifest, encodedDataJson)
            ?.takeIf { result ->
                result !is PortableBackupUpgradeResult.Upgraded ||
                    result.manifest.backupFormatVersion == PORTABLE_BACKUP_FORMAT_VERSION
            }
            ?: PortableBackupUpgradeResult.Unsupported(manifest.backupFormatVersion)
}
