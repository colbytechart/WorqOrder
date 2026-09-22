package worq.order.backup

import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import worq.order.data.preferences.UuidExportOriginIdGenerator
import worq.order.timer.TimerOperationLock

sealed interface PortableBackupImportStageResult {
    data class Ready(
        val stagedImport: PortableBackupStagedImport,
    ) : PortableBackupImportStageResult

    data object InvalidArchive : PortableBackupImportStageResult

    data object StorageFailure : PortableBackupImportStageResult
}

/** A validated, app-private archive staged before the UI asks for replacement consent. */
class PortableBackupStagedImport internal constructor(
    internal val source: PortableBackupArtifact,
    internal val data: PortableBackupDataV1,
)

sealed interface PortableBackupReplacementResult {
    data object Replaced : PortableBackupReplacementResult

    data object Restored : PortableBackupReplacementResult

    data object RolledBack : PortableBackupReplacementResult

    data object TimerRunning : PortableBackupReplacementResult

    data object InvalidSource : PortableBackupReplacementResult

    data object NoRestorePoint : PortableBackupReplacementResult

    /** An operation journal is retained and startup reconciliation must finish it. */
    data object RecoveryRequired : PortableBackupReplacementResult

    data object StorageFailure : PortableBackupReplacementResult
}

sealed interface PortableBackupStartupRecoveryResult {
    data object NoRecoveryNeeded : PortableBackupStartupRecoveryResult

    data object Recovered : PortableBackupStartupRecoveryResult

    data object Blocked : PortableBackupStartupRecoveryResult
}

/**
 * Runtime effects are deliberately narrow: replacement only cancels local scheduling and
 * notifications, and reconciliation must never perform an export or a remote Google mutation.
 */
interface PortableBackupReplacementRuntime {
    suspend fun resetForReplacement()

    suspend fun reconcileAfterRollback()
}

/**
 * The sole import/restore mutation coordinator. Its archive preflight is intentionally separate
 * from confirmation; every transition after the first durable journal is converged in
 * [NonCancellable] using a next-action journal.
 */
class PortableBackupReplacementCoordinator internal constructor(
    private val operationLock: ApplicationDataOperationLock,
    private val timerOperationLock: TimerOperationLock,
    private val backupCoordinator: PortableBackupCoordinator,
    private val archiveReader: PortableBackupArchiveReader,
    private val archiveWriter: PortableBackupArchiveWriter,
    private val roomReplacement: PortableBackupRoomReplacement,
    private val preferencesReplacement: PortableBackupPreferencesReplacement,
    private val fileStore: PortableBackupRecoveryFileStore,
    private val runtime: PortableBackupReplacementRuntime,
    private val newOperationId: () -> String = { UuidExportOriginIdGenerator.newOriginId() },
    private val newExportOriginId: () -> String = { UuidExportOriginIdGenerator.newOriginId() },
) {
    suspend fun stageImport(input: InputStream): PortableBackupImportStageResult {
        val operationId = newOperationId()
        if (!OPERATION_ID.matches(operationId)) return PortableBackupImportStageResult.StorageFailure
        return when (val staged = fileStore.stageSource(operationId, input, archiveReader)) {
            is PortableBackupStagedSourceResult.Ready ->
                PortableBackupImportStageResult.Ready(
                    PortableBackupStagedImport(staged.artifact, staged.archive.data),
                )
            PortableBackupStagedSourceResult.Invalid -> PortableBackupImportStageResult.InvalidArchive
            PortableBackupStagedSourceResult.StorageFailure ->
                PortableBackupImportStageResult.StorageFailure
        }
    }

    fun discardStagedImport(stagedImport: PortableBackupStagedImport) {
        fileStore.delete(stagedImport.source.name)
    }

    suspend fun import(stagedImport: PortableBackupStagedImport): PortableBackupReplacementResult =
        operationLock.mutex.withLock {
            timerOperationLock.mutex.withLock {
                val source = readData(stagedImport.source) ?: return@withLock PortableBackupReplacementResult.InvalidSource
                startOperation(
                    kind = PortableBackupReplacementKind.IMPORT,
                    source = stagedImport.source,
                    sourceData = source,
                )
            }
        }

    suspend fun restore(): PortableBackupReplacementResult =
        operationLock.mutex.withLock {
            timerOperationLock.mutex.withLock {
                val source = fileStore.restorePointArtifact() ?: return@withLock PortableBackupReplacementResult.NoRestorePoint
                val sourceData = readData(source) ?: return@withLock PortableBackupReplacementResult.NoRestorePoint
                startOperation(
                    kind = PortableBackupReplacementKind.RESTORE,
                    source = source,
                    sourceData = sourceData,
                )
            }
        }

    fun hasRestorePoint(): Boolean =
        fileStore.restorePointArtifact()?.let { point -> readData(point) != null } == true

    /** Called before normal timer/export recovery during each application process start. */
    suspend fun reconcileStartup(): PortableBackupStartupRecoveryResult =
        operationLock.mutex.withLock {
            timerOperationLock.mutex.withLock {
                val journal = fileStore.readJournal()
                if (journal == null) {
                    return@withLock if (fileStore.hasJournalFile()) {
                        PortableBackupStartupRecoveryResult.Blocked
                    } else {
                        PortableBackupStartupRecoveryResult.NoRecoveryNeeded
                    }
                }
                when (converge(journal)) {
                    PortableBackupReplacementResult.Replaced,
                    PortableBackupReplacementResult.Restored,
                    PortableBackupReplacementResult.RolledBack,
                    -> PortableBackupStartupRecoveryResult.Recovered
                    else -> PortableBackupStartupRecoveryResult.Blocked
                }
            }
        }

    private suspend fun startOperation(
        kind: PortableBackupReplacementKind,
        source: PortableBackupArtifact,
        sourceData: PortableBackupDataV1,
    ): PortableBackupReplacementResult {
        if (!fileStore.hasVerifiedArtifact(source) || PortableBackupValidator.validateData(sourceData) !is PortableBackupDataValidationResult.Valid) {
            return PortableBackupReplacementResult.InvalidSource
        }
        val displacedBackup =
            when (val prepared = backupCoordinator.prepareWhileTimerLocked()) {
                is PreparePortableBackupResult.Ready -> prepared.backup
                PreparePortableBackupResult.TimerRunning -> return PortableBackupReplacementResult.TimerRunning
                PreparePortableBackupResult.InvalidLocalState -> return PortableBackupReplacementResult.StorageFailure
            }
        val operationId = newOperationId()
        if (!OPERATION_ID.matches(operationId)) return PortableBackupReplacementResult.StorageFailure
        return startOperationWithPrepared(kind, source, sourceData, operationId, displacedBackup)
    }

    private suspend fun startOperationWithPrepared(
        kind: PortableBackupReplacementKind,
        source: PortableBackupArtifact,
        sourceData: PortableBackupDataV1,
        operationId: String,
        displacedBackup: PreparedPortableBackup,
    ): PortableBackupReplacementResult {
        val displaced =
            fileStore.writeArchive(
                name = portableBackupDisplacedName(operationId),
                backup = displacedBackup,
                writer = archiveWriter,
                reader = archiveReader,
            ) ?: return PortableBackupReplacementResult.StorageFailure
        val localPreferences = preferencesReplacement.captureLocalSnapshot()
        val localPreferencesArtifact =
            fileStore.writeLocalPreferences(
                name = portableBackupLocalPreferencesName(operationId),
                snapshot = localPreferences,
            ) ?: return PortableBackupReplacementResult.StorageFailure
        val existingRestorePoint =
            if (kind == PortableBackupReplacementKind.IMPORT) {
                fileStore.restorePointArtifact()?.takeIf { point -> readData(point) != null }
            } else {
                null
            }
        if (kind == PortableBackupReplacementKind.IMPORT && fileStore.restorePointArtifact() != null && existingRestorePoint == null) {
            return PortableBackupReplacementResult.StorageFailure
        }
        val targetOriginId = newExportOriginId()
        if (!ORIGIN_ID.matches(targetOriginId)) return PortableBackupReplacementResult.StorageFailure
        val preservedDestination =
            if (kind == PortableBackupReplacementKind.IMPORT) {
                localPreferences.defaultExportDestination()
            } else {
                sourceData.settings.defaultExportDestination
            }
        val journal =
            PortableBackupReplacementJournal(
                operationId = operationId,
                kind = kind,
                direction = PortableBackupRecoveryDirection.FORWARD,
                nextAction = PortableBackupReplacementNextAction.PREPARE_ROLLING_POINT,
                sequence = 0,
                source = source,
                displaced = displaced,
                localPreferences = localPreferencesArtifact,
                priorRestore = existingRestorePoint,
                targetOriginId = targetOriginId,
                preservedDefaultDestination = preservedDestination,
            )
        if (!fileStore.writeJournal(journal)) return PortableBackupReplacementResult.StorageFailure

        // From here a journal is durable: caller cancellation cannot leave an ambiguous state.
        return withContext(NonCancellable) { converge(journal) }
    }

    private suspend fun converge(initial: PortableBackupReplacementJournal): PortableBackupReplacementResult {
        var journal = initial
        while (true) {
            try {
                when (journal.direction) {
                    PortableBackupRecoveryDirection.FORWARD ->
                        when (journal.nextAction) {
                            PortableBackupReplacementNextAction.PREPARE_ROLLING_POINT -> {
                                journal = prepareRollingPoint(journal) ?: return PortableBackupReplacementResult.RecoveryRequired
                            }
                            PortableBackupReplacementNextAction.APPLY_TARGET_ROOM -> {
                                val target = readData(journal.source) ?: return PortableBackupReplacementResult.RecoveryRequired
                                roomReplacement.replace(target)
                                journal = advance(journal, PortableBackupReplacementNextAction.APPLY_TARGET_PREFERENCES)
                                    ?: return PortableBackupReplacementResult.RecoveryRequired
                            }
                            PortableBackupReplacementNextAction.APPLY_TARGET_PREFERENCES -> {
                                val target = readData(journal.source) ?: return PortableBackupReplacementResult.RecoveryRequired
                                preferencesReplacement.applyTarget(
                                    data = target,
                                    preservedDefaultDestination = journal.preservedDefaultDestination,
                                    newExportOriginId = journal.targetOriginId,
                                )
                                journal = advance(journal, PortableBackupReplacementNextAction.RESET_TARGET_RUNTIME)
                                    ?: return PortableBackupReplacementResult.RecoveryRequired
                            }
                            PortableBackupReplacementNextAction.RESET_TARGET_RUNTIME -> {
                                runtime.resetForReplacement()
                                if (!matchesTarget(journal)) throw PortableBackupRecoveryException()
                                journal = advance(journal, PortableBackupReplacementNextAction.FINALIZE_SUCCESS)
                                    ?: return PortableBackupReplacementResult.RecoveryRequired
                            }
                            PortableBackupReplacementNextAction.FINALIZE_SUCCESS ->
                                return finalizeSuccess(journal)
                            else -> return PortableBackupReplacementResult.RecoveryRequired
                        }
                    PortableBackupRecoveryDirection.ROLLBACK ->
                        when (journal.nextAction) {
                            PortableBackupReplacementNextAction.APPLY_ROLLBACK_ROOM -> {
                                val displaced = readData(journal.displaced)
                                    ?: return PortableBackupReplacementResult.RecoveryRequired
                                roomReplacement.replace(displaced)
                                journal = advance(journal, PortableBackupReplacementNextAction.APPLY_ROLLBACK_PREFERENCES)
                                    ?: return PortableBackupReplacementResult.RecoveryRequired
                            }
                            PortableBackupReplacementNextAction.APPLY_ROLLBACK_PREFERENCES -> {
                                val preferences = fileStore.readLocalPreferences(journal.localPreferences)
                                    ?: return PortableBackupReplacementResult.RecoveryRequired
                                preferencesReplacement.restore(preferences)
                                journal = advance(journal, PortableBackupReplacementNextAction.RESET_ROLLBACK_RUNTIME)
                                    ?: return PortableBackupReplacementResult.RecoveryRequired
                            }
                            PortableBackupReplacementNextAction.RESET_ROLLBACK_RUNTIME -> {
                                runtime.reconcileAfterRollback()
                                if (!matchesArtifact(journal.displaced)) {
                                    return PortableBackupReplacementResult.RecoveryRequired
                                }
                                journal = advance(journal, PortableBackupReplacementNextAction.RESTORE_PRIOR_ROLLING_POINT)
                                    ?: return PortableBackupReplacementResult.RecoveryRequired
                            }
                            PortableBackupReplacementNextAction.RESTORE_PRIOR_ROLLING_POINT -> {
                                journal = restorePriorPoint(journal) ?: return PortableBackupReplacementResult.RecoveryRequired
                            }
                            PortableBackupReplacementNextAction.FINALIZE_ROLLBACK ->
                                return finalizeRollback(journal)
                            else -> return PortableBackupReplacementResult.RecoveryRequired
                        }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (
                    journal.direction == PortableBackupRecoveryDirection.FORWARD &&
                    journal.nextAction != PortableBackupReplacementNextAction.FINALIZE_SUCCESS
                ) {
                    journal =
                        journal.copy(
                            direction = PortableBackupRecoveryDirection.ROLLBACK,
                            nextAction = PortableBackupReplacementNextAction.APPLY_ROLLBACK_ROOM,
                            sequence = journal.sequence + 1,
                        )
                    if (!fileStore.writeJournal(journal)) return PortableBackupReplacementResult.RecoveryRequired
                } else {
                    return PortableBackupReplacementResult.RecoveryRequired
                }
            }
        }
    }

    private suspend fun prepareRollingPoint(
        journal: PortableBackupReplacementJournal,
    ): PortableBackupReplacementJournal? {
        if (journal.kind == PortableBackupReplacementKind.RESTORE) {
            if (readData(journal.source) == null) return null
            return advance(journal, PortableBackupReplacementNextAction.APPLY_TARGET_ROOM)
        }
        val movedPrior =
            journal.priorRestore?.let { prior ->
                val targetName = portableBackupPriorRestoreName(journal.operationId)
                artifactAtOrPromote(prior, targetName) ?: return null
            }
        val movedDisplaced =
            artifactAtOrPromote(journal.displaced, PORTABLE_BACKUP_RESTORE_POINT_NAME) ?: return null
        return advance(
            journal.copy(priorRestore = movedPrior, displaced = movedDisplaced),
            PortableBackupReplacementNextAction.APPLY_TARGET_ROOM,
        )
    }

    private suspend fun restorePriorPoint(
        journal: PortableBackupReplacementJournal,
    ): PortableBackupReplacementJournal? {
        if (journal.kind == PortableBackupReplacementKind.IMPORT) {
            val restored =
                journal.priorRestore?.let { prior ->
                    artifactAtOrPromote(prior, PORTABLE_BACKUP_RESTORE_POINT_NAME)
                } ?: run {
                    if (!fileStore.delete(PORTABLE_BACKUP_RESTORE_POINT_NAME)) return null
                    null
                }
            if (journal.priorRestore != null && restored == null) return null
        } else if (readData(journal.source) == null) {
            return null
        }
        return advance(journal, PortableBackupReplacementNextAction.FINALIZE_ROLLBACK)
    }

    private suspend fun finalizeSuccess(
        journal: PortableBackupReplacementJournal,
    ): PortableBackupReplacementResult {
        if (journal.kind == PortableBackupReplacementKind.RESTORE) {
            if (artifactAtOrPromote(journal.displaced, PORTABLE_BACKUP_RESTORE_POINT_NAME) == null) {
                return PortableBackupReplacementResult.RecoveryRequired
            }
        } else {
            journal.priorRestore?.let { prior ->
                if (fileStore.hasVerifiedArtifact(prior) && !fileStore.delete(prior.name)) {
                    return PortableBackupReplacementResult.RecoveryRequired
                }
            }
        }
        if (journal.kind == PortableBackupReplacementKind.IMPORT &&
            fileStore.hasVerifiedArtifact(journal.source) && !fileStore.delete(journal.source.name)
        ) return PortableBackupReplacementResult.RecoveryRequired
        if (fileStore.hasVerifiedArtifact(journal.localPreferences) && !fileStore.delete(journal.localPreferences.name)) {
            return PortableBackupReplacementResult.RecoveryRequired
        }
        if (!fileStore.clearJournal()) return PortableBackupReplacementResult.RecoveryRequired
        return if (journal.kind == PortableBackupReplacementKind.IMPORT) {
            PortableBackupReplacementResult.Replaced
        } else {
            PortableBackupReplacementResult.Restored
        }
    }

    private suspend fun finalizeRollback(
        journal: PortableBackupReplacementJournal,
    ): PortableBackupReplacementResult {
        val expectedPreferences = fileStore.readLocalPreferences(journal.localPreferences)
            ?: return PortableBackupReplacementResult.RecoveryRequired
        if (!preferencesReplacement.matches(expectedPreferences)) return PortableBackupReplacementResult.RecoveryRequired
        if (journal.kind == PortableBackupReplacementKind.IMPORT && fileStore.hasVerifiedArtifact(journal.source)) {
            if (!fileStore.delete(journal.source.name)) return PortableBackupReplacementResult.RecoveryRequired
        }
        if (journal.kind == PortableBackupReplacementKind.RESTORE && fileStore.hasVerifiedArtifact(journal.displaced)) {
            if (!fileStore.delete(journal.displaced.name)) return PortableBackupReplacementResult.RecoveryRequired
        }
        if (fileStore.hasVerifiedArtifact(journal.localPreferences) && !fileStore.delete(journal.localPreferences.name)) {
            return PortableBackupReplacementResult.RecoveryRequired
        }
        if (!fileStore.clearJournal()) return PortableBackupReplacementResult.RecoveryRequired
        return PortableBackupReplacementResult.RolledBack
    }

    private suspend fun matchesTarget(journal: PortableBackupReplacementJournal): Boolean {
        val expected = readData(journal.source) ?: return false
        if (
            !preferencesReplacement.matchesTarget(
                data = expected,
                preservedDefaultDestination = journal.preservedDefaultDestination,
                newExportOriginId = journal.targetOriginId,
            )
        ) return false
        val expectedWithDestination =
            expected.copy(
                settings = expected.settings.copy(defaultExportDestination = journal.preservedDefaultDestination),
            )
        val current =
            when (val prepared = backupCoordinator.prepareWhileTimerLocked()) {
                is PreparePortableBackupResult.Ready -> prepared.backup.data
                else -> return false
            }
        return current == expectedWithDestination
    }

    private suspend fun matchesArtifact(artifact: PortableBackupArtifact): Boolean {
        val expected = readData(artifact) ?: return false
        val current =
            when (val prepared = backupCoordinator.prepareWhileTimerLocked()) {
                is PreparePortableBackupResult.Ready -> prepared.backup.data
                else -> return false
            }
        return current == expected
    }

    private fun readData(artifact: PortableBackupArtifact): PortableBackupDataV1? =
        (fileStore.readArchive(artifact, archiveReader) as? PortableBackupArchiveReadResult.Ready)?.data

    private fun artifactAtOrPromote(
        source: PortableBackupArtifact,
        targetName: String,
    ): PortableBackupArtifact? {
        val target =
            source.copy(name = targetName).takeIf(fileStore::hasVerifiedArtifact)
        return target ?: fileStore.promote(source, targetName)
    }

    private suspend fun advance(
        journal: PortableBackupReplacementJournal,
        nextAction: PortableBackupReplacementNextAction,
    ): PortableBackupReplacementJournal? {
        val next = journal.copy(nextAction = nextAction, sequence = journal.sequence + 1)
        return if (fileStore.writeJournal(next)) next else null
    }

    private fun PortableBackupLocalPreferencesSnapshot.defaultExportDestination(): String =
        entries
            .firstOrNull { entry -> entry.name == "default_export_destination" }
            ?.value
            ?.takeIf(EXPORT_DESTINATIONS::contains)
            ?: "CSV"

    private class PortableBackupRecoveryException : IllegalStateException()

    private companion object {
        val OPERATION_ID = Regex("^[a-z0-9]{32}$")
        val ORIGIN_ID = Regex("^[a-z0-9]{32}$")
        val EXPORT_DESTINATIONS = setOf("CSV", "XLSX", "GOOGLE_SHEETS")
    }
}
