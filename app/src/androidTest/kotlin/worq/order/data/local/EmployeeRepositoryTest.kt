package worq.order.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.EmployeeMutationResult
import worq.order.data.EntityIdGenerator
import worq.order.data.CreateDailyTaskResult
import worq.order.data.NewDailyTask
import worq.order.timer.UtcClock

@RunWith(AndroidJUnit4::class)
class EmployeeRepositoryTest {
    private lateinit var database: WorqOrderDatabase
    private lateinit var repository: RoomEmployeeRepository

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database =
            Room.inMemoryDatabaseBuilder(context, WorqOrderDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            RoomEmployeeRepository(
                dao = database.employeeDao(),
                idGenerator = QueueIdGenerator("employee-z", "employee-a", "employee-duplicate"),
                clock = FixedClock(Instant.parse("2026-08-02T12:00:00Z")),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun employeeDirectoryNormalizesSortsArchivesAndRestoresWithoutDuplication() =
        runBlocking {
            assertTrue(repository.addEmployee("  Zulu ") is EmployeeMutationResult.Success)
            assertTrue(repository.addEmployee("Alpha") is EmployeeMutationResult.Success)
            assertEquals(
                listOf("Alpha", "Zulu"),
                repository.observeActiveEmployees().first().map { it.name },
            )
            assertEquals(
                EmployeeMutationResult.DuplicateActiveName("employee-z"),
                repository.addEmployee(" ZULU "),
            )

            val archived = repository.archiveEmployee("employee-z") as EmployeeMutationResult.Success
            assertFalse(archived.employee.isActive)
            assertEquals(listOf("Alpha"), repository.observeActiveEmployees().first().map { it.name })
            assertEquals(
                EmployeeMutationResult.MatchingArchivedEmployee(archived.employee),
                repository.addEmployee(" zulu "),
            )
            assertTrue(repository.restoreEmployee("employee-z") is EmployeeMutationResult.Success)
            assertEquals(2, repository.observeAllEmployees().first().size)
        }

    @Test
    fun renameAndRestoreRespectTheActiveCanonicalNameConstraint() =
        runBlocking {
            repository.addEmployee("Zulu")
            repository.addEmployee("Alpha")

            val renamed =
                repository.renameEmployee("employee-z", "  Project   Lead  ") as
                    EmployeeMutationResult.Success
            assertEquals("Project Lead", renamed.employee.name)
            assertEquals(
                EmployeeMutationResult.DuplicateActiveName("employee-z"),
                repository.renameEmployee("employee-a", "project lead"),
            )

            repository.archiveEmployee("employee-z")
            assertTrue(
                repository.renameEmployee("employee-a", "Project Lead") is
                    EmployeeMutationResult.Success,
            )
            assertEquals(
                EmployeeMutationResult.DuplicateActiveName("employee-a"),
                repository.restoreEmployee("employee-z"),
            )
        }

    @Test
    fun taskCreationCapturesConsultantNameAndArchiveDoesNotRewriteHistory() =
        runBlocking {
            database.clientDao().addClient(
                ClientEntity(
                    id = "client",
                    name = "Client",
                    canonicalName = "client",
                    activeNameKey = "client",
                    isActive = true,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    archivedAtEpochMs = null,
                ),
            )
            val added =
                repository.addEmployee("Alex Rivera") as EmployeeMutationResult.Success
            val tasks =
                RoomTaskRepository(
                    taskDao = database.taskDao(),
                    workIntervalDao = database.workIntervalDao(),
                    idGenerator =
                        QueueIdGenerator(
                            "task",
                            "series",
                            "rejected-task",
                            "rejected-series",
                        ),
                    clock = FixedClock(Instant.parse("2026-08-02T12:00:00Z")),
                )

            val created =
                tasks.createDailyTask(
                    NewDailyTask(
                        clientId = "client",
                        description = "Task",
                        employeeId = added.employee.id,
                        workDate = LocalDate.of(2026, 8, 2),
                        zoneId = ZoneId.of("America/New_York"),
                    ),
                ) as CreateDailyTaskResult.Created
            assertEquals("Alex Rivera", created.task.employeeNameSnapshot)

            repository.renameEmployee(added.employee.id, "Alex Renamed")
            repository.archiveEmployee(added.employee.id)

            val historical = requireNotNull(tasks.readTaskWithClient(created.task.id)).task
            assertEquals("Alex Rivera", historical.employeeNameSnapshot)
            assertEquals(
                CreateDailyTaskResult.EmployeeUnavailable,
                tasks.createDailyTask(
                    NewDailyTask(
                        clientId = "client",
                        description = "Later task",
                        employeeId = added.employee.id,
                        workDate = LocalDate.of(2026, 8, 3),
                        zoneId = ZoneId.of("America/New_York"),
                    ),
                ),
            )
        }

    private class QueueIdGenerator(
        vararg ids: String,
    ) : EntityIdGenerator {
        private val values = ArrayDeque(ids.toList())

        override fun newId(): String = values.removeFirst()
    }

    private class FixedClock(
        private val value: Instant,
    ) : UtcClock {
        override fun now(): Instant = value
    }
}
