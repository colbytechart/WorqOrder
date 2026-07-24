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
    fun activeTimerNormalizationCreatesDailyContinuationAndIsIdempotent() =
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
    fun exportedVersionOneSchemaIsPackagedForVerification() {
        val testContext = InstrumentationRegistry.getInstrumentation().context

        val schema =
            testContext.assets
                .open(SCHEMA_ASSET_PATH)
                .bufferedReader()
                .use { it.readText() }

        assertTrue(schema.contains("\"version\": 1"))
        assertTrue(schema.contains("\"tableName\": \"clients\""))
        assertTrue(schema.contains("\"tableName\": \"active_timer\""))
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
        workDateEpochDay: Long = TEST_DATE.toEpochDay(),
        zoneId: String = TEST_ZONE.id,
    ) {
        database.taskDao().insertDailyTask(
            DailyTaskEntity(
                id = id,
                seriesId = seriesId,
                clientId = clientId,
                description = "Task $id",
                workDateEpochDay = workDateEpochDay,
                zoneId = zoneId,
                createdAtEpochMs = 1_000,
                updatedAtEpochMs = 1_000,
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
                "daily_tasks",
                "work_intervals",
                "active_timer",
                "room_master_table",
            )
        val TEST_NOW: Instant = Instant.parse("2026-07-23T12:00:00Z")
        val TEST_DATE: LocalDate = LocalDate.of(2026, 7, 23)
        val TEST_ZONE: ZoneId = ZoneId.of("America/New_York")
        const val REOPEN_TEST_DATABASE = "worqorder-milestone2-reopen-test.db"
        const val SCHEMA_ASSET_PATH =
            "worq.order.data.local.WorqOrderDatabase/1.json"
    }
}
