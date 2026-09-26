package worq.order.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.local.WorqOrderDatabase

@RunWith(AndroidJUnit4::class)
class PortableBackupRoomReplacementTest {
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
    fun replacementWritesTheValidatedCompletedGraphWithNoActiveTimer() = runBlocking {
        val data = completePortableData()
        assertTrue(PortableBackupValidator.validateData(data) is PortableBackupDataValidationResult.Valid)

        PortableBackupRoomReplacement(database).replace(data)

        assertEquals(data.clients.map(PortableBackupClientV1::id).sorted(), database.clientDao().readAllClientsForPortableBackup().map { it.id })
        assertEquals(data.consultants.map(PortableBackupConsultantV1::id).sorted(), database.employeeDao().readAllEmployeesForPortableBackup().map { it.id })
        assertEquals(data.tags.map(PortableBackupTagV1::id).sorted(), database.tagDao().readAllTagsForPortableBackup().map { it.id })
        assertEquals(data.tasks.map(PortableBackupTaskV1::id).sorted(), database.taskDao().readAllTasksForPortableBackup().map { it.id })
        assertEquals(
            data.tasks.flatMap(PortableBackupTaskV1::intervals).map(PortableBackupIntervalV1::id).sorted(),
            database.workIntervalDao().readAllIntervalsForPortableBackup().map { it.id },
        )
        assertNull(database.activeTimerDao().readActiveTimer())
        assertTrue(
            database.workIntervalDao().readAllIntervalsForPortableBackup().all { interval ->
                interval.stopEpochMs != null && interval.activeSlot == null
            },
        )
    }

    private fun completePortableData(): PortableBackupDataV1 {
        val createdAt = 1_758_374_400_000L
        val stoppedAt = createdAt + 5_400_000L
        return PortableBackupDataV1(
            dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
            clients =
                listOf(
                    PortableBackupClientV1(
                        id = "client-1",
                        name = "Example Client",
                        canonicalName = "example client",
                        isActive = true,
                        createdAtEpochMs = createdAt,
                        updatedAtEpochMs = createdAt,
                        archivedAtEpochMs = null,
                    ),
                ),
            consultants =
                listOf(
                    PortableBackupConsultantV1(
                        id = "consultant-1",
                        name = "Alex Rivera",
                        canonicalName = "alex rivera",
                        isActive = true,
                        createdAtEpochMs = createdAt,
                        updatedAtEpochMs = createdAt,
                        archivedAtEpochMs = null,
                    ),
                ),
            tags =
                listOf(
                    PortableBackupTagV1(
                        id = "tag-1",
                        category = "DESCRIPTION",
                        text = "Inspect network",
                        normalizedText = "inspect network",
                        createdAtEpochMs = createdAt,
                        updatedAtEpochMs = createdAt,
                    ),
                ),
            tasks =
                listOf(
                    PortableBackupTaskV1(
                        id = "task-1",
                        seriesId = "series-1",
                        clientId = "client-1",
                        consultantId = "consultant-1",
                        consultantNameSnapshot = "Alex Rivera",
                        description = "Inspect network",
                        hardwareSoftwarePurchases = "",
                        workType = "ON_SITE",
                        billingStatus = "BILLABLE",
                        mileage = "12.5",
                        notes = "Completed successfully",
                        workDateEpochDay = 20_351L,
                        zoneId = "America/New_York",
                        createdAtEpochMs = createdAt,
                        updatedAtEpochMs = stoppedAt,
                        tagSnapshots =
                            listOf(
                                PortableBackupTaskTagSnapshotV1(
                                    id = "snapshot-1",
                                    category = "DESCRIPTION",
                                    text = "Inspect network",
                                    sourceTagId = "tag-1",
                                    selectionOrder = 0,
                                    createdAtEpochMs = createdAt,
                                ),
                            ),
                        intervals =
                            listOf(
                                PortableBackupIntervalV1(
                                    id = "interval-1",
                                    taskId = "task-1",
                                    startEpochMs = createdAt,
                                    stopEpochMs = stoppedAt,
                                    wasManuallyEdited = false,
                                    createdAtEpochMs = createdAt,
                                    updatedAtEpochMs = stoppedAt,
                                ),
                            ),
                    ),
                ),
            settings =
                PortableBackupSettingsV1(
                    themeMode = "SYSTEM",
                    timeZoneMode = "DEVICE",
                    manualZoneId = null,
                    defaultExportDestination = "CSV",
                    lastExportAttempt = null,
                    selectedConsultantId = "consultant-1",
                    landscapeHandedness = "RIGHT_HANDED",
                ),
            selection =
                PortableBackupSelectionV1(
                    taskId = "task-1",
                    seriesId = "series-1",
                    selectedOnEpochDay = 20_351L,
                    selectedInZoneId = "America/New_York",
                ),
        )
    }
}
