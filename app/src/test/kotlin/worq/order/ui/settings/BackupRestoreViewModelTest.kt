package worq.order.ui.settings

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import worq.order.backup.PORTABLE_BACKUP_DATA_MODEL_VERSION
import worq.order.backup.PortableBackupArtifact
import worq.order.backup.PortableBackupCreationResult
import worq.order.backup.PortableBackupDataV1
import worq.order.backup.PortableBackupImportStageResult
import worq.order.backup.PortableBackupReplacementResult
import worq.order.backup.PortableBackupSettingsV1
import worq.order.backup.PortableBackupStagedImport
import worq.order.data.ActiveTimerRepository
import worq.order.data.BackupRestoreStatus
import worq.order.data.BackupRestoreStatusRepository
import worq.order.data.CreateActiveIntervalResult
import worq.order.model.ActiveTimer
import worq.order.model.ActiveTimerSnapshot
import worq.order.testing.FakeActiveTimerRepository
import worq.order.testing.FakeTaskRepository
import worq.order.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun confirmedImportRunsOnceAndReportsSuccess() =
        runTest(mainDispatcherRule.dispatcher) {
            val workflow = FakeBackupRestoreWorkflow()
            val statusRepository = FakeBackupRestoreStatusRepository()
            val viewModel = viewModel(workflow, statusRepository)
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }
            runCurrent()
            val effect =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.effects.first()
                }

            viewModel.onEvent(BackupRestoreEvent.ImportBackup)
            viewModel.onEvent(BackupRestoreEvent.ImportBackup)
            assertEquals(BackupRestoreEffect.LaunchImportBackup, effect.await())
            runCurrent()
            assertFalse(viewModel.uiState.value.canImportBackup)

            viewModel.onEvent(BackupRestoreEvent.ImportDocumentSelected("content://backup/valid"))
            runCurrent()
            assertEquals(
                BackupRestoreConfirmation.IMPORT,
                viewModel.uiState.value.confirmation,
            )

            viewModel.onEvent(BackupRestoreEvent.ConfirmImport)
            viewModel.onEvent(BackupRestoreEvent.ConfirmImport)
            runCurrent()

            assertEquals(1, workflow.importCount)
            assertEquals(
                BackupRestoreStatus.IMPORT_SUCCEEDED,
                viewModel.uiState.value.status?.status,
            )
            collector.cancel()
        }

    @Test
    fun confirmedRestoreRunsOnceAndPickerCancellationReturnsToIdle() =
        runTest(mainDispatcherRule.dispatcher) {
            val workflow = FakeBackupRestoreWorkflow(hasRestorePoint = true)
            val viewModel = viewModel(workflow, FakeBackupRestoreStatusRepository())
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }
            runCurrent()
            assertTrue(viewModel.uiState.value.canRestore)

            viewModel.onEvent(BackupRestoreEvent.RestorePreviousState)
            runCurrent()
            assertEquals(
                BackupRestoreConfirmation.RESTORE,
                viewModel.uiState.value.confirmation,
            )
            viewModel.onEvent(BackupRestoreEvent.ConfirmRestore)
            viewModel.onEvent(BackupRestoreEvent.ConfirmRestore)
            runCurrent()
            assertEquals(1, workflow.restoreCount)

            val effect =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.effects.first()
                }
            viewModel.onEvent(BackupRestoreEvent.CreateBackup)
            assertTrue(effect.await() is BackupRestoreEffect.LaunchCreateBackup)
            viewModel.onEvent(BackupRestoreEvent.BackupDocumentSelected(null))
            runCurrent()
            assertTrue(viewModel.uiState.value.canCreateBackup)
            assertEquals(0, workflow.createCount)
            collector.cancel()
        }

    @Test
    fun timerStartingWhilePickerIsOpenBlocksSelectedBackupDocuments() =
        runTest(mainDispatcherRule.dispatcher) {
            val workflow = FakeBackupRestoreWorkflow()
            val timerRepository = MutableActiveTimerRepository()
            val viewModel =
                viewModel(
                    workflow = workflow,
                    statusRepository = FakeBackupRestoreStatusRepository(),
                    activeTimerRepository = timerRepository,
                )
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.collect {}
                }
            runCurrent()

            val importEffect =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.effects.first()
                }
            viewModel.onEvent(BackupRestoreEvent.ImportBackup)
            assertEquals(BackupRestoreEffect.LaunchImportBackup, importEffect.await())
            timerRepository.setRunning(true)
            runCurrent()
            viewModel.onEvent(BackupRestoreEvent.ImportDocumentSelected("content://backup/valid"))
            runCurrent()

            assertEquals(0, workflow.stageCount)
            assertEquals(
                BackupRestoreStatus.IMPORT_TIMER_RUNNING,
                viewModel.uiState.value.status?.status,
            )

            timerRepository.setRunning(false)
            runCurrent()
            val createEffect =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.effects.first()
                }
            viewModel.onEvent(BackupRestoreEvent.CreateBackup)
            assertTrue(createEffect.await() is BackupRestoreEffect.LaunchCreateBackup)
            timerRepository.setRunning(true)
            runCurrent()
            viewModel.onEvent(BackupRestoreEvent.BackupDocumentSelected("content://backup/new"))
            runCurrent()

            assertEquals(0, workflow.createCount)
            assertEquals(
                BackupRestoreStatus.BACKUP_TIMER_RUNNING,
                viewModel.uiState.value.status?.status,
            )
            collector.cancel()
        }

    private fun viewModel(
        workflow: FakeBackupRestoreWorkflow,
        statusRepository: FakeBackupRestoreStatusRepository,
        activeTimerRepository: ActiveTimerRepository =
            FakeActiveTimerRepository(FakeTaskRepository()),
    ): BackupRestoreViewModel =
        BackupRestoreViewModel(
            activeTimerRepository = activeTimerRepository,
            statusRepository = statusRepository,
            workflow = workflow,
        )

    private class FakeBackupRestoreStatusRepository : BackupRestoreStatusRepository {
        private val status = MutableStateFlow<BackupRestoreStatus?>(null)

        override fun observeStatus(): Flow<BackupRestoreStatus?> = status

        override suspend fun setStatus(status: BackupRestoreStatus) {
            this.status.value = status
        }

        override suspend fun clearStatus() {
            status.value = null
        }
    }

    private class FakeBackupRestoreWorkflow(
        private val hasRestorePoint: Boolean = false,
    ) : BackupRestoreWorkflow {
        var createCount = 0
        var stageCount = 0
        var importCount = 0
        var restoreCount = 0

        override fun suggestedFileName(): String = "WorqOrder_Backup_test.zip"

        override suspend fun create(documentUri: String): PortableBackupCreationResult {
            createCount += 1
            return PortableBackupCreationResult.InvalidLocalState
        }

        override suspend fun stageImport(documentUri: String): PortableBackupImportStageResult {
            stageCount += 1
            return PortableBackupImportStageResult.Ready(STAGED_IMPORT)
        }

        override suspend fun import(
            stagedImport: PortableBackupStagedImport,
        ): PortableBackupReplacementResult {
            importCount += 1
            return PortableBackupReplacementResult.Replaced
        }

        override suspend fun discardStagedImport(stagedImport: PortableBackupStagedImport) = Unit

        override suspend fun restore(): PortableBackupReplacementResult {
            restoreCount += 1
            return PortableBackupReplacementResult.Restored
        }

        override suspend fun hasRestorePoint(): Boolean = hasRestorePoint
    }

    private class MutableActiveTimerRepository : ActiveTimerRepository {
        private val activeTimer = MutableStateFlow<ActiveTimer?>(null)

        fun setRunning(running: Boolean) {
            activeTimer.value =
                if (running) {
                    ActiveTimer(
                        intervalId = "interval-running",
                        taskId = "task-running",
                        boundaryZoneId = ZoneId.of("America/New_York"),
                        createdAt = Instant.parse("2026-09-22T12:00:00Z"),
                        updatedAt = Instant.parse("2026-09-22T12:00:00Z"),
                    )
                } else {
                    null
                }
        }

        override fun observeActiveTimer(): Flow<ActiveTimer?> = activeTimer

        override suspend fun readActiveTimer(): ActiveTimer? = activeTimer.value

        override suspend fun readActiveTimerSnapshot(): ActiveTimerSnapshot? = error("Not used")

        override suspend fun createActiveInterval(
            taskId: String,
            boundaryZoneId: ZoneId,
            start: Instant,
        ): CreateActiveIntervalResult = error("Not used")

        override suspend fun closeActiveInterval(stop: Instant): ActiveTimerSnapshot? =
            error("Not used")

        override suspend fun closeActiveIntervalAtBoundary(
            expectedIntervalId: String,
            boundary: Instant,
        ): ActiveTimerSnapshot? = error("Not used")

        override suspend fun closeActiveInterval(
            expectedIntervalId: String,
            stop: Instant,
        ): ActiveTimerSnapshot? = error("Not used")
    }

    private companion object {
        val STAGED_IMPORT =
            PortableBackupStagedImport(
                source =
                    PortableBackupArtifact(
                        name = "staged-source.zip",
                        byteCount = 1,
                        sha256 = "0".repeat(64),
                    ),
                data =
                    PortableBackupDataV1(
                        dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
                        clients = emptyList(),
                        consultants = emptyList(),
                        tags = emptyList(),
                        tasks = emptyList(),
                        settings =
                            PortableBackupSettingsV1(
                                themeMode = "SYSTEM",
                                timeZoneMode = "DEVICE",
                                manualZoneId = null,
                                defaultExportDestination = "CSV",
                                lastExportAttempt = null,
                                selectedConsultantId = null,
                                landscapeHandedness = "RIGHT_HANDED",
                            ),
                        selection = null,
                    ),
            )
    }
}
