package worq.order.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.AppSettings
import worq.order.data.ExportAttemptOutcome
import worq.order.data.ExportDestination
import worq.order.data.LandscapeHandedness
import worq.order.data.LastExportAttempt
import worq.order.data.SelectedTaskState
import worq.order.data.ThemeMode
import worq.order.data.TimeZoneMode
import worq.order.data.local.ClientEntity
import worq.order.data.local.DailyTaskEntity
import worq.order.data.local.EmployeeEntity
import worq.order.data.local.TagEntity
import worq.order.data.local.TaskTagSnapshotEntity
import worq.order.data.local.WorqOrderDatabase

@RunWith(AndroidJUnit4::class)
class RoomPortableBackupSnapshotReaderTest {
    private lateinit var database: WorqOrderDatabase

    @Before
    fun createDatabase() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
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
    fun logicalSnapshotIncludesPortableStateAndExcludesRuntimeGoogleState() = runBlocking {
        seedCompletedTask()
        val workDate = LocalDate.of(2026, 9, 20)
        val settings =
            AppSettings(
                themeMode = ThemeMode.DARK,
                timeZoneMode = TimeZoneMode.MANUAL,
                manualZoneId = ZoneId.of("America/New_York"),
                defaultExportDestination = ExportDestination.GOOGLE_SHEETS,
                lastExportAttempt =
                    LastExportAttempt(
                        destination = ExportDestination.GOOGLE_SHEETS,
                        workDate = workDate,
                        attemptedAt = Instant.ofEpochMilli(1_758_368_900_000),
                        outcome = ExportAttemptOutcome.SUCCESS,
                    ),
                selectedEmployeeId = CONSULTANT_ID,
                landscapeHandedness = LandscapeHandedness.LEFT_HANDED,
                automaticGoogleExportEnabled = true,
                automaticGoogleTargetDate = workDate,
                automaticGoogleTargetZoneId = ZoneId.of("America/New_York"),
                automaticGoogleTargetConnectionKey = "must-not-leave-device",
            )
        val selection =
            SelectedTaskState(
                taskId = TASK_ID,
                seriesId = SERIES_ID,
                selectedOnDate = workDate,
                selectedInZone = ZoneId.of("America/New_York"),
            )
        val reader =
            RoomPortableBackupSnapshotReader(
                database = database,
                readSettings = { settings },
                readSelection = { selection },
            )

        val result = reader.read()

        assertTrue(result is PortableBackupSnapshotReadResult.Ready)
        val data = (result as PortableBackupSnapshotReadResult.Ready).data
        assertEquals(listOf(CLIENT_ID), data.clients.map(PortableBackupClientV1::id))
        assertEquals(listOf(CONSULTANT_ID), data.consultants.map(PortableBackupConsultantV1::id))
        assertEquals(listOf(TAG_ID), data.tags.map(PortableBackupTagV1::id))
        assertEquals(listOf(TASK_ID), data.tasks.map(PortableBackupTaskV1::id))
        assertEquals(
            listOf(SNAPSHOT_ID),
            data.tasks.single().tagSnapshots.map(PortableBackupTaskTagSnapshotV1::id),
        )
        assertEquals(
            listOf(INTERVAL_ID),
            data.tasks.single().intervals.map(PortableBackupIntervalV1::id),
        )
        assertEquals(CONSULTANT_ID, data.settings.selectedConsultantId)
        assertEquals(ExportDestination.GOOGLE_SHEETS.name, data.settings.defaultExportDestination)
        assertEquals(TASK_ID, data.selection?.taskId)

        val encoded = PortableBackupJsonCodec.encodeData(data)
        assertFalse(encoded.contains("must-not-leave-device"))
        assertFalse(encoded.contains("automaticGoogle"))
        assertFalse(encoded.contains("spreadsheet", ignoreCase = true))
        assertFalse(encoded.contains("token", ignoreCase = true))
    }

    @Test
    fun activeTimerBlocksSnapshotBeforeAnyPortableDataIsReturned() = runBlocking {
        seedCompletedTask()
        database.taskDao().insertDailyTask(runningTask())
        database.activeTimerDao().createActiveIntervalAndTimer(
            intervalId = "interval-running",
            taskId = "task-running",
            boundaryZoneId = "UTC",
            startEpochMs = 1_758_369_000_000,
            createdAtEpochMs = 1_758_369_000_000,
        )
        val reader =
            RoomPortableBackupSnapshotReader(
                database = database,
                readSettings = { AppSettings() },
                readSelection = { null },
            )

        assertEquals(PortableBackupSnapshotReadResult.TimerRunning, reader.read())
    }

    private suspend fun seedCompletedTask() {
        database.clientDao().addClient(
            ClientEntity(
                id = CLIENT_ID,
                name = "Acme",
                canonicalName = "acme",
                activeNameKey = "acme",
                isActive = true,
                createdAtEpochMs = CREATED_AT,
                updatedAtEpochMs = CREATED_AT,
                archivedAtEpochMs = null,
            ),
        )
        database.employeeDao().insert(
            EmployeeEntity(
                id = CONSULTANT_ID,
                name = "Alex Rivera",
                canonicalName = "alex rivera",
                activeNameKey = "alex rivera",
                isActive = true,
                createdAtEpochMs = CREATED_AT,
                updatedAtEpochMs = CREATED_AT,
                archivedAtEpochMs = null,
            ),
        )
        database.tagDao().insertTag(
            TagEntity(
                id = TAG_ID,
                category = "DESCRIPTION",
                text = "Installed updates",
                normalizedText = "installed updates",
                createdAtEpochMs = CREATED_AT,
                updatedAtEpochMs = CREATED_AT,
            ),
        )
        database.taskDao().insertDailyTaskWithSnapshots(
            task = completedTask(),
            snapshots =
                listOf(
                    TaskTagSnapshotEntity(
                        id = SNAPSHOT_ID,
                        taskId = TASK_ID,
                        category = "DESCRIPTION",
                        textSnapshot = "Installed updates",
                        sourceTagId = TAG_ID,
                        selectionOrder = 0,
                        createdAtEpochMs = CREATED_AT,
                    ),
                ),
        )
        database.workIntervalDao().insertInterval(
            intervalId = INTERVAL_ID,
            taskId = TASK_ID,
            startEpochMs = CREATED_AT,
            stopEpochMs = CREATED_AT + 60_000,
            wasManuallyEdited = false,
            createdAtEpochMs = CREATED_AT,
            updatedAtEpochMs = CREATED_AT + 60_000,
        )
    }

    private fun completedTask(): DailyTaskEntity =
        DailyTaskEntity(
            id = TASK_ID,
            seriesId = SERIES_ID,
            clientId = CLIENT_ID,
            description = "Installed updates",
            hardwareSoftwarePurchases = "Cable",
            employeeId = CONSULTANT_ID,
            employeeNameSnapshot = "Alex Rivera",
            workType = "ON_SITE",
            billingStatus = "BILLABLE",
            mileage = "12.5",
            notes = "Call before arrival",
            workDateEpochDay = LocalDate.of(2026, 9, 20).toEpochDay(),
            zoneId = "America/New_York",
            createdAtEpochMs = CREATED_AT,
            updatedAtEpochMs = CREATED_AT + 60_000,
        )

    private fun runningTask(): DailyTaskEntity =
        completedTask().copy(
            id = "task-running",
            seriesId = "series-running",
            createdAtEpochMs = CREATED_AT + 120_000,
            updatedAtEpochMs = CREATED_AT + 120_000,
        )

    private companion object {
        const val CLIENT_ID = "client-1"
        const val CONSULTANT_ID = "consultant-1"
        const val TAG_ID = "tag-1"
        const val TASK_ID = "task-1"
        const val SERIES_ID = "series-1"
        const val SNAPSHOT_ID = "snapshot-1"
        const val INTERVAL_ID = "interval-1"
        const val CREATED_AT = 1_758_368_400_000L
    }
}
