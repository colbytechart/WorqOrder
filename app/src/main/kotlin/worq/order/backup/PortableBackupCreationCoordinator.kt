package worq.order.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock

/**
 * Typed, UI-neutral state emitted by the eventual Settings action. The writer intentionally
 * reports phases rather than byte-level progress because a SAF provider may not expose a stable
 * total write size while Deflate compression is in progress.
 */
sealed interface PortableBackupCreationProgress {
    data object PreparingSnapshot : PortableBackupCreationProgress

    data object WritingArchive : PortableBackupCreationProgress
}

sealed interface PortableBackupCreationResult {
    data class Success(
        val suggestedFileName: String,
        val compressedByteCount: Long,
        val expandedDataByteCount: Long,
        val dataSha256: String,
    ) : PortableBackupCreationResult

    data object TimerRunning : PortableBackupCreationResult

    data object InvalidLocalState : PortableBackupCreationResult

    data class OutputFailed(
        val reason: PortableBackupArchiveWriteFailure,
        val partialDocumentMayRemain: Boolean,
    ) : PortableBackupCreationResult
}

/**
 * The non-UI Settings action boundary for backup creation. M51 deliberately leaves document
 * selection and screen state to M53; callers provide the URI returned from ACTION_CREATE_DOCUMENT.
 */
class PortableBackupCreationCoordinator(
    private val backupCoordinator: PortableBackupCoordinator,
    private val outputDestination: PortableBackupDocumentOutputDestination,
    private val operationLock: ApplicationDataOperationLock? = null,
) {
    suspend fun create(
        documentUri: String,
        onProgress: (PortableBackupCreationProgress) -> Unit = {},
    ): PortableBackupCreationResult =
        operationLock?.mutex?.withLock {
            createWhileOperationLocked(documentUri, onProgress)
        } ?: createWhileOperationLocked(documentUri, onProgress)

    fun suggestedFileName(): String = backupCoordinator.suggestedFileName()

    private suspend fun createWhileOperationLocked(
        documentUri: String,
        onProgress: (PortableBackupCreationProgress) -> Unit,
    ): PortableBackupCreationResult {
        onProgress(PortableBackupCreationProgress.PreparingSnapshot)
        val backup =
            when (val prepared = backupCoordinator.prepare()) {
                is PreparePortableBackupResult.Ready -> prepared.backup
                PreparePortableBackupResult.TimerRunning -> return PortableBackupCreationResult.TimerRunning
                PreparePortableBackupResult.InvalidLocalState ->
                    return PortableBackupCreationResult.InvalidLocalState
            }

        onProgress(PortableBackupCreationProgress.WritingArchive)
        return try {
            when (val result = outputDestination.write(documentUri, backup)) {
                is PortableBackupDocumentWriteResult.Success ->
                    PortableBackupCreationResult.Success(
                        suggestedFileName = backup.suggestedFileName,
                        compressedByteCount = result.archive.compressedByteCount,
                        expandedDataByteCount = result.archive.expandedDataByteCount,
                        dataSha256 = result.archive.dataSha256,
                    )
                is PortableBackupDocumentWriteResult.Failed ->
                    PortableBackupCreationResult.OutputFailed(
                        reason = result.reason,
                        partialDocumentMayRemain = result.partialDocumentMayRemain,
                    )
            }
        } catch (error: CancellationException) {
            throw error
        }
    }
}
