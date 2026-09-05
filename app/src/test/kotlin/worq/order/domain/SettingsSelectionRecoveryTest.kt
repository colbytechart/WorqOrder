package worq.order.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.data.SelectedTaskState
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.timer.CurrentDateProvider
import worq.order.timer.EffectiveZoneIdProvider

class SettingsSelectionRecoveryTest {
    @Test
    fun restartWaitsForPersistedZoneThenClearsWithoutCreatingTask() =
        runTest {
            val tasks = FakeTaskRepository()
            val source =
                tasks.insertDailyTask(
                    NewDailyTask(
                        clientId = "client",
                        description = "Selected task",
                        workDate = LocalDate.of(2026, 7, 23),
                        zoneId = NEW_YORK,
                        seriesId = "series",
                    ),
                )
            val selection =
                FakeSelectedTaskRepository(
                    SelectedTaskState(
                        taskId = source.id,
                        seriesId = source.seriesId,
                        selectedOnDate = source.workDate,
                        selectedInZone = source.zoneId,
                    ),
                )
            val active = FakeActiveTimerRepository(tasks)
            val zone = DelayedPersistedZoneProvider(NEW_YORK, TOKYO)
            val date =
                CurrentDateProvider(
                    clock = FakeUtcClock(Instant.parse("2026-07-25T02:00:00Z")),
                    zoneIdProvider = zone,
                )

            val first =
                SelectionCoordinator(
                    selectedTaskRepository = selection,
                    taskRepository = tasks,
                    activeTimerRepository = active,
                    currentDateProvider = date,
                    zoneIdProvider = zone,
                )
            assertEquals(
                SelectionReconciliationResult.IneligibleSelectionCleared,
                first.reconcileForToday(),
            )
            assertNull(selection.readSelection())
            assertEquals(
                0,
                tasks.observeTasksForDate(LocalDate.of(2026, 7, 25)).first().size,
            )

            val recreated =
                SelectionCoordinator(
                    selectedTaskRepository = selection,
                    taskRepository = tasks,
                    activeTimerRepository = active,
                    currentDateProvider = date,
                    zoneIdProvider = zone,
                )
            assertEquals(
                SelectionReconciliationResult.NoSelection,
                recreated.reconcileForToday(),
            )
            assertEquals(
                0,
                tasks.observeTasksForDate(LocalDate.of(2026, 7, 25)).first().size,
            )
        }

    private class DelayedPersistedZoneProvider(
        initialDeviceZone: ZoneId,
        private val persistedZone: ZoneId,
    ) : EffectiveZoneIdProvider {
        private val state = MutableStateFlow(initialDeviceZone)

        override fun zoneId(): ZoneId = state.value

        override fun observeZoneId(): Flow<ZoneId> = state

        override suspend fun awaitZoneId(): ZoneId {
            state.value = persistedZone
            return persistedZone
        }
    }

    private companion object {
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
    }
}
