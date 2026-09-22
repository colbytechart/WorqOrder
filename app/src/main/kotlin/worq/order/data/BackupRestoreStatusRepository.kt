package worq.order.data

import kotlinx.coroutines.flow.Flow

/**
 * A small, installation-local status record for the Settings Backup & Restore section.
 *
 * It is intentionally excluded from the portable DTO. Replacement clears Preferences as part of
 * its target generation; the ViewModel writes the result of that replacement only afterwards.
 */
enum class BackupRestoreStatus {
    BACKUP_CREATED,
    BACKUP_TIMER_RUNNING,
    BACKUP_INVALID_LOCAL_STATE,
    BACKUP_OUTPUT_FAILED,
    BACKUP_OUTPUT_PARTIAL,
    IMPORT_INVALID_ARCHIVE,
    IMPORT_STORAGE_FAILURE,
    IMPORT_TIMER_RUNNING,
    IMPORT_SUCCEEDED,
    IMPORT_FAILED,
    RESTORE_UNAVAILABLE,
    RESTORE_TIMER_RUNNING,
    RESTORE_SUCCEEDED,
    RESTORE_FAILED,
    RECOVERY_REQUIRED,
    ;

    val isSuccess: Boolean
        get() = this == BACKUP_CREATED || this == IMPORT_SUCCEEDED || this == RESTORE_SUCCEEDED
}

interface BackupRestoreStatusRepository {
    fun observeStatus(): Flow<BackupRestoreStatus?>

    suspend fun setStatus(status: BackupRestoreStatus)

    suspend fun clearStatus()
}
