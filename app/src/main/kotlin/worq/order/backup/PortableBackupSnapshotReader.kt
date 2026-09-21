package worq.order.backup

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import worq.order.data.AppSettings
import worq.order.data.SelectedTaskRepository
import worq.order.data.SelectedTaskState
import worq.order.data.SettingsRepository
import worq.order.data.local.ClientEntity
import worq.order.data.local.DailyTaskEntity
import worq.order.data.local.EmployeeEntity
import worq.order.data.local.TagEntity
import worq.order.data.local.TaskTagSnapshotEntity
import worq.order.data.local.WorkIntervalEntity
import worq.order.data.local.WorqOrderDatabase

sealed interface PortableBackupSnapshotReadResult {
    data class Ready(
        val data: PortableBackupDataV1,
    ) : PortableBackupSnapshotReadResult

    /** A backup may never capture an open interval or active timer. */
    data object TimerRunning : PortableBackupSnapshotReadResult

    /** The authoritative local state could not be converted into a valid logical backup. */
    data object InvalidLocalState : PortableBackupSnapshotReadResult
}

fun interface PortableBackupSnapshotReader {
    suspend fun read(): PortableBackupSnapshotReadResult
}

/**
 * Reads the portable logical graph from Room in one read transaction. Preferences are intentionally
 * adapted into explicit portable values instead of serializing their implementation format.
 */
class RoomPortableBackupSnapshotReader internal constructor(
    private val database: WorqOrderDatabase,
    private val readSettings: suspend () -> AppSettings,
    private val readSelection: suspend () -> SelectedTaskState?,
) : PortableBackupSnapshotReader {
    constructor(
        database: WorqOrderDatabase,
        settingsRepository: SettingsRepository,
        selectedTaskRepository: SelectedTaskRepository,
    ) : this(
        database = database,
        readSettings = settingsRepository::readSettings,
        readSelection = selectedTaskRepository::readSelection,
    )

    override suspend fun read(): PortableBackupSnapshotReadResult {
        val settings = readSettings()
        val requestedSelection = readSelection()

        return try {
            database.withTransaction {
                if (database.activeTimerDao().readActiveTimer() != null) {
                    return@withTransaction PortableBackupSnapshotReadResult.TimerRunning
                }

                val clients = database.clientDao().readAllClientsForPortableBackup()
                val consultants = database.employeeDao().readAllEmployeesForPortableBackup()
                val tags = database.tagDao().readAllTagsForPortableBackup()
                val tasks = database.taskDao().readAllTasksForPortableBackup()
                val snapshots = database.taskDao().readAllTaskTagSnapshotsForPortableBackup()
                val intervals = database.workIntervalDao().readAllIntervalsForPortableBackup()

                if (intervals.any { it.stopEpochMs == null || it.activeSlot != null }) {
                    return@withTransaction PortableBackupSnapshotReadResult.TimerRunning
                }

                val activeConsultantIds =
                    consultants
                        .asSequence()
                        .filter { consultant -> consultant.isActive }
                        .mapTo(mutableSetOf()) { consultant -> consultant.id }
                val taskById = tasks.associateBy(DailyTaskEntity::id)
                val snapshotsByTaskId = snapshots.groupBy(TaskTagSnapshotEntity::taskId)
                val intervalsByTaskId = intervals.groupBy(WorkIntervalEntity::taskId)
                val validSelection =
                    requestedSelection?.takeIf { selection ->
                        taskById[selection.taskId]?.let { task ->
                            task.seriesId == selection.seriesId &&
                                task.workDateEpochDay == selection.selectedOnDate.toEpochDay() &&
                                task.zoneId == selection.selectedInZone.id
                        } == true
                    }
                val data =
                    PortableBackupDataV1(
                        dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
                        clients = clients.map { client -> client.toPortable() },
                        consultants = consultants.map { consultant -> consultant.toPortable() },
                        tags = tags.map { tag -> tag.toPortable() },
                        tasks =
                            tasks.map { task ->
                                task.toPortable(
                                    snapshots = snapshotsByTaskId[task.id].orEmpty(),
                                    intervals = intervalsByTaskId[task.id].orEmpty(),
                                )
                            },
                        settings =
                            PortableBackupSettingsV1(
                                themeMode = settings.themeMode.name,
                                timeZoneMode = settings.timeZoneMode.name,
                                manualZoneId = settings.manualZoneId?.id,
                                defaultExportDestination = settings.defaultExportDestination.name,
                                lastExportAttempt =
                                    settings.lastExportAttempt?.let { attempt ->
                                        PortableBackupLastExportAttemptV1(
                                            destination = attempt.destination.name,
                                            workDateEpochDay = attempt.workDate.toEpochDay(),
                                            attemptedAtEpochMs = attempt.attemptedAt.toEpochMilli(),
                                            outcome = attempt.outcome.name,
                                            errorCategory = attempt.errorCategory?.name,
                                        )
                                    },
                                selectedConsultantId =
                                    settings.selectedEmployeeId?.takeIf(activeConsultantIds::contains),
                                landscapeHandedness = settings.landscapeHandedness.name,
                            ),
                        selection = validSelection?.toPortable(),
                    )

                when (PortableBackupValidator.validateData(data)) {
                    is PortableBackupDataValidationResult.Valid ->
                        PortableBackupSnapshotReadResult.Ready(data)
                    is PortableBackupDataValidationResult.Invalid ->
                        PortableBackupSnapshotReadResult.InvalidLocalState
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PortableBackupSnapshotReadResult.InvalidLocalState
        }
    }

    private fun ClientEntity.toPortable(): PortableBackupClientV1 =
        PortableBackupClientV1(
            id = id,
            name = name,
            canonicalName = canonicalName,
            isActive = isActive,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            archivedAtEpochMs = archivedAtEpochMs,
        )

    private fun EmployeeEntity.toPortable(): PortableBackupConsultantV1 =
        PortableBackupConsultantV1(
            id = id,
            name = name,
            canonicalName = canonicalName,
            isActive = isActive,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            archivedAtEpochMs = archivedAtEpochMs,
        )

    private fun TagEntity.toPortable(): PortableBackupTagV1 =
        PortableBackupTagV1(
            id = id,
            category = category,
            text = text,
            normalizedText = normalizedText,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )

    private fun DailyTaskEntity.toPortable(
        snapshots: List<TaskTagSnapshotEntity>,
        intervals: List<WorkIntervalEntity>,
    ): PortableBackupTaskV1 =
        PortableBackupTaskV1(
            id = id,
            seriesId = seriesId,
            clientId = clientId,
            consultantId = employeeId,
            consultantNameSnapshot = employeeNameSnapshot,
            description = description,
            hardwareSoftwarePurchases = hardwareSoftwarePurchases,
            workType = workType,
            billingStatus = billingStatus,
            mileage = mileage,
            notes = notes,
            workDateEpochDay = workDateEpochDay,
            zoneId = zoneId,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            tagSnapshots = snapshots.map { snapshot -> snapshot.toPortable() },
            intervals = intervals.map { interval -> interval.toPortable() },
        )

    private fun TaskTagSnapshotEntity.toPortable(): PortableBackupTaskTagSnapshotV1 =
        PortableBackupTaskTagSnapshotV1(
            id = id,
            category = category,
            text = textSnapshot,
            sourceTagId = sourceTagId,
            selectionOrder = selectionOrder,
            createdAtEpochMs = createdAtEpochMs,
        )

    private fun WorkIntervalEntity.toPortable(): PortableBackupIntervalV1 =
        PortableBackupIntervalV1(
            id = id,
            taskId = taskId,
            startEpochMs = startEpochMs,
            stopEpochMs = stopEpochMs,
            wasManuallyEdited = wasManuallyEdited,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )

    private fun SelectedTaskState.toPortable(): PortableBackupSelectionV1 =
        PortableBackupSelectionV1(
            taskId = taskId,
            seriesId = seriesId,
            selectedOnEpochDay = selectedOnDate.toEpochDay(),
            selectedInZoneId = selectedInZone.id,
        )
}
