package worq.order.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortableBackupRecoveryJournalTest {
    @Test
    fun journalRoundTripIsStrictAndPreservesTheNextAction() {
        val journal = validJournal()
        val encoded = PortableBackupRecoveryJournalCodec.encode(journal)

        assertEquals(
            journal,
            PortableBackupRecoveryJournalCodec.decode(encoded),
        )
        assertNull(
            PortableBackupRecoveryJournalCodec.decode(
                encoded.replaceFirst("APPLY_TARGET_ROOM", "APPLY_TARGET_PREFERENCES"),
            ),
        )
        assertNull(
            PortableBackupRecoveryJournalCodec.decode(
                """{"schemaVersion":1,"unknown":true}""",
            ),
        )
    }

    @Test
    fun localPreferenceSnapshotRoundTripPreservesTypedValues() {
        val snapshot =
            PortableBackupLocalPreferencesSnapshot(
                listOf(
                    PortableBackupLocalPreferenceEntry(
                        "default_export_destination",
                        PortableBackupLocalPreferenceType.STRING,
                        "GOOGLE_SHEETS",
                    ),
                    PortableBackupLocalPreferenceEntry(
                        "automatic_google_export_enabled",
                        PortableBackupLocalPreferenceType.BOOLEAN,
                        "true",
                    ),
                    PortableBackupLocalPreferenceEntry(
                        "last_export_work_date_epoch_day",
                        PortableBackupLocalPreferenceType.LONG,
                        "20600",
                    ),
                ),
            )

        assertEquals(
            snapshot.entries.sortedBy(PortableBackupLocalPreferenceEntry::name),
            PortableBackupRecoveryJournalCodec.decodeLocalPreferences(
                PortableBackupRecoveryJournalCodec.encodeLocalPreferences(snapshot),
            )?.entries,
        )
        assertNull(
            PortableBackupRecoveryJournalCodec.decodeLocalPreferences(
                """{"schemaVersion":1,"entries":[{"name":"flag","type":"BOOLEAN","value":"not-boolean"}]}""",
            ),
        )
    }

    private fun validJournal(): PortableBackupReplacementJournal =
        PortableBackupReplacementJournal(
            operationId = "a".repeat(32),
            kind = PortableBackupReplacementKind.IMPORT,
            direction = PortableBackupRecoveryDirection.FORWARD,
            nextAction = PortableBackupReplacementNextAction.APPLY_TARGET_ROOM,
            sequence = 2,
            source = artifact("source-" + "a".repeat(32) + ".zip"),
            displaced = artifact(PORTABLE_BACKUP_RESTORE_POINT_NAME),
            localPreferences = artifact("local-preferences-" + "a".repeat(32) + ".json"),
            priorRestore = null,
            targetOriginId = "b".repeat(32),
            preservedDefaultDestination = "CSV",
        )

    private fun artifact(name: String): PortableBackupArtifact =
        PortableBackupArtifact(name, 1, "c".repeat(64))
}
