package worq.order.ui.main

import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.data.NewDailyTask
import worq.order.data.ExportDestination
import worq.order.data.ExportAttemptOutcome
import worq.order.data.GoogleAccountHint
import worq.order.data.LandscapeHandedness
import worq.order.data.SelectedTaskState
import worq.order.domain.SelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.export.CsvExportCoordinator
import worq.order.export.XlsxExportCoordinator
import worq.order.export.automatic.AutomaticGoogleExportController
import worq.order.export.automatic.AutomaticGoogleExportSettingResult
import worq.order.export.csv.DocumentWriteResult
import worq.order.export.google.GoogleSheetExportReceipt
import worq.order.export.google.GoogleSheetsExportFailure
import worq.order.export.google.GoogleSheetsExportOperationResult
import worq.order.model.DailyTask
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeBinaryDocumentOutputDestination
import worq.order.testing.FakeDocumentOutputDestination
import worq.order.testing.FakeMonotonicTimeSource
import worq.order.testing.FakeSelectedTaskRepository
import worq.order.testing.FakeSettingsRepository
import worq.order.testing.FakeGoogleConnectionRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.FakeUtcClock
import worq.order.testing.FakeZoneIdProvider
import worq.order.testing.MainDispatcherRule
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.CurrentDateProvider
import worq.order.timer.LiveTimerSession
import worq.order.timer.TimerCoordinator
import worq.order.timer.TimerOperationLock
import worq.order.timer.TimerRecoveryCoordinator
import worq.order.timer.notification.RunningTimerNotificationController
import worq.order.timer.notification.RunningTimerNotificationResult

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun emptyStateAndStartWithoutSelectionAreSafe() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            assertTrue(viewModel.uiState.value.tasks.isEmpty())
            assertFalse(viewModel.uiState.value.canStart)
            assertEquals(MainUiState.ZERO_DURATION, viewModel.uiState.value.timerText)

            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            assertEquals(
                MainMessage.SELECT_A_TASK_FIRST,
                viewModel.uiState.value.message,
            )
        }

    @Test
    fun datedTaskCanBeSelectedAndItsCompletedTotalIsDisplayed() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY, description = "Prepare report")
            fixture.tasks.insertCompletedInterval(
                taskId = task.id,
                start = Instant.parse("2026-07-24T13:00:00Z"),
                stop = Instant.parse("2026-07-24T14:00:00Z"),
                wasManuallyEdited = false,
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(1, state.tasks.size)
            assertTrue(state.tasks.single().isSelected)
            assertTrue(state.canStart)
            assertEquals("01:00:00", state.timerText)
        }

    @Test
    fun newestTaskIsPresentedFirstWithoutChangingRepositoryOrder() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val oldest =
                fixture.addTask(
                    TODAY,
                    description = "Oldest",
                    seriesId = "series-oldest",
                )
            val newest =
                fixture.addTask(
                    TODAY,
                    description = "Newest",
                    seriesId = "series-newest",
                )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            assertEquals(
                listOf(newest.id, oldest.id),
                viewModel.uiState.value.tasks.map(MainTaskItemUi::id),
            )
        }

    @Test
    fun historicalSelectionDisplaysItsTotalButCannotStart() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val historical = fixture.addTask(TODAY.minusDays(1))
            fixture.tasks.insertCompletedInterval(
                taskId = historical.id,
                start = Instant.parse("2026-07-23T13:00:00Z"),
                stop = Instant.parse("2026-07-23T13:30:00Z"),
                wasManuallyEdited = false,
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(historical.id))
            runCurrent()

            assertEquals(TODAY.minusDays(1), viewModel.uiState.value.displayedDate)
            assertEquals("00:30:00", viewModel.uiState.value.timerText)
            assertFalse(viewModel.uiState.value.canStart)

            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            assertNull(fixture.selection.readSelection())
            assertEquals(
                MainMessage.TIMING_SELECTION_CLEARED,
                viewModel.uiState.value.message,
            )
        }

    @Test
    fun recreatedMainViewModelClearsStalePersistedSelectionWithoutCreatingTask() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val historical = fixture.addTask(TODAY.minusDays(1))
            fixture.selection.select(
                SelectedTaskState(
                    taskId = historical.id,
                    seriesId = historical.seriesId,
                    selectedOnDate = historical.workDate,
                    selectedInZone = historical.zoneId,
                ),
            )

            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            assertNull(fixture.selection.readSelection())
            assertFalse(viewModel.uiState.value.canStart)
            assertNull(viewModel.uiState.value.message)
            assertTrue(fixture.tasks.observeTasksForDate(TODAY).first().isEmpty())
            assertEquals(historical, fixture.tasks.readTaskWithClient(historical.id)?.task)
        }

    @Test
    fun repeatedStartSelectsZeroBasedCopyAndStopFreezesItsOwnTotal() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.tasks.insertCompletedInterval(
                taskId = task.id,
                start = NOW.minusSeconds(3_600),
                stop = NOW,
                wasManuallyEdited = false,
            )
            fixture.clock.instant = NOW.plusSeconds(60)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()

            assertEquals("01:00:00", viewModel.uiState.value.timerText)
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()
            assertEquals(MainTimerAction.STOP, viewModel.uiState.value.timerAction)
            assertEquals("00:00:00", viewModel.uiState.value.timerText)
            val repeatedTaskId = requireNotNull(fixture.selection.readSelection()).taskId
            assertTrue(repeatedTaskId != task.id)
            val source = requireNotNull(fixture.tasks.readTaskWithClient(task.id)).task
            val repeated = requireNotNull(fixture.tasks.readTaskWithClient(repeatedTaskId)).task
            assertEquals(source.seriesId, repeated.seriesId)
            assertEquals(source.clientId, repeated.clientId)
            assertEquals(source.description, repeated.description)
            assertEquals(source.hardwareSoftwarePurchases, repeated.hardwareSoftwarePurchases)
            assertEquals(source.employeeId, repeated.employeeId)
            assertEquals(source.employeeNameSnapshot, repeated.employeeNameSnapshot)
            assertEquals(source.workType, repeated.workType)
            assertEquals(source.billingStatus, repeated.billingStatus)
            assertEquals(source.mileage, repeated.mileage)
            assertEquals(TODAY, repeated.workDate)
            assertEquals(NEW_YORK, repeated.zoneId)

            fixture.clock.instant = fixture.clock.instant.plusSeconds(1)
            fixture.monotonic.nanos += Duration.ofSeconds(1).toNanos()
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS)
            runCurrent()
            assertEquals("00:00:01", viewModel.uiState.value.timerText)

            viewModel.onEvent(MainEvent.StopTimer)
            runCurrent()
            assertEquals(MainTimerAction.START, viewModel.uiState.value.timerAction)
            assertEquals("00:00:01", viewModel.uiState.value.timerText)

            fixture.monotonic.nanos += Duration.ofMinutes(10).toNanos()
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS * 2)
            runCurrent()
            assertEquals("00:00:01", viewModel.uiState.value.timerText)
            assertEquals(
                Duration.ofHours(1).toMillis(),
                fixture.tasks.readCompletedDurationMillis(task.id),
            )
            assertEquals(
                Duration.ofSeconds(1).toMillis(),
                fixture.tasks.readCompletedDurationMillis(repeatedTaskId),
            )
        }

    @Test
    fun firstSuccessfulStartRequestsNotificationPermissionWithoutBlockingTimer() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val notifications =
                FakeRunningTimerNotifications(
                    reconcileResult =
                        RunningTimerNotificationResult.RuntimePermissionRequired,
                )
            val viewModel = fixture.viewModel(notifications)
            collectState(viewModel)
            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()

            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            assertEquals(
                MainEffect.RequestRunningTimerNotificationPermission,
                effect.await(),
            )
            assertEquals(MainTimerAction.STOP, viewModel.uiState.value.timerAction)
            assertTrue(notifications.reconcileCount > 0)
        }

    @Test
    fun successfulStopCleansNotificationPresentation() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val notifications = FakeRunningTimerNotifications()
            val viewModel = fixture.viewModel(notifications)
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()
            fixture.monotonic.nanos += Duration.ofSeconds(1).toNanos()

            viewModel.onEvent(MainEvent.StopTimer)
            runCurrent()

            assertEquals(1, notifications.stopCount)
            assertEquals(MainTimerAction.START, viewModel.uiState.value.timerAction)
        }

    @Test
    fun runningTimerDisablesEveryExportDestinationUntilStopped() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val viewModel = fixture.viewModel()
            val effects = mutableListOf<MainEffect>()
            collectState(viewModel)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect(effects::add)
            }
            runCurrent()

            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            ExportDestination.entries.forEach { destination ->
                fixture.settings.setDefaultExportDestination(destination)
                runCurrent()

                assertEquals(destination, viewModel.uiState.value.exportDestination)
                assertFalse(viewModel.uiState.value.canExport)
                viewModel.onEvent(MainEvent.Export)
                runCurrent()
                assertTrue(effects.isEmpty())
                assertNull(viewModel.uiState.value.exportProgress)
            }

            fixture.clock.instant = fixture.clock.instant.plusSeconds(1)
            fixture.monotonic.nanos += Duration.ofSeconds(1).toNanos()
            viewModel.onEvent(MainEvent.StopTimer)
            runCurrent()
            assertTrue(viewModel.uiState.value.canExport)
        }

    @Test
    fun stopAfterWallClockChangeKeepsTheMonotonicDisplayedTotal() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            fixture.monotonic.nanos += Duration.ofSeconds(45).toNanos()
            fixture.clock.instant = fixture.clock.instant.plusSeconds(65)
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS)
            runCurrent()
            val visibleBeforeStop = viewModel.uiState.value.timerText

            viewModel.onEvent(MainEvent.StopTimer)
            runCurrent()

            assertEquals("00:00:45", visibleBeforeStop)
            assertEquals(visibleBeforeStop, viewModel.uiState.value.timerText)
            assertEquals(
                Duration.ofSeconds(45).toMillis(),
                fixture.tasks.readCompletedDurationMillis(task.id),
            )
        }

    @Test
    fun activityRecreationReusesPersistedTimerAndApplicationLiveAnchor() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val first = fixture.viewModel()
            collectState(first)
            runCurrent()
            first.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()
            first.onEvent(MainEvent.StartTimer)
            runCurrent()

            fixture.clock.instant = fixture.clock.instant.plusSeconds(300)
            fixture.monotonic.nanos += Duration.ofMinutes(5).toNanos()

            val recreated = fixture.viewModel()
            collectState(recreated)
            runCurrent()
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS)
            runCurrent()

            assertEquals(MainTimerAction.STOP, recreated.uiState.value.timerAction)
            assertEquals("00:05:00", recreated.uiState.value.timerText)
            assertEquals(task.id, recreated.uiState.value.runningTask?.taskId)
            assertEquals(
                1,
                requireNotNull(
                    fixture.tasks.readTaskWithIntervals(task.id),
                ).intervals.size,
            )
        }

    @Test
    fun sharedTickerRefreshesDoNotWriteChangingDurationToRepository() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect()
            }
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()
            val before =
                requireNotNull(
                    fixture.active.readActiveTimerSnapshot(),
                ).interval

            fixture.monotonic.nanos += Duration.ofSeconds(50).toNanos()
            fixture.clock.instant = fixture.clock.instant.plusSeconds(50)
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS * 1_000)
            runCurrent()

            val after =
                requireNotNull(
                    fixture.active.readActiveTimerSnapshot(),
                ).interval
            assertEquals(before, after)
            assertEquals("00:00:50", viewModel.uiState.value.timerText)
            assertEquals(
                1,
                requireNotNull(
                    fixture.tasks.readTaskWithIntervals(task.id),
                ).intervals.size,
            )
        }

    @Test
    fun visibleTimerRefreshesOnlyAtTheConfiguredPresentationCadence() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            fixture.monotonic.nanos += Duration.ofSeconds(1).toNanos()
            fixture.clock.instant = fixture.clock.instant.plusSeconds(1)
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS - 1)
            runCurrent()
            assertEquals("00:00:00", viewModel.uiState.value.timerText)

            advanceTimeBy(1)
            runCurrent()
            assertEquals("00:00:01", viewModel.uiState.value.timerText)
        }

    @Test
    fun runningTimerLocksTaskSwitchingAndRemainsVisibleWhileBrowsing() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val first = fixture.addTask(TODAY, seriesId = "series-1")
            val second = fixture.addTask(TODAY, seriesId = "series-2")
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(first.id))
            runCurrent()
            viewModel.onEvent(MainEvent.StartTimer)
            runCurrent()

            viewModel.onEvent(MainEvent.SelectTask(second.id))
            runCurrent()

            assertEquals(
                MainMessage.STOP_BEFORE_SWITCHING,
                viewModel.uiState.value.message,
            )
            assertTrue(viewModel.uiState.value.tasks.single { it.id == first.id }.isRunning)
            assertFalse(viewModel.uiState.value.tasks.single { it.id == second.id }.canSelect)

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            assertFalse(viewModel.uiState.value.isToday)
            assertEquals(MainTimerAction.STOP, viewModel.uiState.value.timerAction)
            assertTrue(viewModel.uiState.value.canStop)
            assertEquals(first.id, viewModel.uiState.value.runningTask?.taskId)
        }

    @Test
    fun dateArrowsPickerAndTodayShortcutUpdateDisplayedDate() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = Fixture().viewModel()
            collectState(viewModel)
            runCurrent()

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            assertEquals(TODAY.minusDays(1), viewModel.uiState.value.displayedDate)

            viewModel.onEvent(MainEvent.NextDate)
            runCurrent()
            assertEquals(TODAY, viewModel.uiState.value.displayedDate)

            val picked = LocalDate.of(2026, 6, 1)
            viewModel.onEvent(MainEvent.OpenDatePicker)
            viewModel.onEvent(MainEvent.PickDate(picked))
            runCurrent()
            assertEquals(picked, viewModel.uiState.value.displayedDate)
            assertFalse(viewModel.uiState.value.isDatePickerVisible)

            viewModel.onEvent(MainEvent.ReturnToToday)
            runCurrent()
            assertEquals(TODAY, viewModel.uiState.value.displayedDate)
        }

    @Test
    fun foregroundDateBoundaryAdvancesTodayWithoutAnActiveTimer() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.clock.instant = Instant.parse("2026-07-25T03:50:00Z")
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            fixture.clock.instant = Instant.parse("2026-07-25T04:00:01Z")
            advanceTimeBy(Duration.ofMinutes(10).toMillis())
            runCurrent()

            assertEquals(TODAY.plusDays(1), viewModel.uiState.value.today)
            assertEquals(TODAY.plusDays(1), viewModel.uiState.value.displayedDate)
        }

    @Test
    fun foregroundDateBoundaryPreservesAnIntentionallyBrowsedDate() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.clock.instant = Instant.parse("2026-07-25T03:50:00Z")
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            val browsedDate = viewModel.uiState.value.displayedDate

            fixture.clock.instant = Instant.parse("2026-07-25T04:00:01Z")
            advanceTimeBy(Duration.ofMinutes(10).toMillis())
            runCurrent()

            assertEquals(TODAY.plusDays(1), viewModel.uiState.value.today)
            assertEquals(browsedDate, viewModel.uiState.value.displayedDate)
        }

    @Test
    fun browsingDatesDoesNotCreateTasksOrChangePersistentSelection() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            viewModel.onEvent(MainEvent.NextDate)
            runCurrent()
            viewModel.onEvent(MainEvent.PickDate(TODAY.plusDays(5)))
            runCurrent()

            assertEquals(task.id, fixture.selection.readSelection()?.taskId)
            assertTrue(fixture.tasks.observeTasksForDate(TODAY.plusDays(5)).first().isEmpty())
            assertTrue(fixture.tasks.observeTasksForDate(TODAY).first().any { it.task.id == task.id })
        }

    @Test
    fun navigationEventsCarryTheCurrentPresentationContext() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = Fixture().viewModel()
            collectState(viewModel)
            runCurrent()
            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()

            val createEffect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.OpenCreateTask)
            assertEquals(
                MainEffect.NavigateToCreateTask(TODAY.minusDays(1)),
                createEffect.await(),
            )

            val settingsEffect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.OpenSettings)
            assertEquals(MainEffect.NavigateToSettings, settingsEffect.await())
        }

    @Test
    fun exportDestinationUpdatesButtonStateAndGoogleRoutesToSetup() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            assertEquals(
                ExportDestination.CSV,
                viewModel.uiState.value.exportDestination,
            )
            assertTrue(viewModel.uiState.value.canExport)

            fixture.settings.setDefaultExportDestination(
                ExportDestination.GOOGLE_SHEETS,
            )
            runCurrent()

            assertEquals(
                ExportDestination.GOOGLE_SHEETS,
                viewModel.uiState.value.exportDestination,
            )
            assertTrue(viewModel.uiState.value.canExport)

            fixture.settings.setLandscapeHandedness(
                LandscapeHandedness.LEFT_HANDED,
            )
            runCurrent()

            assertEquals(
                LandscapeHandedness.LEFT_HANDED,
                viewModel.uiState.value.landscapeHandedness,
            )

            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            assertEquals(
                MainEffect.NavigateToGoogleSheetsSettings,
                effect.await(),
            )
        }

    @Test
    fun connectedGoogleDestinationEmitsExportAndRecordsSuccess() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.settings.setDefaultExportDestination(
                ExportDestination.GOOGLE_SHEETS,
            )
            fixture.google.saveSignedInAccount(
                GoogleAccountHint("person@example.com", "Person"),
            )
            fixture.google.saveConnectedSpreadsheet(
                spreadsheetId =
                    "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789",
                spreadsheetTitle = "Work Log",
                validatedAt = NOW,
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            assertEquals(
                MainGoogleExportState.CONNECTED,
                viewModel.uiState.value.googleExportState,
            )
            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()
            assertEquals(
                MainEffect.ExportToGoogleSheets(TODAY),
                effect.await(),
            )
            assertEquals(
                MainExportProgress.PREPARING,
                viewModel.uiState.value.exportProgress,
            )
            viewModel.onGoogleSheetsExportResult(
                GoogleSheetsExportOperationResult.Success(
                    GoogleSheetExportReceipt(
                        workDate = TODAY,
                        exportedAt = NOW,
                        spreadsheetId =
                            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789",
                        spreadsheetTitle = "Work Log",
                        tabName = "WorqOrder_2026-07-24",
                        dataRowCount = 0,
                    ),
                ),
            )
            runCurrent()

            assertEquals(
                MainExportOutcome.SUCCESS,
                viewModel.uiState.value.exportFeedback?.outcome,
            )
            assertEquals(
                ExportDestination.GOOGLE_SHEETS,
                viewModel.uiState.value.exportFeedback?.destination,
            )
            assertEquals(
                ExportDestination.GOOGLE_SHEETS,
                fixture.settings
                    .readSettings()
                    .lastExportAttempt
                    ?.destination,
            )
            assertEquals(
                ExportAttemptOutcome.SUCCESS,
                fixture.settings.readSettings().lastExportAttempt?.outcome,
            )
            assertTrue(fixture.document.writes.isEmpty())
        }

    @Test
    fun googleFailureClearsProgressAndRecordsTypedDiagnostic() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.settings.setDefaultExportDestination(
                ExportDestination.GOOGLE_SHEETS,
            )
            fixture.google.saveSignedInAccount(
                GoogleAccountHint("person@example.com", "Person"),
            )
            fixture.google.saveConnectedSpreadsheet(
                spreadsheetId =
                    "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789",
                spreadsheetTitle = "Work Log",
                validatedAt = NOW,
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()
            effect.await()

            viewModel.onGoogleSheetsExportResult(
                GoogleSheetsExportOperationResult.Failed(
                    GoogleSheetsExportFailure.OFFLINE,
                ),
            )
            runCurrent()

            assertNull(viewModel.uiState.value.exportProgress)
            assertEquals(
                MainExportOutcome.OFFLINE,
                viewModel.uiState.value.exportFeedback?.outcome,
            )
            assertEquals(
                worq.order.data.ExportErrorCategory.GOOGLE_OFFLINE,
                fixture.settings
                    .readSettings()
                    .lastExportAttempt
                    ?.errorCategory,
            )
            assertTrue(fixture.document.writes.isEmpty())
        }

    @Test
    fun googleAuthorizationCancellationIsVisuallySilentAndRecorded() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.settings.setDefaultExportDestination(
                ExportDestination.GOOGLE_SHEETS,
            )
            fixture.google.saveSignedInAccount(
                GoogleAccountHint("person@example.com", "Person"),
            )
            fixture.google.saveConnectedSpreadsheet(
                spreadsheetId =
                    "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789",
                spreadsheetTitle = "Work Log",
                validatedAt = NOW,
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()
            effect.await()

            viewModel.onGoogleSheetsExportResult(
                GoogleSheetsExportOperationResult.Canceled,
            )
            runCurrent()

            assertNull(viewModel.uiState.value.exportProgress)
            assertNull(viewModel.uiState.value.exportFeedback)
            assertEquals(
                ExportAttemptOutcome.CANCELED,
                fixture.settings.readSettings().lastExportAttempt?.outcome,
            )
        }

    @Test
    fun csvPickerCancellationIsNeutralAndWritesNothing() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()

            assertEquals(
                MainEffect.LaunchCsvDocument("worqorder_2026-07-24.csv"),
                effect.await(),
            )
            assertEquals(
                MainExportProgress.CHOOSING_DESTINATION,
                viewModel.uiState.value.exportProgress,
            )

            viewModel.onEvent(MainEvent.CsvDocumentSelected(null))
            runCurrent()

            assertTrue(fixture.document.writes.isEmpty())
            assertNull(viewModel.uiState.value.exportProgress)
            assertNull(viewModel.uiState.value.exportFeedback)
            assertEquals(
                ExportAttemptOutcome.CANCELED,
                fixture.settings.readSettings().lastExportAttempt?.outcome,
            )
        }

    @Test
    fun csvSnapshotRemainsStableWhilePickerIsOpenAndSuccessIsRecorded() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY, description = "Original description")
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()
            effect.await()

            fixture.tasks.updateTaskMetadata(
                taskId = task.id,
                clientId = task.clientId,
                description = "Changed after picker opened",
                hardwareSoftwarePurchases = "",
                employeeId = task.employeeId,
                workType = task.workType,
                mileage = task.mileage,
            )
            viewModel.onEvent(
                MainEvent.CsvDocumentSelected("content://documents/export.csv"),
            )
            runCurrent()

            val write = fixture.document.writes.single()
            assertTrue(write.contents.contains("Original description"))
            assertFalse(write.contents.contains("Changed after picker opened"))
            assertEquals(
                MainExportOutcome.SUCCESS,
                viewModel.uiState.value.exportFeedback?.outcome,
            )
            assertEquals(
                ExportAttemptOutcome.SUCCESS,
                fixture.settings.readSettings().lastExportAttempt?.outcome,
            )
        }

    @Test
    fun xlsxUsesFreshDocumentFlowAndStablePreparedSnapshot() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.settings.setDefaultExportDestination(ExportDestination.XLSX)
            val task = fixture.addTask(TODAY, description = "Original description")
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()

            assertEquals(
                MainEffect.LaunchXlsxDocument(
                    "worqorder_2026-07-24.xlsx",
                ),
                effect.await(),
            )
            fixture.tasks.updateTaskMetadata(
                taskId = task.id,
                clientId = task.clientId,
                description = "Changed after picker opened",
                hardwareSoftwarePurchases = "",
                employeeId = task.employeeId,
                workType = task.workType,
                mileage = task.mileage,
            )
            viewModel.onEvent(
                MainEvent.XlsxDocumentSelected(
                    "content://documents/export.xlsx",
                ),
            )
            runCurrent()

            val write = fixture.binaryDocument.writes.single()
            val worksheet =
                zipEntryText(
                    bytes = write.contents,
                    entryName = "xl/worksheets/sheet1.xml",
                )
            assertTrue(worksheet.contains("Original description"))
            assertFalse(worksheet.contains("Changed after picker opened"))
            assertEquals(
                ExportDestination.XLSX,
                viewModel.uiState.value.exportFeedback?.destination,
            )
            assertEquals(
                MainExportOutcome.SUCCESS,
                viewModel.uiState.value.exportFeedback?.outcome,
            )
            assertEquals(
                ExportDestination.XLSX,
                fixture.settings.readSettings().lastExportAttempt?.destination,
            )
        }

    @Test
    fun xlsxPickerCancellationWritesNothing() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.settings.setDefaultExportDestination(ExportDestination.XLSX)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()
            effect.await()
            viewModel.onEvent(MainEvent.XlsxDocumentSelected(null))
            runCurrent()

            assertTrue(fixture.binaryDocument.writes.isEmpty())
            assertNull(viewModel.uiState.value.exportProgress)
            assertNull(viewModel.uiState.value.exportFeedback)
            assertEquals(
                ExportAttemptOutcome.CANCELED,
                fixture.settings.readSettings().lastExportAttempt?.outcome,
            )
        }

    @Test
    fun xlsxOutputFailureIsActionableAndDoesNotClaimSuccess() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.settings.setDefaultExportDestination(ExportDestination.XLSX)
            fixture.binaryDocument.result =
                DocumentWriteResult.Failed(partialDocumentMayRemain = true)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()
            effect.await()
            viewModel.onEvent(
                MainEvent.XlsxDocumentSelected(
                    "content://documents/failure.xlsx",
                ),
            )
            runCurrent()

            assertEquals(
                MainExportOutcome.PARTIAL_OUTPUT_MAY_REMAIN,
                viewModel.uiState.value.exportFeedback?.outcome,
            )
            assertEquals(
                ExportAttemptOutcome.FAILED,
                fixture.settings.readSettings().lastExportAttempt?.outcome,
            )
        }

    @Test
    fun csvOutputFailureIsActionableAndDoesNotClaimSuccess() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.document.result =
                DocumentWriteResult.Failed(partialDocumentMayRemain = false)
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            val effect = async { viewModel.effects.first() }
            runCurrent()
            viewModel.onEvent(MainEvent.Export)
            runCurrent()
            effect.await()
            viewModel.onEvent(
                MainEvent.CsvDocumentSelected("content://documents/failure.csv"),
            )
            runCurrent()

            assertEquals(
                MainExportOutcome.OUTPUT_FAILED,
                viewModel.uiState.value.exportFeedback?.outcome,
            )
            assertEquals(
                ExportAttemptOutcome.FAILED,
                fixture.settings.readSettings().lastExportAttempt?.outcome,
            )
        }

    @Test
    fun effectiveZoneChangeMovesTodayPresentationButPreservesBrowsedDate() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.clock.instant = Instant.parse("2026-07-25T02:00:00Z")
            val historical =
                fixture.addTask(
                    LocalDate.of(2026, 7, 23),
                    seriesId = "historical-series",
                )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()
            assertEquals(LocalDate.of(2026, 7, 24), viewModel.uiState.value.today)

            fixture.zone.current = ZoneId.of("Asia/Tokyo")
            runCurrent()
            assertEquals(LocalDate.of(2026, 7, 25), viewModel.uiState.value.today)
            assertEquals(
                LocalDate.of(2026, 7, 25),
                viewModel.uiState.value.displayedDate,
            )
            val unchanged =
                requireNotNull(
                    fixture.tasks.readTaskWithClient(historical.id),
                ).task
            assertEquals(LocalDate.of(2026, 7, 23), unchanged.workDate)
            assertEquals(NEW_YORK, unchanged.zoneId)

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            val browsedDate = viewModel.uiState.value.displayedDate
            fixture.zone.current = ZoneId.of("Pacific/Honolulu")
            runCurrent()
            assertEquals(browsedDate, viewModel.uiState.value.displayedDate)
        }

    @Test
    fun resumeAfterDateRolloverFollowsTodayButPreservesBrowsing() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            fixture.clock.instant = NOW.plusSeconds(24 * 60 * 60)
            viewModel.onEvent(MainEvent.LifecycleResumed)
            runCurrent()
            assertEquals(TODAY.plusDays(1), viewModel.uiState.value.today)
            assertEquals(TODAY.plusDays(1), viewModel.uiState.value.displayedDate)

            viewModel.onEvent(MainEvent.PreviousDate)
            runCurrent()
            val browsed = viewModel.uiState.value.displayedDate
            fixture.clock.instant = fixture.clock.instant.plusSeconds(24 * 60 * 60)
            viewModel.onEvent(MainEvent.LifecycleResumed)
            runCurrent()
            assertEquals(browsed, viewModel.uiState.value.displayedDate)
        }

    @Test
    fun foregroundRecoveryClosesAtPinnedBoundaryAndSignalsPendingAutomaticExport() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            fixture.active.createActiveInterval(
                taskId = task.id,
                boundaryZoneId = NEW_YORK,
                start = Instant.parse("2026-07-25T03:30:00Z"),
            )
            fixture.clock.instant = Instant.parse("2026-07-25T04:10:00Z")
            val automaticExport = FakeAutomaticGoogleExportManager()

            val viewModel = fixture.viewModel(automaticGoogleExport = automaticExport)
            collectState(viewModel)
            runCurrent()

            assertEquals(1, automaticExport.timerStoppedCount)
            assertNull(fixture.active.readActiveTimerSnapshot())
            assertEquals(
                Instant.parse("2026-07-25T04:00:00Z"),
                fixture.tasks.readTaskWithIntervals(task.id)?.intervals?.single()?.stop,
            )
            assertNull(viewModel.uiState.value.message)
        }

    @Test
    fun externalBoundaryCloseCannotStrandTheMainScreenOnYesterday() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.clock.instant = Instant.parse("2026-07-25T03:50:00Z")
            val task = fixture.addTask(TODAY)
            fixture.active.createActiveInterval(
                taskId = task.id,
                boundaryZoneId = NEW_YORK,
                start = Instant.parse("2026-07-25T03:30:00Z"),
            )
            val viewModel = fixture.viewModel()
            collectState(viewModel)
            runCurrent()

            fixture.clock.instant = Instant.parse("2026-07-25T04:00:01Z")
            fixture.monotonic.nanos +=
                Duration.ofMinutes(10).plusSeconds(1).toNanos()
            fixture.normalizeActiveTimer()
            runCurrent()
            assertNull(fixture.active.readActiveTimerSnapshot())

            advanceTimeBy(Duration.ofMinutes(10).toMillis())
            runCurrent()

            assertEquals(TODAY.plusDays(1), viewModel.uiState.value.today)
            assertEquals(TODAY.plusDays(1), viewModel.uiState.value.displayedDate)
        }

    @Test
    fun foregroundTickerStillSignalsAutomaticExportWhenBoundaryCloseCancelsItsActiveFlow() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            fixture.clock.instant = Instant.parse("2026-07-25T03:50:00Z")
            val task = fixture.addTask(TODAY)
            fixture.active.createActiveInterval(
                taskId = task.id,
                boundaryZoneId = NEW_YORK,
                start = Instant.parse("2026-07-25T03:30:00Z"),
            )
            val automaticExport = FakeAutomaticGoogleExportManager()
            val viewModel = fixture.viewModel(automaticGoogleExport = automaticExport)
            collectState(viewModel)
            runCurrent()
            assertEquals(0, automaticExport.timerStoppedCount)

            fixture.clock.instant = Instant.parse("2026-07-25T04:00:01Z")
            fixture.monotonic.nanos += Duration.ofMinutes(10).plusSeconds(1).toNanos()
            advanceTimeBy(MainViewModel.TIMER_REFRESH_MILLIS)
            runCurrent()

            assertNull(fixture.active.readActiveTimerSnapshot())
            assertEquals(
                Instant.parse("2026-07-25T04:00:00Z"),
                fixture.tasks.readTaskWithIntervals(task.id)?.intervals?.single()?.stop,
            )
            assertEquals(1, automaticExport.timerStoppedCount)
            assertNull(viewModel.uiState.value.message)
        }

    @Test
    fun repositoryBackedSelectionSurvivesViewModelRecreation() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = Fixture()
            val task = fixture.addTask(TODAY)
            val first = fixture.viewModel()
            collectState(first)
            runCurrent()
            first.onEvent(MainEvent.SelectTask(task.id))
            runCurrent()
            assertTrue(first.uiState.value.tasks.single().isSelected)

            val recreated = fixture.viewModel()
            collectState(recreated)
            runCurrent()

            assertTrue(recreated.uiState.value.tasks.single().isSelected)
            assertTrue(recreated.uiState.value.canStart)
        }

    private fun kotlinx.coroutines.test.TestScope.collectState(
        viewModel: MainViewModel,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private fun zipEntryText(
        bytes: ByteArray,
        entryName: String,
    ): String =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryName) {
                    return@use zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            error("Missing ZIP entry $entryName")
        }

    private class Fixture {
        val tasks = FakeTaskRepository()
        val selection = FakeSelectedTaskRepository()
        val active = FakeActiveTimerRepository(tasks)
        val clock = FakeUtcClock(NOW)
        val zone = FakeZoneIdProvider(NEW_YORK)
        val settings = FakeSettingsRepository()
        val google = FakeGoogleConnectionRepository()
        val monotonic = FakeMonotonicTimeSource()
        private val currentDateProvider = CurrentDateProvider(clock, zone)
        private val operationLock = TimerOperationLock()
        private val liveTimerSession = LiveTimerSession(monotonic)
        private val selectionCoordinator =
            SelectionCoordinator(
                selectedTaskRepository = selection,
                taskRepository = tasks,
                activeTimerRepository = active,
                currentDateProvider = currentDateProvider,
                zoneIdProvider = zone,
            )
        private val timerCoordinator =
            TimerCoordinator(
                activeTimerRepository = active,
                taskRepository = tasks,
                selectedTaskRepository = selection,
                clock = clock,
                zoneIdProvider = zone,
                liveTimerSession = liveTimerSession,
                operationLock = operationLock,
            )
        private val normalizer =
            ActiveTimerNormalizer(
                activeTimerRepository = active,
                taskRepository = tasks,
                selectedTaskRepository = selection,
                clock = clock,
                liveTimerSession = liveTimerSession,
                operationLock = operationLock,
            )
        private val recoveryCoordinator =
            TimerRecoveryCoordinator(
                activeTimerNormalizer = normalizer,
                selectionCoordinator = selectionCoordinator,
                zoneIdProvider = zone,
                clock = clock,
            )
        private val csvExportCoordinator =
            CsvExportCoordinator(
                taskRepository = tasks,
                activeTimerRepository = active,
                activeTimerNormalizer = normalizer,
                clock = clock,
                timerOperationLock = operationLock,
            )
        private val xlsxExportCoordinator =
            XlsxExportCoordinator(
                snapshotCoordinator =
                    worq.order.export.ExportSnapshotCoordinator(
                        taskRepository = tasks,
                        activeTimerRepository = active,
                        activeTimerNormalizer = normalizer,
                        clock = clock,
                        timerOperationLock = operationLock,
                    ),
            )
        val document = FakeDocumentOutputDestination()
        val binaryDocument = FakeBinaryDocumentOutputDestination()
        private val taskMutationCoordinator =
            TaskMutationCoordinator(
                taskRepository = tasks,
                selectionCoordinator = selectionCoordinator,
                currentDateProvider = currentDateProvider,
                zoneIdProvider = zone,
            )

        fun viewModel(
            runningTimerNotifications: RunningTimerNotificationController? = null,
            automaticGoogleExport: AutomaticGoogleExportController? = null,
        ) =
            MainViewModel(
                taskRepository = tasks,
                activeTimerRepository = active,
                selectionCoordinator = selectionCoordinator,
                timerCoordinator = timerCoordinator,
                timerRecoveryCoordinator = recoveryCoordinator,
                liveTimerSession = liveTimerSession,
                utcClock = clock,
                zoneIdProvider = zone,
                currentDateProvider = currentDateProvider,
                taskMutationCoordinator = taskMutationCoordinator,
                settingsRepository = settings,
                googleConnectionRepository = google,
                csvExportCoordinator = csvExportCoordinator,
                xlsxExportCoordinator = xlsxExportCoordinator,
                documentOutputDestination = document,
                binaryDocumentOutputDestination = binaryDocument,
                automaticGoogleExportManager = automaticGoogleExport,
                runningTimerNotificationController = runningTimerNotifications,
            )

        suspend fun addTask(
            date: LocalDate,
            description: String = "Task",
            seriesId: String = "series-1",
        ): DailyTask =
            tasks.insertDailyTask(
                NewDailyTask(
                    clientId = "client-1",
                    description = description,
                    workDate = date,
                    zoneId = NEW_YORK,
                    seriesId = seriesId,
                ),
            )

        suspend fun normalizeActiveTimer() {
            normalizer.normalize(clock.now())
        }
    }

    private class FakeRunningTimerNotifications(
        var reconcileResult: RunningTimerNotificationResult =
            RunningTimerNotificationResult.Posted,
    ) : RunningTimerNotificationController {
        var reconcileCount = 0
        var stopCount = 0
        val dismissedIntervals = mutableListOf<String>()

        override suspend fun reconcile(): RunningTimerNotificationResult {
            reconcileCount += 1
            return reconcileResult
        }

        override suspend fun onTimerStopped() {
            stopCount += 1
        }

        override suspend fun recordDismissal(intervalId: String) {
            dismissedIntervals += intervalId
        }
    }

    private class FakeAutomaticGoogleExportManager : AutomaticGoogleExportController {
        var timerStoppedCount = 0

        override suspend fun setEnabled(
            enabled: Boolean,
        ): AutomaticGoogleExportSettingResult =
            AutomaticGoogleExportSettingResult.Failed

        override suspend fun onTimerStopped() {
            timerStoppedCount += 1
        }

        override suspend fun completeInteractiveExport(
            workDate: LocalDate,
            result: GoogleSheetsExportOperationResult,
        ) = Unit
    }

    private companion object {
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 24)
        val NOW: Instant = Instant.parse("2026-07-24T13:00:00Z")
    }
}
