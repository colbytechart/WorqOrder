package worq.order.ui.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import worq.order.backup.PortableBackupCreationCoordinator
import worq.order.backup.PortableBackupCreationResult
import worq.order.backup.PortableBackupImportDocumentStager
import worq.order.backup.PortableBackupImportStageResult
import worq.order.backup.PortableBackupReplacementCoordinator
import worq.order.backup.PortableBackupReplacementResult
import worq.order.backup.PortableBackupStagedImport

/** Testable UI boundary around the already-audited backup coordinators. */
internal interface BackupRestoreWorkflow {
    fun suggestedFileName(): String

    suspend fun create(documentUri: String): PortableBackupCreationResult

    suspend fun stageImport(documentUri: String): PortableBackupImportStageResult

    suspend fun import(stagedImport: PortableBackupStagedImport): PortableBackupReplacementResult

    suspend fun discardStagedImport(stagedImport: PortableBackupStagedImport)

    suspend fun restore(): PortableBackupReplacementResult

    suspend fun hasRestorePoint(): Boolean
}

/** Keeps file validation/replacement work off the main thread without moving policy into the UI. */
internal class CoordinatorBackupRestoreWorkflow(
    private val creationCoordinator: PortableBackupCreationCoordinator,
    private val importDocumentStager: PortableBackupImportDocumentStager,
    private val replacementCoordinator: PortableBackupReplacementCoordinator,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupRestoreWorkflow {
    override fun suggestedFileName(): String = creationCoordinator.suggestedFileName()

    override suspend fun create(documentUri: String): PortableBackupCreationResult =
        withContext(workDispatcher) { creationCoordinator.create(documentUri) }

    override suspend fun stageImport(documentUri: String): PortableBackupImportStageResult =
        withContext(workDispatcher) { importDocumentStager.stage(documentUri) }

    override suspend fun import(
        stagedImport: PortableBackupStagedImport,
    ): PortableBackupReplacementResult =
        withContext(workDispatcher) { replacementCoordinator.import(stagedImport) }

    override suspend fun discardStagedImport(stagedImport: PortableBackupStagedImport) {
        withContext(workDispatcher) {
            replacementCoordinator.discardStagedImport(stagedImport)
        }
    }

    override suspend fun restore(): PortableBackupReplacementResult =
        withContext(workDispatcher) { replacementCoordinator.restore() }

    override suspend fun hasRestorePoint(): Boolean =
        withContext(workDispatcher) { replacementCoordinator.hasRestorePoint() }
}
