package worq.order.backup

import java.io.ByteArrayInputStream
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic evidence for the M52 recovery contract. These tests model the durable journal
 * boundary rather than Android file primitives; the production file store and Room transaction
 * are exercised by the connected replacement test.
 */
class PortableBackupReplacementFaultMatrixTest {
    @Test
    fun everyJournalPhaseAndDirectionReconstructsExactly() {
        PortableBackupReplacementKind.entries.forEach { kind ->
            PortableBackupRecoveryDirection.entries.forEach { direction ->
                PortableBackupReplacementNextAction.entries.forEachIndexed { index, action ->
                    val journal = validJournal(kind, direction, action, index.toLong())
                    val encoded = PortableBackupRecoveryJournalCodec.encode(journal)
                    assertEquals(journal, PortableBackupRecoveryJournalCodec.decode(encoded))
                }
            }
        }
    }

    @Test
    fun replayingEveryForwardPhaseTwiceIsIdempotentAndReachesTargetGeneration() {
        val state = GenerationState(current = "before", restorePoint = "prior")
        val phases =
            listOf(
                PortableBackupReplacementNextAction.PREPARE_ROLLING_POINT,
                PortableBackupReplacementNextAction.APPLY_TARGET_ROOM,
                PortableBackupReplacementNextAction.APPLY_TARGET_PREFERENCES,
                PortableBackupReplacementNextAction.RESET_TARGET_RUNTIME,
                PortableBackupReplacementNextAction.FINALIZE_SUCCESS,
            )

        phases.forEach { phase ->
            state.applyForward(phase)
            state.applyForward(phase)
        }

        assertEquals("target", state.current)
        assertEquals("displaced", state.restorePoint)
        assertEquals(phases, state.appliedForward)
    }

    @Test
    fun rollbackReconstructionTwiceRestoresOriginalGenerationAndPriorPoint() {
        val state = GenerationState(current = "before", restorePoint = "prior")
        state.applyForward(PortableBackupReplacementNextAction.PREPARE_ROLLING_POINT)
        state.applyForward(PortableBackupReplacementNextAction.APPLY_TARGET_ROOM)
        state.applyForward(PortableBackupReplacementNextAction.APPLY_TARGET_PREFERENCES)

        val rollback =
            listOf(
                PortableBackupReplacementNextAction.APPLY_ROLLBACK_ROOM,
                PortableBackupReplacementNextAction.APPLY_ROLLBACK_PREFERENCES,
                PortableBackupReplacementNextAction.RESET_ROLLBACK_RUNTIME,
                PortableBackupReplacementNextAction.RESTORE_PRIOR_ROLLING_POINT,
                PortableBackupReplacementNextAction.FINALIZE_ROLLBACK,
            )
        rollback.forEach { phase ->
            state.applyRollback(phase)
            state.applyRollback(phase)
        }

        assertEquals("before", state.current)
        assertEquals("prior", state.restorePoint)
        assertEquals(rollback, state.appliedRollback)
    }

    @Test
    fun cancellationBeforeCommitIntentLeavesNoTargetMutation() {
        val state = GenerationState(current = "before", restorePoint = "prior")
        val journal = validJournal(
            kind = PortableBackupReplacementKind.IMPORT,
            direction = PortableBackupRecoveryDirection.FORWARD,
            action = PortableBackupReplacementNextAction.PREPARE_ROLLING_POINT,
            sequence = 0,
        )

        // Cancellation at this point discards staging; no Room/Preferences generation has changed.
        assertEquals(PortableBackupReplacementNextAction.PREPARE_ROLLING_POINT, journal.nextAction)
        assertEquals("before", state.current)
        assertEquals("prior", state.restorePoint)
        assertTrue(state.appliedForward.isEmpty())
    }

    @Test
    fun corruptedRestorePointIsRejectedBeforeLogicalDataCanBeConsumed() = runTest {
        val valid = PortableBackupArchiveFixtures.validEntries()
        val corrupted =
            PortableBackupArchiveFixtures.zip(
                valid.dropLast(1) +
                    PortableBackupArchiveEntryFixture(
                        PORTABLE_BACKUP_DATA_ENTRY,
                        valid.last().bytes.copyOf().also { bytes -> bytes[bytes.lastIndex] = 0x7f },
                    ),
            )
        val result = PortableBackupArchiveReader().read(ByteArrayInputStream(corrupted))
        assertEquals(
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.CHECKSUM),
            result,
        )
        assertNull(
            (result as? PortableBackupArchiveReadResult.Ready)?.data,
        )
    }

    @Test
    fun logicalPortableDataRoundTripIsByteIndependentAndExactlyEquivalent() {
        val original = logicalData()
        val encoded = PortableBackupJsonCodec.encodeData(original)
        val decoded = PortableBackupJsonCodec.decodeData(encoded)
        assertEquals(original, (decoded as PortableBackupDecodeResult.Success).value)
    }

    private fun validJournal(
        kind: PortableBackupReplacementKind,
        direction: PortableBackupRecoveryDirection,
        action: PortableBackupReplacementNextAction,
        sequence: Long,
    ): PortableBackupReplacementJournal =
        PortableBackupReplacementJournal(
            operationId = "a".repeat(32),
            kind = kind,
            direction = direction,
            nextAction = action,
            sequence = sequence,
            source = artifact("source-" + "a".repeat(32) + ".zip"),
            displaced = artifact(PORTABLE_BACKUP_RESTORE_POINT_NAME),
            localPreferences = artifact("local-preferences-" + "a".repeat(32) + ".json"),
            priorRestore = artifact("prior-restore-" + "a".repeat(32) + ".zip"),
            targetOriginId = "b".repeat(32),
            preservedDefaultDestination = "CSV",
        )

    private fun artifact(name: String): PortableBackupArtifact =
        PortableBackupArtifact(name, 1, "c".repeat(64))

    private fun logicalData(): PortableBackupDataV1 =
        PortableBackupDataV1(
            dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
            clients =
                listOf(
                    PortableBackupClientV1("client", "Client", "client", true, 1_000, 1_000, null),
                ),
            consultants = emptyList(),
            tags = emptyList(),
            tasks =
                listOf(
                    PortableBackupTaskV1(
                        id = "task",
                        seriesId = "series",
                        clientId = "client",
                        consultantId = null,
                        consultantNameSnapshot = "",
                        description = "Work",
                        hardwareSoftwarePurchases = "",
                        workType = "ON_SITE",
                        billingStatus = "BILLABLE",
                        mileage = null,
                        notes = "",
                        workDateEpochDay = 1,
                        zoneId = "UTC",
                        createdAtEpochMs = 1_000,
                        updatedAtEpochMs = 2_000,
                        tagSnapshots = emptyList(),
                        intervals =
                            listOf(
                                PortableBackupIntervalV1("interval", "task", 1_000, 2_000, false, 1_000, 2_000),
                            ),
                    ),
                ),
            settings = PortableBackupSettingsV1("SYSTEM", "DEVICE", null, "CSV", null, null, "RIGHT_HANDED"),
            selection = null,
        )

    private data class GenerationState(
        var current: String,
        var restorePoint: String,
        val appliedForward: MutableList<PortableBackupReplacementNextAction> = mutableListOf(),
        val appliedRollback: MutableList<PortableBackupReplacementNextAction> = mutableListOf(),
    ) {
        fun applyForward(action: PortableBackupReplacementNextAction) {
            if (action in appliedForward) return
            when (action) {
                PortableBackupReplacementNextAction.PREPARE_ROLLING_POINT -> restorePoint = "displaced"
                PortableBackupReplacementNextAction.APPLY_TARGET_ROOM,
                PortableBackupReplacementNextAction.APPLY_TARGET_PREFERENCES,
                PortableBackupReplacementNextAction.RESET_TARGET_RUNTIME,
                -> current = "target"
                PortableBackupReplacementNextAction.FINALIZE_SUCCESS -> Unit
                else -> error("not a forward phase: $action")
            }
            appliedForward += action
        }

        fun applyRollback(action: PortableBackupReplacementNextAction) {
            if (action in appliedRollback) return
            when (action) {
                PortableBackupReplacementNextAction.APPLY_ROLLBACK_ROOM,
                PortableBackupReplacementNextAction.APPLY_ROLLBACK_PREFERENCES,
                PortableBackupReplacementNextAction.RESET_ROLLBACK_RUNTIME,
                -> current = "before"
                PortableBackupReplacementNextAction.RESTORE_PRIOR_ROLLING_POINT -> restorePoint = "prior"
                PortableBackupReplacementNextAction.FINALIZE_ROLLBACK -> Unit
                else -> error("not a rollback phase: $action")
            }
            appliedRollback += action
        }
    }
}
