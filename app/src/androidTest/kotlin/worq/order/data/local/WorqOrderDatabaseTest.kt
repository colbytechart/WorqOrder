package worq.order.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.ClientMutationResult
import worq.order.data.CreateActiveIntervalResult
import worq.order.data.EntityIdGenerator
import worq.order.data.TimerSplitBoundary
import worq.order.model.Client
import worq.order.timer.UtcClock

@RunWith(AndroidJUnit4::class)
class WorqOrderDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: WorqOrderDatabase

    @Before
    fun createDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database =
            Room
                .inMemoryDatabaseBuilder(context, WorqOrderDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun databaseCreatesExpectedTablesWithForeignKeysEnabled() {
        val sqlite = database.openHelper.writableDatabase

        val tableNames =
            sqlite
                .query(
                    """
                    SELECT name
                    FROM sqlite_master
                    WHERE type = 'table'
                    """.trimIndent(),
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(0))
                        }
                    }
                }
        val foreignKeysEnabled =
            sqlite.query("PRAGMA foreign_keys").use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) == 1
            }

        assertTrue(tableNames.containsAll(EXPECTED_TABLES))
        assertTrue(foreignKeysEnabled)
    }

    @Test
    fun clientInsertIsObservedInAlphabeticalOrder() =
        runBlocking {
            insertClient(id = "client-z", name = "Zulu", canonicalName = "zulu")
            insertClient(id = "client-a", name = "Alpha", canonicalName = "alpha")

            val clients = database.clientDao().observeActiveClients().first()

            assertEquals(listOf("Alpha", "Zulu"), clients.map(ClientEntity::name))
        }

    @Test
    fun duplicateNormalizedActiveClientIsRejectedByRepositoryAndDatabase() =
        runBlocking {
            val repository =
                RoomClientRepository(
                    clientDao = database.clientDao(),
                    idGenerator = QueueIdGenerator("client-1", "client-2"),
                    clock = FixedClock(TEST_NOW),
                )

            val first = repository.addClient("Acme Corp")
            val duplicate = repository.addClient("  ACME\t corp ")

            assertTrue(first is ClientMutationResult.Success)
            assertEquals(
                ClientMutationResult.DuplicateActiveName("client-1"),
                duplicate,
            )

            expectConstraintFailure {
                database.clientDao().addClient(
                    clientEntity(
                        id = "client-direct-duplicate",
                        name = "ACME CORP",
                        canonicalName = "acme corp",
                    ),
                )
            }
        }

    @Test
    fun addingMatchingArchivedClientOffersRestoreInsteadOfCreatingDuplicate() =
        runBlocking {
            database.clientDao().addClient(
                clientEntity(
                    id = "archived-client",
                    name = "Acme Corp",
                    canonicalName = "acme corp",
                ).copy(
                    activeNameKey = null,
                    isActive = false,
                    archivedAtEpochMs = 2_000,
                ),
            )
            val repository =
                RoomClientRepository(
                    clientDao = database.clientDao(),
                    idGenerator = QueueIdGenerator("must-not-be-used"),
                    clock = FixedClock(TEST_NOW),
                )

            val result = repository.addClient("  ACME\t corp ")

            assertTrue(result is ClientMutationResult.MatchingArchivedClient)
            assertEquals(
                "archived-client",
                (result as ClientMutationResult.MatchingArchivedClient).client.id,
            )
            assertEquals(1, database.clientDao().observeAllClients().first().size)
        }

    @Test
    fun renameUpdatesHistoricalTaskJoinWithoutRewritingTask() =
        runBlocking {
            insertClient(id = "client-1", name = "Original Name")
            insertTask(id = "task-1", clientId = "client-1")
            val taskBefore = requireNotNull(database.taskDao().readTask("task-1"))
            val repository =
                RoomClientRepository(
                    clientDao = database.clientDao(),
                    idGenerator = QueueIdGenerator(),
                    clock = FixedClock(TEST_NOW),
                )

            val result = repository.renameClient("client-1", " Updated   Name ")
            val joined = requireNotNull(database.taskDao().readTaskWithClient("task-1"))

            assertTrue(result is ClientMutationResult.Success)
            assertEquals("Updated Name", joined.clientName)
            assertEquals(taskBefore, database.taskDao().readTask("task-1"))
        }

    @Test
    fun renameAndRestoreRejectActiveNormalizedNameConflicts() =
        runBlocking {
            insertClient(id = "active-acme", name = "Acme", canonicalName = "acme")
            insertClient(id = "active-beta", name = "Beta", canonicalName = "beta")
            database.clientDao().addClient(
                clientEntity(
                    id = "archived-acme",
                    name = "ACME",
                    canonicalName = "acme",
                ).copy(
                    activeNameKey = null,
                    isActive = false,
                    archivedAtEpochMs = 2_000,
                ),
            )
            val repository =
                RoomClientRepository(
                    clientDao = database.clientDao(),
                    idGenerator = QueueIdGenerator(),
                    clock = FixedClock(TEST_NOW),
                )

            assertEquals(
                ClientMutationResult.DuplicateActiveName("active-acme"),
                repository.renameClient("active-beta", " ACME "),
            )
            assertEquals(
                ClientMutationResult.DuplicateActiveName("active-acme"),
                repository.restoreClient("archived-acme"),
            )
            assertEquals(
                "Beta",
                database.clientDao().readClient("active-beta")?.name,
            )
            assertFalse(
                requireNotNull(
                    database.clientDao().readClient("archived-acme"),
                ).isActive,
            )
        }

    @Test
    fun archivingClientPreservesHistoricalTaskJoin() =
        runBlocking {
            insertClient(id = "client-1", name = "Historical Client")
            insertTask(id = "task-1", clientId = "client-1")
            val repository =
                RoomClientRepository(
                    clientDao = database.clientDao(),
                    idGenerator = QueueIdGenerator(),
                    clock = FixedClock(TEST_NOW.plusSeconds(60)),
                )

            val archived = repository.archiveClient("client-1")
            val joinedTask = database.taskDao().readTaskWithClient("task-1")

            assertTrue(archived is ClientMutationResult.Success)
            assertFalse((archived as ClientMutationResult.Success).client.isActive)
            assertEquals("Historical Client", joinedTask?.clientName)
            assertFalse(requireNotNull(joinedTask).clientIsActive)
            assertTrue(database.clientDao().observeActiveClients().first().isEmpty())
            assertEquals(1, database.clientDao().observeAllClients().first().size)
        }

    @Test
    fun exportSnapshotReadIsDateFilteredJoinedAndIntervalOrdered() =
        runBlocking {
            insertClient(id = "client-1", name = "Export Client")
            insertTask(id = "task-export", clientId = "client-1")
            insertTask(
                id = "task-other-date",
                clientId = "client-1",
                seriesId = "series-other",
                workDateEpochDay = TEST_DATE.plusDays(1).toEpochDay(),
            )
            insertCompletedInterval(
                id = "later",
                taskId = "task-export",
                startEpochMs = 3_000,
                stopEpochMs = 4_000,
            )
            insertCompletedInterval(
                id = "earlier",
                taskId = "task-export",
                startEpochMs = 1_000,
                stopEpochMs = 2_000,
            )

            val snapshot =
                database
                    .taskDao()
                    .readTasksWithOrderedIntervalsForWorkDate(
                        TEST_DATE.toEpochDay(),
                    )

            assertEquals(1, snapshot.size)
            assertEquals("task-export", snapshot.single().taskWithClient.task.id)
            assertEquals("Export Client", snapshot.single().taskWithClient.clientName)
            assertEquals(
                listOf("earlier", "later"),
                snapshot.single().intervals.map(WorkIntervalEntity::id),
            )
        }

    @Test
    fun clientRenameAndArchiveDoNotDisturbRelatedRunningTimer() =
        runBlocking {
            insertClient(id = "client-1", name = "Running Client")
            insertTask(id = "task-1", clientId = "client-1")
            database.activeTimerDao().createActiveIntervalAndTimer(
                intervalId = "active-interval",
                taskId = "task-1",
                boundaryZoneId = TEST_ZONE.id,
                startEpochMs = 1_000,
                createdAtEpochMs = 1_000,
            )
            val repository =
                RoomClientRepository(
                    clientDao = database.clientDao(),
                    idGenerator = QueueIdGenerator(),
                    clock = FixedClock(TEST_NOW),
                )

            assertTrue(
                repository.renameClient("client-1", "Renamed Client") is
                    ClientMutationResult.Success,
            )
            assertTrue(
                repository.archiveClient("client-1") is
                    ClientMutationResult.Success,
            )

            val active = requireNotNull(database.activeTimerDao().readActiveTimer())
            val joined = requireNotNull(database.taskDao().readTaskWithClient("task-1"))
            assertEquals("active-interval", active.intervalId)
            assertEquals("task-1", active.taskId)
            assertEquals("Renamed Client", joined.clientName)
            assertFalse(joined.clientIsActive)
        }

    @Test
    fun taskInsertionFiltersByIndependentWorkDate() =
        runBlocking {
            insertClient(id = "client-1")
            val firstDate = LocalDate.of(2026, 7, 22)
            val secondDate = firstDate.plusDays(1)
            insertTask(
                id = "task-1",
                clientId = "client-1",
                workDateEpochDay = firstDate.toEpochDay(),
            )
            insertTask(
                id = "task-2",
                clientId = "client-1",
                seriesId = "series-2",
                workDateEpochDay = secondDate.toEpochDay(),
            )

            val firstDateTasks =
                database.taskDao().observeTasksForWorkDate(firstDate.toEpochDay()).first()

            assertEquals(listOf("task-1"), firstDateTasks.map { it.task.id })
        }

    @Test
    fun taskSeriesDateZoneKeyIsUniqueAndDifferentZoneRemainsDistinct() =
        runBlocking {
            insertClient(id = "client-1")
            insertEmployee(id = "employee-1", name = "Alex Rivera")
            insertTask(id = "task-1", clientId = "client-1")

            expectConstraintFailure {
                insertTask(
                    id = "task-duplicate",
                    clientId = "client-1",
                )
            }
            insertTask(
                id = "task-other-zone",
                clientId = "client-1",
                zoneId = "America/Chicago",
            )

            val tasks =
                database
                    .taskDao()
                    .observeTasksForWorkDate(TEST_DATE.toEpochDay())
                    .first()
            assertEquals(2, tasks.size)
        }

    @Test
    fun multipleIntervalsUseStableOrdinalsAndChronologicalDetailOrder() =
        runBlocking {
            insertClient(id = "client-1")
            insertEmployee(id = "employee-1", name = "Alex Rivera")
            insertTask(id = "task-1", clientId = "client-1")

            val later =
                insertCompletedInterval(
                    id = "interval-later",
                    taskId = "task-1",
                    startEpochMs = 2_000,
                    stopEpochMs = 3_000,
                )
            val earlier =
                insertCompletedInterval(
                    id = "interval-earlier",
                    taskId = "task-1",
                    startEpochMs = 500,
                    stopEpochMs = 1_500,
                )
            val detail = requireNotNull(database.taskDao().readTaskWithOrderedIntervals("task-1"))

            assertEquals(1, later.ordinal)
            assertEquals(2, earlier.ordinal)
            assertEquals(
                listOf("interval-earlier", "interval-later"),
                detail.intervals.map(WorkIntervalEntity::id),
            )
            assertEquals(2_000L, database.workIntervalDao().readCompletedDurationMs("task-1"))
        }

    @Test
    fun deletingTaskCascadesToIntervalsButRetainsClient() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")
            insertCompletedInterval(
                id = "interval-1",
                taskId = "task-1",
                startEpochMs = 1_000,
                stopEpochMs = 2_000,
            )

            assertEquals(1, database.taskDao().deleteTask("task-1"))

            assertEquals(0, database.workIntervalDao().countIntervalsForTask("task-1"))
            assertNotNull(database.clientDao().readClient("client-1"))
        }

    @Test
    fun activeIntervalAndSingletonPointerAreCreatedAtomically() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")

            val transaction =
                database.activeTimerDao().createActiveIntervalAndTimer(
                    intervalId = "interval-active",
                    taskId = "task-1",
                    boundaryZoneId = TEST_ZONE.id,
                    startEpochMs = 1_000,
                    createdAtEpochMs = 1_000,
                )

            assertEquals("interval-active", transaction.activeTimer.intervalId)
            assertEquals("task-1", transaction.activeTimer.taskId)
            assertNull(transaction.interval.stopEpochMs)
            assertEquals(WorkIntervalEntity.ACTIVE_SLOT, transaction.interval.activeSlot)
            assertEquals(transaction.activeTimer, database.activeTimerDao().readActiveTimer())
            assertEquals(
                transaction.interval,
                database.workIntervalDao().readInterval("interval-active"),
            )
        }

    @Test
    fun secondGlobalActiveTimerIsPrevented() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")
            insertTask(
                id = "task-2",
                clientId = "client-1",
                seriesId = "series-2",
            )
            val repository =
                RoomActiveTimerRepository(
                    activeTimerDao = database.activeTimerDao(),
                    idGenerator = QueueIdGenerator("interval-1", "interval-2"),
                    clock = FixedClock(TEST_NOW),
                )

            val first =
                repository.createActiveInterval(
                    taskId = "task-1",
                    boundaryZoneId = TEST_ZONE,
                    start = TEST_NOW,
                )
            val second =
                repository.createActiveInterval(
                    taskId = "task-2",
                    boundaryZoneId = TEST_ZONE,
                    start = TEST_NOW.plusSeconds(1),
                )

            assertTrue(first is CreateActiveIntervalResult.Created)
            assertEquals(CreateActiveIntervalResult.AlreadyActive, second)
            assertEquals(1, database.workIntervalDao().countIntervalsForTask("task-1"))
            assertEquals(0, database.workIntervalDao().countIntervalsForTask("task-2"))
            assertEquals("task-1", database.activeTimerDao().readActiveTimer()?.taskId)
        }

    @Test
    fun activeSlotUniqueIndexStructurallyPreventsSecondOpenInterval() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")
            insertTask(
                id = "task-2",
                clientId = "client-1",
                seriesId = "series-2",
            )
            database.activeTimerDao().createActiveIntervalAndTimer(
                intervalId = "interval-1",
                taskId = "task-1",
                boundaryZoneId = TEST_ZONE.id,
                startEpochMs = 1_000,
                createdAtEpochMs = 1_000,
            )

            expectConstraintFailure {
                database.openHelper.writableDatabase.execSQL(
                    """
                    INSERT INTO work_intervals (
                        id,
                        task_id,
                        ordinal,
                        start_epoch_ms,
                        stop_epoch_ms,
                        active_slot,
                        was_manually_edited,
                        created_at_epoch_ms,
                        updated_at_epoch_ms
                    ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        "interval-2",
                        "task-2",
                        1,
                        2_000,
                        WorkIntervalEntity.ACTIVE_SLOT,
                        0,
                        2_000,
                        2_000,
                    ),
                )
            }
        }

    @Test
    fun deletingActiveTaskIsRestrictedUntilTimerIsClosed() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")
            database.activeTimerDao().createActiveIntervalAndTimer(
                intervalId = "interval-active",
                taskId = "task-1",
                boundaryZoneId = TEST_ZONE.id,
                startEpochMs = 1_000,
                createdAtEpochMs = 1_000,
            )

            expectConstraintFailure {
                database.taskDao().deleteTask("task-1")
            }

            assertNotNull(database.taskDao().readTask("task-1"))
            assertNotNull(database.activeTimerDao().readActiveTimer())
        }

    @Test
    fun closingActiveIntervalAndClearingPointerIsAtomic() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")
            database.activeTimerDao().createActiveIntervalAndTimer(
                intervalId = "interval-active",
                taskId = "task-1",
                boundaryZoneId = TEST_ZONE.id,
                startEpochMs = 1_000,
                createdAtEpochMs = 1_000,
            )

            val closed =
                database.activeTimerDao().closeActiveIntervalAndClearTimer(
                    stopEpochMs = 2_500,
                    updatedAtEpochMs = 2_500,
                )

            assertEquals(2_500L, closed?.interval?.stopEpochMs)
            assertNull(closed?.interval?.activeSlot)
            assertNull(database.activeTimerDao().readActiveTimer())
            assertEquals(
                2_500L,
                database.workIntervalDao().readInterval("interval-active")?.stopEpochMs,
            )
            assertEquals(1_500L, database.workIntervalDao().readCompletedDurationMs("task-1"))
        }

    @Test
    fun taskCreationRequiresClientToRemainActiveAtTransactionTime() =
        runBlocking {
            insertClient(id = "client-1")
            val clientRepository =
                RoomClientRepository(
                    clientDao = database.clientDao(),
                    idGenerator = QueueIdGenerator(),
                    clock = FixedClock(TEST_NOW),
                )
            val taskRepository =
                RoomTaskRepository(
                    taskDao = database.taskDao(),
                    workIntervalDao = database.workIntervalDao(),
                    idGenerator =
                        QueueIdGenerator(
                            "task-must-not-persist",
                            "series-must-not-persist",
                        ),
                    clock = FixedClock(TEST_NOW),
                )
            clientRepository.archiveClient("client-1")

            val result =
                taskRepository.createDailyTask(
                    worq.order.data.NewDailyTask(
                        clientId = "client-1",
                        description = "Task",
                        hardwareSoftwarePurchases = "Laptop",
                        workDate = TEST_DATE,
                        zoneId = TEST_ZONE,
                    ),
                )

            assertEquals(worq.order.data.CreateDailyTaskResult.ClientUnavailable, result)
            assertNull(database.taskDao().readTask("task-must-not-persist"))
        }

    @Test
    fun taskMetadataAndManualIntervalsUseTransactionalGuards() =
        runBlocking {
            insertClient(id = "client-1")
            insertEmployee(id = "employee-1", name = "Alex Rivera")
            insertTask(id = "task-1", clientId = "client-1")
            val repository =
                RoomTaskRepository(
                    taskDao = database.taskDao(),
                    workIntervalDao = database.workIntervalDao(),
                    idGenerator =
                        QueueIdGenerator(
                            "manual-1",
                            "manual-overlap",
                        ),
                    clock = FixedClock(TEST_NOW),
                )

            val updated =
                repository.updateTaskMetadata(
                    taskId = "task-1",
                    clientId = "client-1",
                    description = " Updated task ",
                    hardwareSoftwarePurchases = " Laptop and IDE ",
                    employeeId = "employee-1",
                    workType = worq.order.model.WorkType.IN_OFFICE,
                    mileage = "0012.500",
                )
            assertTrue(updated is worq.order.data.UpdateTaskMetadataResult.Updated)
            val task = requireNotNull(database.taskDao().readTask("task-1"))
            assertEquals("Updated task", task.description)
            assertEquals("Laptop and IDE", task.hardwareSoftwarePurchases)
            assertEquals("employee-1", task.employeeId)
            assertEquals("Alex Rivera", task.employeeNameSnapshot)
            assertEquals("IN_OFFICE", task.workType)
            assertEquals("12.5", task.mileage)

            database.employeeDao().archive("employee-1", TEST_NOW.toEpochMilli())
            assertEquals(
                worq.order.data.UpdateTaskMetadataResult.EmployeeUnavailable,
                repository.updateTaskMetadata(
                    taskId = "task-1",
                    clientId = "client-1",
                    description = "Must not persist",
                    hardwareSoftwarePurchases = "",
                    employeeId = "employee-1",
                    workType = worq.order.model.WorkType.ON_SITE,
                    mileage = "1",
                ),
            )
            assertEquals(
                "Updated task",
                database.taskDao().readTask("task-1")?.description,
            )

            val added =
                repository.addManualInterval(
                    taskId = "task-1",
                    start = Instant.ofEpochMilli(2_000),
                    stop = Instant.ofEpochMilli(3_000),
                )
            assertTrue(added is worq.order.data.ManualIntervalPersistenceResult.Saved)
            val overlap =
                repository.addManualInterval(
                    taskId = "task-1",
                    start = Instant.ofEpochMilli(2_500),
                    stop = Instant.ofEpochMilli(3_500),
                )
            assertEquals(
                worq.order.data.ManualIntervalPersistenceResult.Overlap,
                overlap,
            )

            val edited =
                repository.updateManualInterval(
                    taskId = "task-1",
                    intervalId = "manual-1",
                    start = Instant.ofEpochMilli(4_000),
                    stop = Instant.ofEpochMilli(6_000),
                )
            assertTrue(edited is worq.order.data.ManualIntervalPersistenceResult.Saved)
            assertEquals(
                2_000L,
                database.workIntervalDao().readCompletedDurationMs("task-1"),
            )
            assertEquals(
                worq.order.data.ManualIntervalPersistenceResult.Deleted,
                repository.deleteManualInterval("task-1", "manual-1"),
            )
            assertEquals(
                0L,
                database.workIntervalDao().readCompletedDurationMs("task-1"),
            )
        }

    @Test
    fun runningTaskRejectsMetadataIntervalAndTaskMutations() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")
            database.activeTimerDao().createActiveIntervalAndTimer(
                intervalId = "active-interval",
                taskId = "task-1",
                boundaryZoneId = TEST_ZONE.id,
                startEpochMs = 1_000,
                createdAtEpochMs = 1_000,
            )
            val repository =
                RoomTaskRepository(
                    taskDao = database.taskDao(),
                    workIntervalDao = database.workIntervalDao(),
                    idGenerator = QueueIdGenerator("unused"),
                    clock = FixedClock(TEST_NOW),
                )

            assertEquals(
                worq.order.data.UpdateTaskMetadataResult.RunningTask,
                repository.updateTaskMetadata(
                    taskId = "task-1",
                    clientId = "client-1",
                    description = "Changed",
                    hardwareSoftwarePurchases = "",
                    employeeId = null,
                    workType = worq.order.model.WorkType.UNSPECIFIED,
                    mileage = null,
                ),
            )
            assertEquals(
                worq.order.data.ManualIntervalPersistenceResult.RunningTask,
                repository.addManualInterval(
                    taskId = "task-1",
                    start = Instant.ofEpochMilli(100),
                    stop = Instant.ofEpochMilli(500),
                ),
            )
            assertEquals(
                worq.order.data.DeleteTaskResult.RunningTask,
                repository.deleteTask("task-1"),
            )
            assertNotNull(database.taskDao().readTask("task-1"))
        }

    @Test
    fun activeTimerNormalizationCreatesDailyContinuationAndIsIdempotent() =
        runBlocking {
            insertClient(id = "client-1")
            insertEmployee(id = "employee-1", name = "Alex Rivera")
            insertTask(
                id = "task-day-1",
                clientId = "client-1",
                hardwareSoftwarePurchases = "Laptop",
                employeeId = "employee-1",
                employeeNameSnapshot = "Alex Rivera",
                workType = "ON_SITE",
                mileage = "18.5",
                workDateEpochDay = LocalDate.of(2026, 7, 24).toEpochDay(),
            )
            val repository =
                RoomActiveTimerRepository(
                    activeTimerDao = database.activeTimerDao(),
                    idGenerator =
                        QueueIdGenerator(
                            "interval-day-1",
                            "task-day-2",
                            "interval-day-2",
                        ),
                    clock = FixedClock(Instant.parse("2026-07-25T05:00:00Z")),
                )
            val start = Instant.parse("2026-07-25T03:30:00Z")
            val boundary = Instant.parse("2026-07-25T04:00:00Z")
            val created =
                repository.createActiveInterval(
                    taskId = "task-day-1",
                    boundaryZoneId = TEST_ZONE,
                    start = start,
                ) as CreateActiveIntervalResult.Created

            val normalized =
                requireNotNull(
                    repository.normalizeActiveInterval(
                        expectedIntervalId = created.snapshot.interval.id,
                        boundaries =
                            listOf(
                                TimerSplitBoundary(
                                    instant = boundary,
                                    workDate = LocalDate.of(2026, 7, 25),
                                    zoneId = TEST_ZONE,
                                ),
                            ),
                    ),
                )
            val repeated =
                requireNotNull(
                    repository.normalizeActiveInterval(
                        expectedIntervalId = normalized.interval.id,
                        boundaries = emptyList(),
                    ),
                )

            val sourceInterval =
                requireNotNull(database.workIntervalDao().readInterval("interval-day-1"))
            val continuationTask =
                requireNotNull(database.taskDao().readTask("task-day-2"))
            assertEquals(boundary.toEpochMilli(), sourceInterval.stopEpochMs)
            assertNull(sourceInterval.activeSlot)
            assertEquals("series-1", continuationTask.seriesId)
            assertEquals("client-1", continuationTask.clientId)
            assertEquals("Task task-day-1", continuationTask.description)
            assertEquals("Laptop", continuationTask.hardwareSoftwarePurchases)
            assertEquals("employee-1", continuationTask.employeeId)
            assertEquals("Alex Rivera", continuationTask.employeeNameSnapshot)
            assertEquals("ON_SITE", continuationTask.workType)
            assertEquals("18.5", continuationTask.mileage)
            assertEquals(LocalDate.of(2026, 7, 25).toEpochDay(), continuationTask.workDateEpochDay)
            assertEquals(TEST_ZONE.id, continuationTask.zoneId)
            assertEquals("interval-day-2", normalized.interval.id)
            assertEquals("task-day-2", normalized.interval.taskId)
            assertNull(normalized.interval.stop)
            assertEquals(normalized, repeated)
            assertEquals(1, database.workIntervalDao().countIntervalsForTask("task-day-1"))
            assertEquals(1, database.workIntervalDao().countIntervalsForTask("task-day-2"))
        }

    @Test
    fun orphanOpenIntervalIsReportedInsteadOfBeingTreatedAsStopped() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")
            database.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO work_intervals (
                    id,
                    task_id,
                    ordinal,
                    start_epoch_ms,
                    stop_epoch_ms,
                    active_slot,
                    was_manually_edited,
                    created_at_epoch_ms,
                    updated_at_epoch_ms
                ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "orphan-open",
                    "task-1",
                    1,
                    TEST_NOW.toEpochMilli(),
                    WorkIntervalEntity.ACTIVE_SLOT,
                    0,
                    TEST_NOW.toEpochMilli(),
                    TEST_NOW.toEpochMilli(),
                ),
            )

            try {
                database.activeTimerDao().readActiveTimerSnapshot()
            } catch (error: PersistenceInvariantException) {
                assertTrue(error.message.orEmpty().contains("without active timer"))
                return@runBlocking
            }
            throw AssertionError("Expected orphan open interval to be reported")
        }

    @Test
    fun failedMultiBoundaryNormalizationRollsBackTheWholeChain() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(
                id = "task-day-1",
                clientId = "client-1",
                workDateEpochDay = LocalDate.of(2026, 7, 24).toEpochDay(),
            )
            val repository =
                RoomActiveTimerRepository(
                    activeTimerDao = database.activeTimerDao(),
                    idGenerator =
                        QueueIdGenerator(
                            "interval-day-1",
                            "task-day-2",
                            "interval-day-2",
                            "task-day-3",
                            "interval-day-3",
                        ),
                    clock = FixedClock(Instant.parse("2026-07-25T05:00:00Z")),
                )
            repository.createActiveInterval(
                taskId = "task-day-1",
                boundaryZoneId = TEST_ZONE,
                start = Instant.parse("2026-07-25T03:30:00Z"),
            )
            val duplicatedBoundary = Instant.parse("2026-07-25T04:00:00Z")

            try {
                repository.normalizeActiveInterval(
                    expectedIntervalId = "interval-day-1",
                    boundaries =
                        listOf(
                            TimerSplitBoundary(
                                instant = duplicatedBoundary,
                                workDate = LocalDate.of(2026, 7, 25),
                                zoneId = TEST_ZONE,
                            ),
                            TimerSplitBoundary(
                                instant = duplicatedBoundary,
                                workDate = LocalDate.of(2026, 7, 26),
                                zoneId = TEST_ZONE,
                            ),
                        ),
                )
            } catch (_: IllegalArgumentException) {
                val active =
                    requireNotNull(
                        database.activeTimerDao().readActiveTimerSnapshot(),
                    )
                assertEquals("interval-day-1", active.interval.id)
                assertNull(active.interval.stopEpochMs)
                assertEquals(
                    WorkIntervalEntity.ACTIVE_SLOT,
                    active.interval.activeSlot,
                )
                assertNull(database.taskDao().readTask("task-day-2"))
                assertNull(database.workIntervalDao().readInterval("interval-day-2"))
                return@runBlocking
            }
            throw AssertionError("Expected invalid continuation chain to roll back")
        }

    @Test
    fun stoppingAcrossMultipleMidnightsSplitsAndClearsAtomically() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(
                id = "task-day-1",
                clientId = "client-1",
                workDateEpochDay = LocalDate.of(2026, 7, 24).toEpochDay(),
            )
            val stop = Instant.parse("2026-07-26T05:00:00Z")
            val repository =
                RoomActiveTimerRepository(
                    activeTimerDao = database.activeTimerDao(),
                    idGenerator =
                        QueueIdGenerator(
                            "interval-day-1",
                            "task-day-2",
                            "interval-day-2",
                            "task-day-3",
                            "interval-day-3",
                        ),
                    clock = FixedClock(stop),
                )
            val created =
                repository.createActiveInterval(
                    taskId = "task-day-1",
                    boundaryZoneId = TEST_ZONE,
                    start = Instant.parse("2026-07-25T03:30:00Z"),
                ) as CreateActiveIntervalResult.Created

            val closed =
                requireNotNull(
                    repository.closeActiveInterval(
                        expectedIntervalId = created.snapshot.interval.id,
                        boundaries =
                            listOf(
                                TimerSplitBoundary(
                                    instant = Instant.parse("2026-07-25T04:00:00Z"),
                                    workDate = LocalDate.of(2026, 7, 25),
                                    zoneId = TEST_ZONE,
                                ),
                                TimerSplitBoundary(
                                    instant = Instant.parse("2026-07-26T04:00:00Z"),
                                    workDate = LocalDate.of(2026, 7, 26),
                                    zoneId = TEST_ZONE,
                                ),
                            ),
                        stop = stop,
                    ),
                )

            assertEquals("interval-day-3", closed.interval.id)
            assertEquals(stop, closed.interval.stop)
            assertNull(database.activeTimerDao().readActiveTimer())
            assertEquals(
                Instant.parse("2026-07-25T04:00:00Z").toEpochMilli(),
                database.workIntervalDao().readInterval("interval-day-1")?.stopEpochMs,
            )
            assertEquals(
                Instant.parse("2026-07-26T04:00:00Z").toEpochMilli(),
                database.workIntervalDao().readInterval("interval-day-2")?.stopEpochMs,
            )
            assertEquals(
                stop.toEpochMilli(),
                database.workIntervalDao().readInterval("interval-day-3")?.stopEpochMs,
            )
        }

    @Test
    fun concurrentRoomRepositoryStartsHaveOneWinner() =
        runBlocking {
            insertClient(id = "client-1")
            insertTask(id = "task-1", clientId = "client-1")
            val repository =
                RoomActiveTimerRepository(
                    activeTimerDao = database.activeTimerDao(),
                    idGenerator = AtomicIdGenerator(),
                    clock = FixedClock(TEST_NOW),
                )

            val results =
                coroutineScope {
                    List(2) {
                        async(Dispatchers.IO) {
                            repository.createActiveInterval(
                                taskId = "task-1",
                                boundaryZoneId = TEST_ZONE,
                                start = TEST_NOW,
                            )
                        }
                    }.awaitAll()
                }

            assertEquals(1, results.count { it is CreateActiveIntervalResult.Created })
            assertEquals(1, results.count { it is CreateActiveIntervalResult.AlreadyActive })
            assertEquals(1, database.workIntervalDao().countIntervalsForTask("task-1"))
        }

    @Test
    fun dataPersistsAfterFileDatabaseIsClosedAndReopened() =
        runBlocking {
            database.close()
            context.deleteDatabase(REOPEN_TEST_DATABASE)
            var fileDatabase =
                Room
                    .databaseBuilder(
                        context,
                        WorqOrderDatabase::class.java,
                        REOPEN_TEST_DATABASE,
                    ).allowMainThreadQueries()
                    .build()
            try {
                fileDatabase.clientDao().addClient(
                    clientEntity(
                        id = "persistent-client",
                        name = "Persistent Client",
                        canonicalName = "persistent client",
                    ),
                )
            } finally {
                fileDatabase.close()
            }

            fileDatabase =
                Room
                    .databaseBuilder(
                        context,
                        WorqOrderDatabase::class.java,
                        REOPEN_TEST_DATABASE,
                    ).allowMainThreadQueries()
                    .build()
            try {
                assertEquals(
                    "Persistent Client",
                    fileDatabase.clientDao().readClient("persistent-client")?.name,
                )
            } finally {
                fileDatabase.close()
                context.deleteDatabase(REOPEN_TEST_DATABASE)
            }
        }

    @Test
    fun exportedSchemasArePackagedForVerification() {
        val testContext = InstrumentationRegistry.getInstrumentation().context

        val versionOne =
            testContext.assets
                .open(VERSION_ONE_SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val versionTwo =
            testContext.assets
                .open(VERSION_TWO_SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }
        val versionThree =
            testContext.assets
                .open(VERSION_THREE_SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }

        assertTrue(versionOne.contains("\"version\": 1"))
        assertTrue(versionTwo.contains("\"version\": 2"))
        assertTrue(versionTwo.contains("\"tableName\": \"clients\""))
        assertTrue(versionTwo.contains("\"tableName\": \"active_timer\""))
        assertTrue(versionTwo.contains("\"columnName\": \"hardware_software_purchases\""))
        assertTrue(versionThree.contains("\"version\": 3"))
        assertTrue(versionThree.contains("\"tableName\": \"employees\""))
        assertTrue(versionThree.contains("\"columnName\": \"employee_name_snapshot\""))
    }

    @Test
    fun migrationOneToThreePreservesPopulatedTaskAndActiveTimer() =
        runBlocking {
            context.deleteDatabase(MIGRATION_TEST_DATABASE)
            createPopulatedVersionOneDatabase()

            val migrated =
                Room
                    .databaseBuilder(
                        context,
                        WorqOrderDatabase::class.java,
                        MIGRATION_TEST_DATABASE,
                    ).addMigrations(
                        WorqOrderMigrations.MIGRATION_1_2,
                        WorqOrderMigrations.MIGRATION_2_3,
                    )
                    .allowMainThreadQueries()
                    .build()
            try {
                val task = requireNotNull(migrated.taskDao().readTask("migration-task"))
                assertEquals("Existing description", task.description)
                assertEquals("", task.hardwareSoftwarePurchases)
                assertNull(task.employeeId)
                assertEquals("", task.employeeNameSnapshot)
                assertEquals("UNSPECIFIED", task.workType)
                assertNull(task.mileage)
                val intervals =
                    migrated.workIntervalDao()
                        .readIntervalsForOverlapValidation("migration-task")
                assertEquals(2, intervals.size)
                assertEquals(
                    "migration-active",
                    migrated.activeTimerDao().readActiveTimer()?.intervalId,
                )
            } finally {
                migrated.close()
                context.deleteDatabase(MIGRATION_TEST_DATABASE)
            }
        }

    @Test
    fun migrationTwoToThreePreservesReleasedGraphAndAddsSafeDefaults() =
        runBlocking {
            context.deleteDatabase(MIGRATION_TEST_DATABASE)
            createPopulatedVersionTwoDatabase()

            val migrated =
                Room
                    .databaseBuilder(
                        context,
                        WorqOrderDatabase::class.java,
                        MIGRATION_TEST_DATABASE,
                    ).addMigrations(WorqOrderMigrations.MIGRATION_2_3)
                    .allowMainThreadQueries()
                    .build()
            try {
                val task = requireNotNull(migrated.taskDao().readTask("migration-task"))
                assertEquals("Existing description", task.description)
                assertEquals("", task.hardwareSoftwarePurchases)
                assertNull(task.employeeId)
                assertEquals("", task.employeeNameSnapshot)
                assertEquals("UNSPECIFIED", task.workType)
                assertNull(task.mileage)
                assertEquals(
                    listOf("migration-complete", "migration-active"),
                    migrated.workIntervalDao()
                        .readIntervalsForOverlapValidation("migration-task")
                        .map(WorkIntervalEntity::id),
                )
                assertEquals(
                    "migration-active",
                    migrated.activeTimerDao().readActiveTimer()?.intervalId,
                )
                assertTrue(migrated.employeeDao().observeAllEmployees().first().isEmpty())
            } finally {
                migrated.close()
                context.deleteDatabase(MIGRATION_TEST_DATABASE)
            }
        }

    private fun createPopulatedVersionTwoDatabase() {
        createPopulatedVersionOneDatabase()
        context.openOrCreateDatabase(
            MIGRATION_TEST_DATABASE,
            Context.MODE_PRIVATE,
            null,
        ).use { database ->
            database.execSQL(
                "ALTER TABLE daily_tasks " +
                    "ADD COLUMN hardware_software_purchases TEXT NOT NULL DEFAULT ''",
            )
            database.version = 2
        }
    }

    private fun createPopulatedVersionOneDatabase() {
        val sqlite =
            context.openOrCreateDatabase(
                MIGRATION_TEST_DATABASE,
                Context.MODE_PRIVATE,
                null,
            )
        sqlite.use { database ->
            database.execSQL("PRAGMA foreign_keys = ON")
            database.execSQL(
                "CREATE TABLE clients (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, canonical_name TEXT NOT NULL, active_name_key TEXT, is_active INTEGER NOT NULL, created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, archived_at_epoch_ms INTEGER)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX index_clients_active_name_key ON clients (active_name_key)",
            )
            database.execSQL(
                "CREATE INDEX index_clients_active_sort ON clients (is_active, name, id)",
            )
            database.execSQL(
                "CREATE TABLE daily_tasks (id TEXT NOT NULL PRIMARY KEY, series_id TEXT NOT NULL, client_id TEXT NOT NULL, description TEXT NOT NULL, work_date_epoch_day INTEGER NOT NULL, zone_id TEXT NOT NULL, created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, FOREIGN KEY(client_id) REFERENCES clients(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX index_daily_tasks_series_date_zone ON daily_tasks (series_id, work_date_epoch_day, zone_id)",
            )
            database.execSQL(
                "CREATE INDEX index_daily_tasks_work_date_sort ON daily_tasks (work_date_epoch_day, created_at_epoch_ms, id)",
            )
            database.execSQL(
                "CREATE INDEX index_daily_tasks_client_id ON daily_tasks (client_id)",
            )
            database.execSQL(
                "CREATE TABLE work_intervals (id TEXT NOT NULL PRIMARY KEY, task_id TEXT NOT NULL, ordinal INTEGER NOT NULL, start_epoch_ms INTEGER NOT NULL, stop_epoch_ms INTEGER, active_slot INTEGER, was_manually_edited INTEGER NOT NULL, created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, FOREIGN KEY(task_id) REFERENCES daily_tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX index_work_intervals_task_ordinal ON work_intervals (task_id, ordinal)",
            )
            database.execSQL(
                "CREATE INDEX index_work_intervals_task_start ON work_intervals (task_id, start_epoch_ms, ordinal, id)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX index_work_intervals_active_slot ON work_intervals (active_slot)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX index_work_intervals_id_task ON work_intervals (id, task_id)",
            )
            database.execSQL(
                "CREATE TABLE active_timer (singleton_id INTEGER NOT NULL PRIMARY KEY, interval_id TEXT NOT NULL, task_id TEXT NOT NULL, boundary_zone_id TEXT NOT NULL, created_at_epoch_ms INTEGER NOT NULL, updated_at_epoch_ms INTEGER NOT NULL, FOREIGN KEY(interval_id, task_id) REFERENCES work_intervals(id, task_id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
            )
            database.execSQL(
                "CREATE UNIQUE INDEX index_active_timer_interval_task ON active_timer (interval_id, task_id)",
            )
            database.execSQL(
                "CREATE INDEX index_active_timer_task_id ON active_timer (task_id)",
            )
            database.execSQL(
                "CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            database.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, '7cea78f332f52d42b7bd750092d38e4a')",
            )
            database.execSQL(
                "INSERT INTO clients VALUES ('migration-client', 'Client', 'client', 'client', 1, 1000, 1000, NULL)",
            )
            database.execSQL(
                "INSERT INTO daily_tasks VALUES ('migration-task', 'migration-series', 'migration-client', 'Existing description', ${TEST_DATE.toEpochDay()}, '${TEST_ZONE.id}', 1000, 1000)",
            )
            database.execSQL(
                "INSERT INTO work_intervals VALUES ('migration-complete', 'migration-task', 1, 1000, 2000, NULL, 0, 1000, 2000)",
            )
            database.execSQL(
                "INSERT INTO work_intervals VALUES ('migration-active', 'migration-task', 2, 3000, NULL, 1, 0, 3000, 3000)",
            )
            database.execSQL(
                "INSERT INTO active_timer VALUES (1, 'migration-active', 'migration-task', '${TEST_ZONE.id}', 3000, 3000)",
            )
            database.version = 1
        }
    }

    private suspend fun insertClient(
        id: String,
        name: String = "Client",
        canonicalName: String = name.lowercase(),
    ) {
        database.clientDao().addClient(
            clientEntity(
                id = id,
                name = name,
                canonicalName = canonicalName,
            ),
        )
    }

    private suspend fun insertTask(
        id: String,
        clientId: String,
        seriesId: String = "series-1",
        hardwareSoftwarePurchases: String = "",
        employeeId: String? = null,
        employeeNameSnapshot: String = "",
        workType: String = "UNSPECIFIED",
        mileage: String? = null,
        workDateEpochDay: Long = TEST_DATE.toEpochDay(),
        zoneId: String = TEST_ZONE.id,
    ) {
        database.taskDao().insertDailyTask(
            DailyTaskEntity(
                id = id,
                seriesId = seriesId,
                clientId = clientId,
                description = "Task $id",
                hardwareSoftwarePurchases = hardwareSoftwarePurchases,
                employeeId = employeeId,
                employeeNameSnapshot = employeeNameSnapshot,
                workType = workType,
                mileage = mileage,
                workDateEpochDay = workDateEpochDay,
                zoneId = zoneId,
                createdAtEpochMs = 1_000,
                updatedAtEpochMs = 1_000,
            ),
        )
    }

    private suspend fun insertEmployee(
        id: String,
        name: String,
    ) {
        database.employeeDao().insert(
            EmployeeEntity(
                id = id,
                name = name,
                canonicalName = name.lowercase(),
                activeNameKey = name.lowercase(),
                isActive = true,
                createdAtEpochMs = 1_000,
                updatedAtEpochMs = 1_000,
                archivedAtEpochMs = null,
            ),
        )
    }

    private suspend fun insertCompletedInterval(
        id: String,
        taskId: String,
        startEpochMs: Long,
        stopEpochMs: Long,
    ): WorkIntervalEntity =
        database.workIntervalDao().insertInterval(
            intervalId = id,
            taskId = taskId,
            startEpochMs = startEpochMs,
            stopEpochMs = stopEpochMs,
            wasManuallyEdited = false,
            createdAtEpochMs = stopEpochMs,
            updatedAtEpochMs = stopEpochMs,
        )

    private fun clientEntity(
        id: String,
        name: String,
        canonicalName: String,
    ) = ClientEntity(
        id = id,
        name = name,
        canonicalName = canonicalName,
        activeNameKey = canonicalName,
        isActive = true,
        createdAtEpochMs = 1_000,
        updatedAtEpochMs = 1_000,
        archivedAtEpochMs = null,
    )

    private suspend fun expectConstraintFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: SQLiteConstraintException) {
            return
        }
        throw AssertionError("Expected SQLiteConstraintException")
    }

    private class QueueIdGenerator(
        vararg ids: String,
    ) : EntityIdGenerator {
        private val remainingIds = ArrayDeque(ids.toList())

        override fun newId(): String = remainingIds.removeFirst()
    }

    private class AtomicIdGenerator : EntityIdGenerator {
        private val next = AtomicInteger()

        override fun newId(): String = "generated-${next.incrementAndGet()}"
    }

    private class FixedClock(
        private val instant: Instant,
    ) : UtcClock {
        override fun now(): Instant = instant
    }

    private companion object {
        val EXPECTED_TABLES =
            setOf(
                "clients",
                "employees",
                "daily_tasks",
                "work_intervals",
                "active_timer",
                "room_master_table",
            )
        val TEST_NOW: Instant = Instant.parse("2026-07-23T12:00:00Z")
        val TEST_DATE: LocalDate = LocalDate.of(2026, 7, 23)
        val TEST_ZONE: ZoneId = ZoneId.of("America/New_York")
        const val REOPEN_TEST_DATABASE = "worqorder-milestone2-reopen-test.db"
        const val MIGRATION_TEST_DATABASE = "worqorder-migration-1-2-test.db"
        const val VERSION_ONE_SCHEMA_ASSET_PATH =
            "worq.order.data.local.WorqOrderDatabase/1.json"
        const val VERSION_TWO_SCHEMA_ASSET_PATH =
            "worq.order.data.local.WorqOrderDatabase/2.json"
        const val VERSION_THREE_SCHEMA_ASSET_PATH =
            "worq.order.data.local.WorqOrderDatabase/3.json"
    }
}
