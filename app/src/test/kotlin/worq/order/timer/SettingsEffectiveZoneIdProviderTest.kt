package worq.order.timer

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.data.TimeZoneMode
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsEffectiveZoneIdProviderTest {
    @Test
    fun deviceModeTracksDeviceAndManualModePreservesCanonicalZone() =
        runTest {
            val settings = FakeSettingsRepository()
            val device = FakeDeviceZoneIdSource(NEW_YORK)
            val provider =
                SettingsEffectiveZoneIdProvider(
                    settingsRepository = settings,
                    deviceZoneIdSource = device,
                    activeTimerRepository =
                        FakeActiveTimerRepository(FakeTaskRepository()),
                    applicationScope = backgroundScope,
                )
            runCurrent()
            assertEquals(NEW_YORK, provider.awaitZoneId())

            device.current = TOKYO
            runCurrent()
            assertEquals(TOKYO, provider.zoneId())

            settings.setManualZoneId(LOS_ANGELES)
            runCurrent()
            assertEquals(LOS_ANGELES, provider.zoneId())

            device.current = NEW_YORK
            runCurrent()
            assertEquals(LOS_ANGELES, provider.zoneId())

            settings.setTimeZoneMode(TimeZoneMode.DEVICE)
            runCurrent()
            assertEquals(NEW_YORK, provider.zoneId())
        }

    @Test
    fun activeTimerPinsBoundaryZoneUntilItStops() =
        runTest {
            val tasks = FakeTaskRepository()
            val task =
                tasks.insertDailyTask(
                    NewDailyTask(
                        clientId = "client",
                        description = "Task",
                        workDate =
                            Instant.parse("2026-07-24T13:00:00Z")
                                .atZone(NEW_YORK)
                                .toLocalDate(),
                        zoneId = NEW_YORK,
                    ),
                )
            val activeTimers = FakeActiveTimerRepository(tasks)
            val settings = FakeSettingsRepository()
            val device = FakeDeviceZoneIdSource(NEW_YORK)
            val provider =
                SettingsEffectiveZoneIdProvider(
                    settingsRepository = settings,
                    deviceZoneIdSource = device,
                    activeTimerRepository = activeTimers,
                    applicationScope = backgroundScope,
                )
            runCurrent()
            activeTimers.createActiveInterval(
                taskId = task.id,
                boundaryZoneId = NEW_YORK,
                start = Instant.parse("2026-07-24T13:00:00Z"),
            )
            device.current = TOKYO
            runCurrent()

            assertEquals(NEW_YORK, provider.zoneId())

            activeTimers.closeActiveInterval(
                stop = Instant.parse("2026-07-24T14:00:00Z"),
            )
            runCurrent()
            assertEquals(TOKYO, provider.zoneId())
        }

    @Test
    fun currentDateUsesRealZoneRulesNearMidnightAndDst() =
        runTest {
            val settings = FakeSettingsRepository()
            settings.setManualZoneId(NEW_YORK)
            val provider =
                SettingsEffectiveZoneIdProvider(
                    settingsRepository = settings,
                    deviceZoneIdSource = FakeDeviceZoneIdSource(TOKYO),
                    activeTimerRepository =
                        FakeActiveTimerRepository(FakeTaskRepository()),
                    applicationScope = backgroundScope,
                )
            runCurrent()
            provider.awaitZoneId()
            val clock = FakeUtcClock(Instant.parse("2026-03-08T04:59:59Z"))
            val dates = CurrentDateProvider(clock, provider)

            assertEquals(
                java.time.LocalDate.of(2026, 3, 7),
                dates.today(),
            )
            clock.instant = Instant.parse("2026-03-08T05:00:00Z")
            assertEquals(
                java.time.LocalDate.of(2026, 3, 8),
                dates.today(),
            )
        }

    private class FakeDeviceZoneIdSource(
        initial: ZoneId,
    ) : DeviceZoneIdSource {
        private val state = MutableStateFlow(initial)

        var current: ZoneId
            get() = state.value
            set(value) {
                state.value = value
            }

        override fun currentZoneId(): ZoneId = state.value

        override fun observeZoneId(): Flow<ZoneId> = state
    }

    private companion object {
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val LOS_ANGELES: ZoneId = ZoneId.of("America/Los_Angeles")
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
    }
}
