package worq.order.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.BackupRestoreStatus

class BackupRestoreUiStateTest {
    @Test
    fun idleWithoutTimerEnablesCreateAndImportButNotRestore() {
        val state = BackupRestoreUiState()

        assertTrue(state.actionsEnabled)
        assertTrue(state.canCreateBackup)
        assertTrue(state.canImportBackup)
        assertFalse(state.canRestore)
    }

    @Test
    fun verifiedRestorePointEnablesRestoreOnlyWhenIdle() {
        val state = BackupRestoreUiState(hasRestorePoint = true)

        assertTrue(state.canCreateBackup)
        assertTrue(state.canImportBackup)
        assertTrue(state.canRestore)
    }

    @Test
    fun timerRunningDisablesEveryBackupAndRestoreAction() {
        val state =
            BackupRestoreUiState(
                isTimerRunning = true,
                hasRestorePoint = true,
            )

        assertFalse(state.actionsEnabled)
        assertFalse(state.canCreateBackup)
        assertFalse(state.canImportBackup)
        assertFalse(state.canRestore)
    }

    @Test
    fun initializationDisablesEveryBackupAndRestoreAction() {
        val state =
            BackupRestoreUiState(
                isInitializing = true,
                hasRestorePoint = true,
            )

        assertFalse(state.canCreateBackup)
        assertFalse(state.canImportBackup)
        assertFalse(state.canRestore)
    }

    @Test
    fun everyNonIdleOperationDisablesEveryAction() {
        BackupRestoreOperation.entries
            .filter { operation -> operation != BackupRestoreOperation.IDLE }
            .forEach { operation ->
                val state =
                    BackupRestoreUiState(
                        operation = operation,
                        hasRestorePoint = true,
                    )

                assertFalse("$operation should block actions", state.actionsEnabled)
                assertFalse("$operation should block create", state.canCreateBackup)
                assertFalse("$operation should block import", state.canImportBackup)
                assertFalse("$operation should block restore", state.canRestore)
            }
    }

    @Test
    fun statusIsPartOfStateForActivityRecreation() {
        val status =
            BackupRestoreStatusUi(
                status = BackupRestoreStatus.BACKUP_CREATED,
            )
        val recreated = BackupRestoreUiState(status = status).copy()

        assertTrue(recreated.status == status)
    }

    @Test
    fun errorStatusRetainsActionableTextAndErrorTone() {
        val status =
            BackupRestoreStatusUi(
                status = BackupRestoreStatus.IMPORT_INVALID_ARCHIVE,
            )

        assertTrue(status.isError)
    }
}
