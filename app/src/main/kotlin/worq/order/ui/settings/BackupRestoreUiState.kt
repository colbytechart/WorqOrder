package worq.order.ui.settings

import worq.order.data.BackupRestoreStatus

/**
 * Operations exposed by the eventual Backup & Restore Settings section.
 *
 * The state is intentionally UI-neutral. The ViewModel owns transitions and the replacement
 * coordinator remains the only authority for importing/restoring data.
 */
enum class BackupRestoreOperation {
    IDLE,
    CHOOSING_BACKUP_DESTINATION,
    CHOOSING_IMPORT_SOURCE,
    CREATING_BACKUP,
    STAGING_IMPORT,
    AWAITING_IMPORT_CONFIRMATION,
    IMPORTING,
    AWAITING_RESTORE_CONFIRMATION,
    RESTORING,
}

data class BackupRestoreStatusUi(
    val status: BackupRestoreStatus,
) {
    val isError: Boolean
        get() = !status.isSuccess
}

enum class BackupRestoreConfirmation {
    IMPORT,
    RESTORE,
}

data class BackupRestoreUiState(
    val isInitializing: Boolean = false,
    val isTimerRunning: Boolean = false,
    val operation: BackupRestoreOperation = BackupRestoreOperation.IDLE,
    val hasRestorePoint: Boolean = false,
    val status: BackupRestoreStatusUi? = null,
) {
    val isBusy: Boolean
        get() = operation != BackupRestoreOperation.IDLE

    /**
     * Import and Restore replace Room plus portable DataStore state. The Settings route must stay
     * modal for this short phase so unrelated UI writes or navigation cannot race the recovery
     * protocol. Backup creation and source validation remain non-destructive and need no barrier.
     */
    val isReplacingData: Boolean
        get() =
            operation == BackupRestoreOperation.IMPORTING ||
                operation == BackupRestoreOperation.RESTORING

    /** Create, Import, and Restore must all be blocked while timing or during another operation. */
    val actionsEnabled: Boolean
        get() = !isInitializing && !isTimerRunning && !isBusy

    val canCreateBackup: Boolean
        get() = actionsEnabled

    val canImportBackup: Boolean
        get() = actionsEnabled

    val canRestore: Boolean
        get() = actionsEnabled && hasRestorePoint

    val confirmation: BackupRestoreConfirmation?
        get() =
            when (operation) {
                BackupRestoreOperation.AWAITING_IMPORT_CONFIRMATION ->
                    BackupRestoreConfirmation.IMPORT
                BackupRestoreOperation.AWAITING_RESTORE_CONFIRMATION ->
                    BackupRestoreConfirmation.RESTORE
                else -> null
            }
}
