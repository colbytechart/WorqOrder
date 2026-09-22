package worq.order.backup

import androidx.room.withTransaction
import worq.order.data.local.ClientEntity
import worq.order.data.local.DailyTaskEntity
import worq.order.data.local.EmployeeEntity
import worq.order.data.local.TagEntity
import worq.order.data.local.TaskTagSnapshotEntity
import worq.order.data.local.WorkIntervalEntity
import worq.order.data.local.WorqOrderDatabase

/**
 * The sole Room mutation boundary for a validated portable replacement. The caller owns the
 * application-wide replacement lock; this class deliberately bypasses normal repository flows so
 * it cannot expose a partially written task graph between individual CRUD calls.
 */
class PortableBackupRoomReplacement(
    private val database: WorqOrderDatabase,
) {
    suspend fun replace(data: PortableBackupDataV1) {
        require(PortableBackupValidator.validateData(data) is PortableBackupDataValidationResult.Valid) {
            "Portable state must be valid before replacing Room"
        }
        val entities = data.toEntities()
        database.withTransaction {
            check(database.activeTimerDao().readActiveTimer() == null) {
                "A portable replacement cannot run while a timer is active"
            }
            check(
                database.workIntervalDao().readAllIntervalsForPortableBackup().none { interval ->
                    interval.stopEpochMs == null || interval.activeSlot != null
                },
            ) {
                "A portable replacement cannot run while an interval is open"
            }

            database.activeTimerDao().deleteAllForPortableReplacement()
            database.workIntervalDao().deleteAllForPortableReplacement()
            database.taskDao().deleteAllSnapshotsForPortableReplacement()
            database.taskDao().deleteAllForPortableReplacement()
            database.tagDao().deleteAllForPortableReplacement()
            database.clientDao().deleteAllForPortableReplacement()
            database.employeeDao().deleteAllForPortableReplacement()

            database.clientDao().insertAllForPortableReplacement(entities.clients)
            database.employeeDao().insertAllForPortableReplacement(entities.consultants)
            database.tagDao().insertAllForPortableReplacement(entities.tags)
            database.taskDao().insertAllForPortableReplacement(entities.tasks)
            database.taskDao().insertAllSnapshotsForPortableReplacement(entities.snapshots)
            database.workIntervalDao().insertAllForPortableReplacement(entities.intervals)

            check(
                database.clientDao().readAllClientsForPortableBackup().map(ClientEntity::id).sorted() ==
                    entities.clients.map(ClientEntity::id).sorted(),
            ) { "Portable replacement client verification failed" }
            check(
                database.employeeDao().readAllEmployeesForPortableBackup().map(EmployeeEntity::id).sorted() ==
                    entities.consultants.map(EmployeeEntity::id).sorted(),
            ) { "Portable replacement Consultant verification failed" }
            check(
                database.tagDao().readAllTagsForPortableBackup().map(TagEntity::id).sorted() ==
                    entities.tags.map(TagEntity::id).sorted(),
            ) { "Portable replacement Tag verification failed" }
            check(
                database.taskDao().readAllTasksForPortableBackup().map(DailyTaskEntity::id).sorted() ==
                    entities.tasks.map(DailyTaskEntity::id).sorted(),
            ) { "Portable replacement task verification failed" }
            check(
                database.taskDao().readAllTaskTagSnapshotsForPortableBackup().map(TaskTagSnapshotEntity::id).sorted() ==
                    entities.snapshots.map(TaskTagSnapshotEntity::id).sorted(),
            ) { "Portable replacement Tag snapshot verification failed" }
            check(
                database.workIntervalDao().readAllIntervalsForPortableBackup().map(WorkIntervalEntity::id).sorted() ==
                    entities.intervals.map(WorkIntervalEntity::id).sorted(),
            ) { "Portable replacement interval verification failed" }
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                check(!cursor.moveToFirst()) { "Portable replacement foreign-key verification failed" }
            }
            check(database.activeTimerDao().readActiveTimer() == null) {
                "Portable replacement unexpectedly created an active timer"
            }
            check(
                database.workIntervalDao().readAllIntervalsForPortableBackup().all { interval ->
                    interval.stopEpochMs != null && interval.activeSlot == null
                },
            ) {
                "Portable replacement unexpectedly created an open interval"
            }
        }
    }
}

private data class PortableBackupReplacementEntities(
    val clients: List<ClientEntity>,
    val consultants: List<EmployeeEntity>,
    val tags: List<TagEntity>,
    val tasks: List<DailyTaskEntity>,
    val snapshots: List<TaskTagSnapshotEntity>,
    val intervals: List<WorkIntervalEntity>,
)

private fun PortableBackupDataV1.toEntities(): PortableBackupReplacementEntities =
    PortableBackupReplacementEntities(
        clients =
            clients.map { client ->
                ClientEntity(
                    id = client.id,
                    name = client.name,
                    canonicalName = client.canonicalName,
                    activeNameKey = client.canonicalName.takeIf { client.isActive },
                    isActive = client.isActive,
                    createdAtEpochMs = client.createdAtEpochMs,
                    updatedAtEpochMs = client.updatedAtEpochMs,
                    archivedAtEpochMs = client.archivedAtEpochMs,
                )
            },
        consultants =
            consultants.map { consultant ->
                EmployeeEntity(
                    id = consultant.id,
                    name = consultant.name,
                    canonicalName = consultant.canonicalName,
                    activeNameKey = consultant.canonicalName.takeIf { consultant.isActive },
                    isActive = consultant.isActive,
                    createdAtEpochMs = consultant.createdAtEpochMs,
                    updatedAtEpochMs = consultant.updatedAtEpochMs,
                    archivedAtEpochMs = consultant.archivedAtEpochMs,
                )
            },
        tags =
            tags.map { tag ->
                TagEntity(
                    id = tag.id,
                    category = tag.category,
                    text = tag.text,
                    normalizedText = tag.normalizedText,
                    createdAtEpochMs = tag.createdAtEpochMs,
                    updatedAtEpochMs = tag.updatedAtEpochMs,
                )
            },
        tasks =
            tasks.map { task ->
                DailyTaskEntity(
                    id = task.id,
                    seriesId = task.seriesId,
                    clientId = task.clientId,
                    description = task.description,
                    hardwareSoftwarePurchases = task.hardwareSoftwarePurchases,
                    employeeId = task.consultantId,
                    employeeNameSnapshot = task.consultantNameSnapshot,
                    workType = task.workType,
                    billingStatus = task.billingStatus,
                    mileage = task.mileage,
                    notes = task.notes,
                    workDateEpochDay = task.workDateEpochDay,
                    zoneId = task.zoneId,
                    createdAtEpochMs = task.createdAtEpochMs,
                    updatedAtEpochMs = task.updatedAtEpochMs,
                )
            },
        snapshots =
            tasks.flatMap { task ->
                task.tagSnapshots.map { snapshot ->
                    TaskTagSnapshotEntity(
                        id = snapshot.id,
                        taskId = task.id,
                        category = snapshot.category,
                        textSnapshot = snapshot.text,
                        sourceTagId = snapshot.sourceTagId,
                        selectionOrder = snapshot.selectionOrder,
                        createdAtEpochMs = snapshot.createdAtEpochMs,
                    )
                }
            },
        intervals =
            tasks.flatMap { task ->
                task.intervals.map { interval ->
                    WorkIntervalEntity(
                        id = interval.id,
                        taskId = task.id,
                        startEpochMs = interval.startEpochMs,
                        stopEpochMs = requireNotNull(interval.stopEpochMs),
                        activeSlot = null,
                        wasManuallyEdited = interval.wasManuallyEdited,
                        createdAtEpochMs = interval.createdAtEpochMs,
                        updatedAtEpochMs = interval.updatedAtEpochMs,
                    )
                }
            },
    )
