package worq.order.backup

import java.time.Instant
import java.time.LocalDate

/**
 * Test-only logical snapshot contract for the v0.6 format work.
 *
 * These fixtures intentionally do not depend on the production codec or Room entities. Terra's
 * implementation task maps the production DTOs/validator to this contract without making the
 * archive format a raw database dump. Keeping the fixture model here lets the Luna test task land
 * before the production model exists and makes the persistence boundary reviewable in one place.
 */
data class PortableBackupClientFixture(
    val id: String,
    val name: String,
    val canonicalName: String,
    val active: Boolean,
    val archivedAt: Instant?,
)

data class PortableBackupConsultantFixture(
    val id: String,
    val name: String,
    val canonicalName: String,
    val active: Boolean,
    val archivedAt: Instant?,
)

data class PortableBackupTagFixture(
    val id: String,
    val category: String,
    val text: String,
    val normalizedText: String,
    val createdAt: Instant,
)

data class PortableBackupTagSnapshotFixture(
    val id: String,
    val category: String,
    val order: Int,
    val text: String,
    val sourceTagId: String?,
    val createdAt: Instant,
)

data class PortableBackupIntervalFixture(
    val id: String,
    val taskId: String,
    val start: Instant,
    val stop: Instant,
    val manuallyEdited: Boolean,
)

data class PortableBackupTaskFixture(
    val id: String,
    val seriesId: String,
    val clientId: String,
    val consultantId: String?,
    val consultantNameSnapshot: String?,
    val workDate: LocalDate,
    val zoneId: String,
    val description: String,
    val expense: String,
    val workType: String,
    val billingStatus: String?,
    val mileage: String,
    val notes: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val descriptionTags: List<PortableBackupTagSnapshotFixture>,
    val expenseTags: List<PortableBackupTagSnapshotFixture>,
    val intervals: List<PortableBackupIntervalFixture>,
)

data class PortableBackupExportHistoryFixture(
    val destination: String,
    val workDate: LocalDate,
    val attemptedAt: Instant,
    val outcome: String,
    val errorCategory: String?,
)

data class PortableBackupPreferencesFixture(
    val themeMode: String,
    val timeZoneMode: String,
    val manualZoneId: String?,
    val defaultExportDestination: String,
    val selectedConsultantId: String?,
    val landscapeHandedness: String,
    val selectedTaskId: String?,
    val selectedSeriesId: String?,
    val selectedDate: LocalDate?,
    val selectedZoneId: String?,
    val exportHistory: List<PortableBackupExportHistoryFixture>,
)

data class PortableBackupV1Fixture(
    val name: String,
    val formatVersion: Int,
    val dataModelVersion: Int,
    val clients: List<PortableBackupClientFixture>,
    val consultants: List<PortableBackupConsultantFixture>,
    val tags: List<PortableBackupTagFixture>,
    val tasks: List<PortableBackupTaskFixture>,
    val preferences: PortableBackupPreferencesFixture,
)

data class PortableBackupInvalidCase(
    val name: String,
    val reason: String,
    val expectedFailureCode: String,
)

data class PortableBackupExcludedStateFixture(
    val activeTimer: Boolean,
    val openInterval: Boolean,
    val oauthCredentials: Boolean,
    val googleConnectionMetadata: Boolean,
    val notificationPermission: Boolean,
    val workManagerJob: Boolean,
    val pendingAutomaticExport: Boolean,
    val transientUiState: Boolean,
    val cache: Boolean,
    val recoveryJournal: Boolean,
    val rollingRestorePoint: Boolean,
    val installationExportOrigin: Boolean,
)

object PortableBackupV1Fixtures {
    const val maxCompressedBytes: Long = 100L * 1024L * 1024L
    const val maxExpandedBytes: Long = 500L * 1024L * 1024L

    val createdAt: Instant = Instant.parse("2026-09-20T15:00:00Z")
    val intervalStart: Instant = Instant.parse("2026-09-20T13:00:00Z")
    val intervalStop: Instant = Instant.parse("2026-09-20T14:30:00Z")

    val excludedState =
        PortableBackupExcludedStateFixture(
            activeTimer = true,
            openInterval = true,
            oauthCredentials = true,
            googleConnectionMetadata = true,
            notificationPermission = true,
            workManagerJob = true,
            pendingAutomaticExport = true,
            transientUiState = true,
            cache = true,
            recoveryJournal = true,
            rollingRestorePoint = true,
            installationExportOrigin = true,
        )

    private val descriptionSnapshot =
        PortableBackupTagSnapshotFixture(
            id = "snapshot-description-2",
            category = "DESCRIPTION",
            order = 0,
            text = "東京 inspection 🚀",
            sourceTagId = "tag-description-2",
            createdAt = createdAt,
        )

    private val secondDescriptionSnapshot =
        PortableBackupTagSnapshotFixture(
            id = "snapshot-description-1",
            category = "DESCRIPTION",
            order = 1,
            text = "Confirm access",
            sourceTagId = "tag-description-1",
            createdAt = createdAt.plusSeconds(1),
        )

    private val expenseSnapshot =
        PortableBackupTagSnapshotFixture(
            id = "snapshot-expense-1",
            category = "HARDWARE_SOFTWARE_PURCHASE",
            order = 0,
            text = "Replacement cable",
            sourceTagId = "tag-expense-1",
            createdAt = createdAt,
        )

    val complete =
        PortableBackupV1Fixture(
            name = "complete-domain-state",
            formatVersion = 1,
            dataModelVersion = 7,
            clients =
                listOf(
                    PortableBackupClientFixture(
                        id = "client-active",
                        name = "Café Client",
                        canonicalName = "café client",
                        active = true,
                        archivedAt = null,
                    ),
                    PortableBackupClientFixture(
                        id = "client-archived",
                        name = "Historical Client",
                        canonicalName = "historical client",
                        active = false,
                        archivedAt = createdAt,
                    ),
                ),
            consultants =
                listOf(
                    PortableBackupConsultantFixture(
                        id = "consultant-active",
                        name = "Alex Rivera",
                        canonicalName = "alex rivera",
                        active = true,
                        archivedAt = null,
                    ),
                    PortableBackupConsultantFixture(
                        id = "consultant-archived",
                        name = "Former Consultant",
                        canonicalName = "former consultant",
                        active = false,
                        archivedAt = createdAt,
                    ),
                ),
            tags =
                listOf(
                    PortableBackupTagFixture(
                        id = "tag-description-1",
                        category = "DESCRIPTION",
                        text = "Confirm access",
                        normalizedText = "confirm access",
                        createdAt = createdAt,
                    ),
                    PortableBackupTagFixture(
                        id = "tag-description-2",
                        category = "DESCRIPTION",
                        text = "東京 inspection 🚀",
                        normalizedText = "東京 inspection 🚀",
                        createdAt = createdAt,
                    ),
                    PortableBackupTagFixture(
                        id = "tag-expense-1",
                        category = "HARDWARE_SOFTWARE_PURCHASE",
                        text = "Replacement cable",
                        normalizedText = "replacement cable",
                        createdAt = createdAt,
                    ),
                ),
            tasks =
                listOf(
                    PortableBackupTaskFixture(
                        id = "task-complete",
                        seriesId = "series-complete",
                        clientId = "client-active",
                        consultantId = "consultant-active",
                        consultantNameSnapshot = "Alex Rivera",
                        workDate = LocalDate.of(2026, 9, 20),
                        zoneId = "America/New_York",
                        description = "Inspect network",
                        expense = "",
                        workType = "ON_SITE",
                        billingStatus = "BILLABLE",
                        mileage = "12.5",
                        notes = "Café notes 東京 🚀",
                        createdAt = createdAt,
                        updatedAt = createdAt.plusSeconds(2),
                        descriptionTags = listOf(descriptionSnapshot, secondDescriptionSnapshot),
                        expenseTags = listOf(expenseSnapshot),
                        intervals =
                            listOf(
                                PortableBackupIntervalFixture(
                                    id = "interval-complete",
                                    taskId = "task-complete",
                                    start = intervalStart,
                                    stop = intervalStop,
                                    manuallyEdited = false,
                                ),
                            ),
                    ),
                    PortableBackupTaskFixture(
                        id = "task-untimed",
                        seriesId = "series-untimed",
                        clientId = "client-archived",
                        consultantId = null,
                        consultantNameSnapshot = null,
                        workDate = LocalDate.of(2026, 9, 19),
                        zoneId = "UTC",
                        description = "Historical task",
                        expense = "",
                        workType = "IN_OFFICE",
                        billingStatus = null,
                        mileage = "",
                        notes = "",
                        createdAt = createdAt.minusSeconds(10),
                        updatedAt = createdAt.minusSeconds(10),
                        descriptionTags = emptyList(),
                        expenseTags = emptyList(),
                        intervals = emptyList(),
                    ),
                ),
            preferences =
                PortableBackupPreferencesFixture(
                    themeMode = "DARK",
                    timeZoneMode = "MANUAL",
                    manualZoneId = "America/New_York",
                    defaultExportDestination = "GOOGLE_SHEETS",
                    selectedConsultantId = "consultant-active",
                    landscapeHandedness = "RIGHT_HANDED",
                    selectedTaskId = "task-complete",
                    selectedSeriesId = "series-complete",
                    selectedDate = LocalDate.of(2026, 9, 20),
                    selectedZoneId = "America/New_York",
                    exportHistory =
                        listOf(
                            PortableBackupExportHistoryFixture(
                                destination = "GOOGLE_SHEETS",
                                workDate = LocalDate.of(2026, 9, 20),
                                attemptedAt = createdAt.plusSeconds(3),
                                outcome = "SUCCESS",
                                errorCategory = null,
                            ),
                        ),
                ),
        )

    val empty =
        PortableBackupV1Fixture(
            name = "empty-domain-state",
            formatVersion = 1,
            dataModelVersion = 7,
            clients = emptyList(),
            consultants = emptyList(),
            tags = emptyList(),
            tasks = emptyList(),
            preferences =
                PortableBackupPreferencesFixture(
                    themeMode = "SYSTEM",
                    timeZoneMode = "DEVICE",
                    manualZoneId = null,
                    defaultExportDestination = "CSV",
                    selectedConsultantId = null,
                    landscapeHandedness = "RIGHT_HANDED",
                    selectedTaskId = null,
                    selectedSeriesId = null,
                    selectedDate = null,
                    selectedZoneId = null,
                    exportHistory = emptyList(),
                ),
        )

    val invalidCases =
        listOf(
            PortableBackupInvalidCase("duplicate-id", "two clients share one ID", "DUPLICATE_ID"),
            PortableBackupInvalidCase("broken-reference", "task references missing client", "BROKEN_REFERENCE"),
            PortableBackupInvalidCase("duplicate-snapshot-order", "one task repeats a snapshot ID/order", "DUPLICATE_SNAPSHOT"),
            PortableBackupInvalidCase("active-timer", "active timer singleton is present", "ACTIVE_TIMER_PRESENT"),
            PortableBackupInvalidCase("open-interval", "an interval has no stop instant", "OPEN_INTERVAL"),
            PortableBackupInvalidCase("invalid-enum", "work type is not a known value", "INVALID_ENUM"),
            PortableBackupInvalidCase("invalid-zone", "ZoneId cannot be resolved", "INVALID_ZONE_ID"),
            PortableBackupInvalidCase("invalid-instant", "timestamp cannot be parsed", "INVALID_INSTANT"),
            PortableBackupInvalidCase("overlength-text", "composed text exceeds 999 code points", "TEXT_LIMIT"),
            PortableBackupInvalidCase("overlength-tag", "Tag exceeds 400 code points", "TAG_LIMIT"),
            PortableBackupInvalidCase("unsupported-future", "backup format is newer than this app", "UNSUPPORTED_VERSION"),
            PortableBackupInvalidCase("legacy-v0", "older format requires an explicit upgrader", "UNSUPPORTED_VERSION"),
        )
}
