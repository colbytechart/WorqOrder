package worq.order.backup

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import worq.order.data.EmployeeNameNormalizer
import worq.order.data.ExportAttemptOutcome
import worq.order.data.ExportDestination
import worq.order.data.ExportErrorCategory
import worq.order.data.LandscapeHandedness
import worq.order.data.MileageNormalizer
import worq.order.data.MileageValidationResult
import worq.order.data.TagTextNormalizer
import worq.order.data.TagTextValidationResult
import worq.order.data.TaskTextComposer
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.data.ClientNameNormalizer
import worq.order.model.BillingStatus
import worq.order.model.TagCategory
import worq.order.model.WorkType

/**
 * Validates a fully decoded logical backup before an archive reader, UI, Room, or DataStore can
 * use it. All failures are accumulated and typed so later milestones can present safe guidance
 * while keeping authoritative state untouched.
 */
object PortableBackupValidator {
    private val sha256Pattern = Regex("^[A-Fa-f0-9]{64}$")

    fun validateManifest(
        manifest: PortableBackupManifestV1,
    ): PortableBackupManifestValidationResult {
        val failures = linkedSetOf<PortableBackupValidationFailure>()
        if (manifest.productId != PORTABLE_BACKUP_PRODUCT_ID) {
            failures += PortableBackupValidationFailure.PRODUCT_MARKER
        }
        if (manifest.dataEncoding != "UTF-8") {
            failures += PortableBackupValidationFailure.MANIFEST_ENCODING
        }
        if (manifest.compression != "DEFLATE") {
            failures += PortableBackupValidationFailure.MANIFEST_COMPRESSION
        }
        if (manifest.dataByteCount !in 1..PortableBackupLimits.MAX_EXPANDED_BYTES) {
            failures += PortableBackupValidationFailure.MANIFEST_DATA_SIZE
        }
        if (!sha256Pattern.matches(manifest.dataSha256)) {
            failures += PortableBackupValidationFailure.MANIFEST_CHECKSUM
        }
        if (manifest.producerVersionName.isBlank() || manifest.producerVersionCode < 0) {
            failures += PortableBackupValidationFailure.INVALID_ID
        }
        if (!isValidInstant(manifest.createdAtEpochMs)) {
            failures += PortableBackupValidationFailure.INVALID_INSTANT
        }
        if (failures.isNotEmpty()) {
            return PortableBackupManifestValidationResult.Invalid(failures)
        }
        if (manifest.backupFormatVersion > PORTABLE_BACKUP_FORMAT_VERSION) {
            return PortableBackupManifestValidationResult.UnsupportedFutureVersion(
                manifest.backupFormatVersion,
            )
        }
        if (manifest.backupFormatVersion < PORTABLE_BACKUP_FORMAT_VERSION) {
            return PortableBackupManifestValidationResult.UpgradeRequired(
                manifest.backupFormatVersion,
            )
        }
        if (manifest.dataModelVersion != PORTABLE_BACKUP_DATA_MODEL_VERSION) {
            failures += PortableBackupValidationFailure.UNSUPPORTED_DATA_MODEL
        }
        return if (failures.isEmpty()) PortableBackupManifestValidationResult.Current
        else PortableBackupManifestValidationResult.Invalid(failures)
    }

    fun validateData(
        data: PortableBackupDataV1,
    ): PortableBackupDataValidationResult {
        val failures = linkedSetOf<PortableBackupValidationFailure>()
        if (data.dataModelVersion != PORTABLE_BACKUP_DATA_MODEL_VERSION) {
            failures += PortableBackupValidationFailure.UNSUPPORTED_DATA_MODEL
        }

        validateDirectory(
            entries = data.clients,
            id = PortableBackupClientV1::id,
            name = PortableBackupClientV1::name,
            canonicalName = PortableBackupClientV1::canonicalName,
            isActive = PortableBackupClientV1::isActive,
            createdAt = PortableBackupClientV1::createdAtEpochMs,
            updatedAt = PortableBackupClientV1::updatedAtEpochMs,
            archivedAt = PortableBackupClientV1::archivedAtEpochMs,
            canonicalizes = { rawName ->
                (ClientNameNormalizer.validate(rawName) as? worq.order.data.ClientNameValidationResult.Valid)
                    ?.name
                    ?.canonicalName
            },
            failures = failures,
        )
        validateDirectory(
            entries = data.consultants,
            id = PortableBackupConsultantV1::id,
            name = PortableBackupConsultantV1::name,
            canonicalName = PortableBackupConsultantV1::canonicalName,
            isActive = PortableBackupConsultantV1::isActive,
            createdAt = PortableBackupConsultantV1::createdAtEpochMs,
            updatedAt = PortableBackupConsultantV1::updatedAtEpochMs,
            archivedAt = PortableBackupConsultantV1::archivedAtEpochMs,
            canonicalizes = { rawName ->
                (EmployeeNameNormalizer.validate(rawName) as? worq.order.data.EmployeeNameValidationResult.Valid)
                    ?.name
                    ?.canonicalName
            },
            failures = failures,
        )

        val clientIds = data.clients.mapTo(mutableSetOf(), PortableBackupClientV1::id)
        val consultantById = data.consultants.associateBy(PortableBackupConsultantV1::id)
        validateTags(data.tags, failures)
        validateTasks(data.tasks, clientIds, consultantById, failures)
        validateSettings(data.settings, consultantById, failures)
        validateSelection(data.selection, data.tasks, failures)

        return if (failures.isEmpty()) {
            PortableBackupDataValidationResult.Valid(data)
        } else {
            PortableBackupDataValidationResult.Invalid(failures)
        }
    }

    private fun <T> validateDirectory(
        entries: List<T>,
        id: (T) -> String,
        name: (T) -> String,
        canonicalName: (T) -> String,
        isActive: (T) -> Boolean,
        createdAt: (T) -> Long,
        updatedAt: (T) -> Long,
        archivedAt: (T) -> Long?,
        canonicalizes: (String) -> String?,
        failures: MutableSet<PortableBackupValidationFailure>,
    ) {
        if (entries.map(id).hasDuplicates()) {
            failures += PortableBackupValidationFailure.DUPLICATE_ID
        }
        val activeKeys = mutableSetOf<String>()
        entries.forEach { entry ->
            val entryId = id(entry)
            val rawName = name(entry)
            val expectedCanonical = canonicalizes(rawName)
            if (!isValidId(entryId)) {
                failures += PortableBackupValidationFailure.INVALID_ID
            }
            if (expectedCanonical == null || canonicalName(entry) != expectedCanonical) {
                failures += PortableBackupValidationFailure.INVALID_NAME
            }
            val active = isActive(entry)
            if (active && !activeKeys.add(canonicalName(entry))) {
                failures += PortableBackupValidationFailure.INVALID_NAME
            }
            if ((active && archivedAt(entry) != null) || (!active && archivedAt(entry) == null)) {
                failures += PortableBackupValidationFailure.INVALID_NAME
            }
            if (archivedAt(entry) != null && archivedAt(entry)!! < createdAt(entry)) {
                failures += PortableBackupValidationFailure.INVALID_INSTANT
            }
            validateTimestamps(createdAt(entry), updatedAt(entry), archivedAt(entry), failures)
        }
    }

    private fun validateTags(
        tags: List<PortableBackupTagV1>,
        failures: MutableSet<PortableBackupValidationFailure>,
    ) {
        if (tags.map(PortableBackupTagV1::id).hasDuplicates()) {
            failures += PortableBackupValidationFailure.DUPLICATE_ID
        }
        val categoryAndNormalizedText = mutableSetOf<Pair<String, String>>()
        tags.forEach { tag ->
            if (!isValidId(tag.id)) failures += PortableBackupValidationFailure.INVALID_ID
            if (tag.category !in TagCategory.entries.map(TagCategory::name)) {
                failures += PortableBackupValidationFailure.INVALID_ENUM
            }
            when (val normalized = TagTextNormalizer.validate(tag.text)) {
                is TagTextValidationResult.Valid -> {
                    if (tag.normalizedText != normalized.text.normalizedText) {
                        failures += PortableBackupValidationFailure.INVALID_TAG
                    }
                }
                is TagTextValidationResult.Invalid -> {
                    failures +=
                        if (normalized.error.name == "TOO_LONG") {
                            PortableBackupValidationFailure.TAG_LIMIT
                        } else {
                            PortableBackupValidationFailure.INVALID_TAG
                        }
                }
            }
            if (!categoryAndNormalizedText.add(tag.category to tag.normalizedText)) {
                failures += PortableBackupValidationFailure.INVALID_TAG
            }
            validateTimestamps(tag.createdAtEpochMs, tag.updatedAtEpochMs, null, failures)
        }
    }

    private fun validateTasks(
        tasks: List<PortableBackupTaskV1>,
        clientIds: Set<String>,
        consultants: Map<String, PortableBackupConsultantV1>,
        failures: MutableSet<PortableBackupValidationFailure>,
    ) {
        if (tasks.map(PortableBackupTaskV1::id).hasDuplicates()) {
            failures += PortableBackupValidationFailure.DUPLICATE_ID
        }
        val intervalIds = mutableSetOf<String>()
        val snapshotIds = mutableSetOf<String>()
        tasks.forEach { task ->
            if (!isValidId(task.id) || !isValidId(task.seriesId)) {
                failures += PortableBackupValidationFailure.INVALID_ID
            }
            if (task.clientId !in clientIds) {
                failures += PortableBackupValidationFailure.BROKEN_REFERENCE
            }
            task.consultantId?.let { consultantId ->
                if (consultantId !in consultants) {
                    failures += PortableBackupValidationFailure.BROKEN_REFERENCE
                }
            }
            if ((task.consultantId == null && task.consultantNameSnapshot.isNotEmpty()) ||
                (task.consultantId != null &&
                    (task.consultantNameSnapshot.isBlank() ||
                        task.consultantNameSnapshot.codePointCount(
                            0,
                            task.consultantNameSnapshot.length,
                        ) > 100))
            ) {
                failures += PortableBackupValidationFailure.BROKEN_REFERENCE
            }
            if (task.workType !in WorkType.entries.map(WorkType::name) ||
                (task.billingStatus != null && task.billingStatus !in BillingStatus.entries.map(BillingStatus::name))
            ) {
                failures += PortableBackupValidationFailure.INVALID_ENUM
            }
            if (!isValidZone(task.zoneId)) failures += PortableBackupValidationFailure.INVALID_ZONE_ID
            if (!isValidDate(task.workDateEpochDay)) failures += PortableBackupValidationFailure.INVALID_DATE
            validateTimestamps(task.createdAtEpochMs, task.updatedAtEpochMs, null, failures)
            when (MileageNormalizer.normalize(task.mileage)) {
                is MileageValidationResult.Valid -> Unit
                is MileageValidationResult.Invalid ->
                    failures += PortableBackupValidationFailure.INVALID_MILEAGE
            }

            validateTaskSnapshots(task, snapshotIds, failures)
            val descriptionTags =
                task.tagSnapshots
                    .filter { it.category == TagCategory.DESCRIPTION.name }
                    .sortedBy(PortableBackupTaskTagSnapshotV1::selectionOrder)
                    .map(PortableBackupTaskTagSnapshotV1::text)
            val purchaseTags =
                task.tagSnapshots
                    .filter { it.category == TagCategory.HARDWARE_SOFTWARE_PURCHASE.name }
                    .sortedBy(PortableBackupTaskTagSnapshotV1::selectionOrder)
                    .map(PortableBackupTaskTagSnapshotV1::text)
            val composedDescription = TaskTextComposer.compose(task.description, descriptionTags)
            val composedPurchases = TaskTextComposer.compose(task.hardwareSoftwarePurchases, purchaseTags)
            if (composedDescription.isEmpty() ||
                TaskTextComposer.codePointCount(composedDescription) > PortableBackupLimits.MAX_TASK_TEXT_CODE_POINTS ||
                TaskTextComposer.codePointCount(composedPurchases) > PortableBackupLimits.MAX_TASK_TEXT_CODE_POINTS ||
                TaskTextComposer.codePointCount(task.notes) > PortableBackupLimits.MAX_TASK_TEXT_CODE_POINTS
            ) {
                failures += PortableBackupValidationFailure.TEXT_LIMIT
            }

            if (task.intervals.size > 1) failures += PortableBackupValidationFailure.INVALID_INTERVAL
            task.intervals.forEach { interval ->
                if (!intervalIds.add(interval.id)) failures += PortableBackupValidationFailure.DUPLICATE_ID
                if (!isValidId(interval.id) || interval.taskId != task.id) {
                    failures += PortableBackupValidationFailure.INVALID_INTERVAL
                }
                val stop = interval.stopEpochMs
                if (stop == null) {
                    failures += PortableBackupValidationFailure.OPEN_INTERVAL
                } else if (interval.startEpochMs >= stop) {
                    failures += PortableBackupValidationFailure.INVALID_INTERVAL
                }
                validateTimestamps(
                    interval.createdAtEpochMs,
                    interval.updatedAtEpochMs,
                    null,
                    failures,
                )
            }
        }
    }

    private fun validateTaskSnapshots(
        task: PortableBackupTaskV1,
        allSnapshotIds: MutableSet<String>,
        failures: MutableSet<PortableBackupValidationFailure>,
    ) {
        task.tagSnapshots.forEach { snapshot ->
            if (!allSnapshotIds.add(snapshot.id)) failures += PortableBackupValidationFailure.DUPLICATE_ID
            if (!isValidId(snapshot.id) ||
                snapshot.category !in TagCategory.entries.map(TagCategory::name) ||
                snapshot.selectionOrder < 0 ||
                (snapshot.sourceTagId != null && !isValidId(snapshot.sourceTagId))
            ) {
                failures += PortableBackupValidationFailure.DUPLICATE_SNAPSHOT
            }
            when (val normalized = TagTextNormalizer.validate(snapshot.text)) {
                is TagTextValidationResult.Valid -> Unit
                is TagTextValidationResult.Invalid -> {
                    failures +=
                        if (normalized.error.name == "TOO_LONG") {
                            PortableBackupValidationFailure.TAG_LIMIT
                        } else {
                            PortableBackupValidationFailure.INVALID_TAG
                        }
                }
            }
            if (!isValidInstant(snapshot.createdAtEpochMs)) {
                failures += PortableBackupValidationFailure.INVALID_INSTANT
            }
        }
        task.tagSnapshots.groupBy(PortableBackupTaskTagSnapshotV1::category).forEach { (_, snapshots) ->
            val orders = snapshots.map(PortableBackupTaskTagSnapshotV1::selectionOrder).sorted()
            val sourceIds = snapshots.mapNotNull(PortableBackupTaskTagSnapshotV1::sourceTagId)
            if (orders != orders.indices.toList() || sourceIds.size != sourceIds.distinct().size) {
                failures += PortableBackupValidationFailure.DUPLICATE_SNAPSHOT
            }
        }
    }

    private fun validateSettings(
        settings: PortableBackupSettingsV1,
        consultants: Map<String, PortableBackupConsultantV1>,
        failures: MutableSet<PortableBackupValidationFailure>,
    ) {
        if (settings.themeMode !in ThemeMode.entries.map(ThemeMode::name) ||
            settings.timeZoneMode !in TimeZoneMode.entries.map(TimeZoneMode::name) ||
            settings.defaultExportDestination !in ExportDestination.entries.map(ExportDestination::name) ||
            settings.landscapeHandedness !in LandscapeHandedness.entries.map(LandscapeHandedness::name)
        ) {
            failures += PortableBackupValidationFailure.INVALID_ENUM
        }
        if (settings.timeZoneMode == TimeZoneMode.MANUAL.name && !isValidZone(settings.manualZoneId)) {
            failures += PortableBackupValidationFailure.INVALID_ZONE_ID
        }
        if (settings.timeZoneMode == TimeZoneMode.DEVICE.name && settings.manualZoneId != null && !isValidZone(settings.manualZoneId)) {
            failures += PortableBackupValidationFailure.INVALID_ZONE_ID
        }
        settings.selectedConsultantId?.let { consultantId ->
            if (consultants[consultantId]?.isActive != true) {
                failures += PortableBackupValidationFailure.BROKEN_REFERENCE
            }
        }
        settings.lastExportAttempt?.let { attempt ->
            if (attempt.destination !in ExportDestination.entries.map(ExportDestination::name) ||
                attempt.outcome !in ExportAttemptOutcome.entries.map(ExportAttemptOutcome::name) ||
                (attempt.errorCategory != null &&
                    attempt.errorCategory !in ExportErrorCategory.entries.map(ExportErrorCategory::name))
            ) {
                failures += PortableBackupValidationFailure.INVALID_ENUM
            }
            if (!isValidDate(attempt.workDateEpochDay)) failures += PortableBackupValidationFailure.INVALID_DATE
            if (!isValidInstant(attempt.attemptedAtEpochMs)) failures += PortableBackupValidationFailure.INVALID_INSTANT
        }
    }

    private fun validateSelection(
        selection: PortableBackupSelectionV1?,
        tasks: List<PortableBackupTaskV1>,
        failures: MutableSet<PortableBackupValidationFailure>,
    ) {
        selection ?: return
        val task = tasks.singleOrNull { it.id == selection.taskId }
        if (task == null ||
            task.seriesId != selection.seriesId ||
            task.workDateEpochDay != selection.selectedOnEpochDay ||
            task.zoneId != selection.selectedInZoneId ||
            !isValidZone(selection.selectedInZoneId)
        ) {
            failures += PortableBackupValidationFailure.INVALID_SELECTION
        }
    }

    private fun validateTimestamps(
        createdAt: Long,
        updatedAt: Long,
        archivedAt: Long?,
        failures: MutableSet<PortableBackupValidationFailure>,
    ) {
        if (!isValidInstant(createdAt) || !isValidInstant(updatedAt) ||
            (archivedAt != null && !isValidInstant(archivedAt)) || updatedAt < createdAt
        ) {
            failures += PortableBackupValidationFailure.INVALID_INSTANT
        }
    }

    private fun isValidId(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= PortableBackupLimits.MAX_ID_LENGTH &&
            value == value.trim() &&
            ':' !in value &&
            value.none { character -> character.isISOControl() }

    private fun isValidZone(value: String?): Boolean =
        value != null && runCatching { ZoneId.of(value) }.isSuccess

    private fun isValidDate(value: Long): Boolean =
        runCatching { LocalDate.ofEpochDay(value) }.isSuccess

    private fun isValidInstant(value: Long): Boolean =
        runCatching { Instant.ofEpochMilli(value) }.isSuccess

    private fun <T> List<T>.hasDuplicates(): Boolean = size != toSet().size
}
