package worq.order.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Milestone 54A's archive-facing compatibility matrix. Room 1-7 upgrade coverage remains in the
 * connected migration tests; this class proves that the complete logical state they feed into the
 * portability boundary survives the actual Deflate writer/reader and that hostile containers fail
 * closed before replacement can be considered.
 */
class PortableBackupMilestone54FixtureMatrixTest {
    @Test
    fun completeArchivedUnicodeSelectionAndExportHistoryGraphRoundTripsThroughArchive() = runTest {
        val data = completeData()
        val archive = writeArchive(data)

        val result = PortableBackupArchiveReader().read(ByteArrayInputStream(archive))

        assertTrue(result is PortableBackupArchiveReadResult.Ready)
        val ready = result as PortableBackupArchiveReadResult.Ready
        assertEquals(data, ready.data)
        assertEquals("Café Client", ready.data.clients.first().name)
        assertEquals(false, ready.data.clients.last().isActive)
        assertEquals("東京 inspection 🚀", ready.data.tags.single().text)
        assertEquals("GOOGLE_SHEETS", ready.data.settings.defaultExportDestination)
        assertEquals("task-complete", ready.data.selection?.taskId)
        assertEquals("SUCCESS", ready.data.settings.lastExportAttempt?.outcome)
        assertEquals(1, ready.data.tasks.single().tagSnapshots.size)
        assertEquals(1, ready.data.tasks.single().intervals.size)
    }

    @Test
    fun malformedAndAdversarialArchivesFailClosedWithTypedReasons() = runTest {
        val validDataResult =
            PortableBackupJsonCodec.decodeData(
                PortableBackupArchiveFixtures.validData.toString(Charsets.UTF_8),
            )
        val validData = (validDataResult as PortableBackupDecodeResult.Success).value
        val validArchive = writeArchive(validData)
        val validEntries = PortableBackupArchiveFixtures.validEntries()
        val cases =
            listOf(
                Triple(
                    "truncated",
                    validArchive.copyOf(validArchive.size / 2),
                    PortableBackupArchiveReadFailure.IO,
                ),
                Triple(
                    "unknown-entry",
                    PortableBackupArchiveFixtures.zip(
                        validEntries +
                            PortableBackupArchiveEntryFixture(
                                "extra.json",
                                "{}".toByteArray(),
                            ),
                    ),
                    PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE,
                ),
                Triple(
                    "reordered-entry",
                    PortableBackupArchiveFixtures.zip(
                        listOf(
                            PortableBackupArchiveEntryFixture(PORTABLE_BACKUP_DATA_ENTRY, PortableBackupArchiveFixtures.validData),
                            PortableBackupArchiveEntryFixture(PORTABLE_BACKUP_MANIFEST_ENTRY, PortableBackupArchiveFixtures.validManifest),
                        ),
                    ),
                    PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE,
                ),
                Triple(
                    "invalid-utf8",
                    PortableBackupArchiveFixtures.zip(
                        listOf(
                            PortableBackupArchiveEntryFixture(PORTABLE_BACKUP_MANIFEST_ENTRY, byteArrayOf(0xc3.toByte(), 0x28)),
                            PortableBackupArchiveEntryFixture(PORTABLE_BACKUP_DATA_ENTRY, PortableBackupArchiveFixtures.validData),
                        ),
                    ),
                    PortableBackupArchiveReadFailure.INVALID_UTF8,
                ),
            )

        cases.forEach { (name, bytes, expectedReason) ->
            val result = PortableBackupArchiveReader().read(ByteArrayInputStream(bytes))
            assertTrue("$name should be rejected", result is PortableBackupArchiveReadResult.Invalid)
            val reason = (result as PortableBackupArchiveReadResult.Invalid).reason
            assertEquals("$name returned unexpected reason", expectedReason, reason)
        }
    }

    @Test
    fun unsupportedOlderAndFutureFormatVersionsFailClosedWithoutGuessing() = runTest {
        val data = PortableBackupArchiveFixtures.validData
        val currentManifest = PortableBackupArchiveFixtures.validManifest
        val oldManifest = PortableBackupJsonCodec
            .decodeManifest(currentManifest.toString(Charsets.UTF_8))
            .let { (it as PortableBackupDecodeResult.Success).value }
            .copy(backupFormatVersion = 0)
        val futureManifest =
            PortableBackupJsonCodec
                .decodeManifest(currentManifest.toString(Charsets.UTF_8))
                .let { (it as PortableBackupDecodeResult.Success).value }
                .copy(backupFormatVersion = PORTABLE_BACKUP_FORMAT_VERSION + 1)

        listOf(oldManifest, futureManifest).forEach { manifest ->
            val archive =
                PortableBackupArchiveFixtures.zip(
                    listOf(
                        PortableBackupArchiveEntryFixture(
                            PORTABLE_BACKUP_MANIFEST_ENTRY,
                            PortableBackupJsonCodec.encodeManifest(manifest).toByteArray(),
                        ),
                        PortableBackupArchiveEntryFixture(PORTABLE_BACKUP_DATA_ENTRY, data),
                    ),
                )
            assertEquals(
                PortableBackupArchiveReadFailure.UNSUPPORTED_VERSION,
                (PortableBackupArchiveReader().read(ByteArrayInputStream(archive)) as PortableBackupArchiveReadResult.Invalid).reason,
            )
        }
    }

    @Test
    fun maximalUnicodeAndApprovedLengthBoundariesRemainValidButOverLimitsFailValidation() {
        val base = completeData()
        // The 999-code-point boundary includes export punctuation. Supplying the period here
        // prevents TaskTextComposer from producing a 1,000th code point.
        val maxText =
            base.tasks.single().copy(
                description = "x".repeat(998) + ".",
                tagSnapshots = emptyList(),
            )
        val maxTag = base.tags.single().copy(text = "t".repeat(400), normalizedText = "t".repeat(400))
        assertTrue(PortableBackupValidator.validateData(base.copy(tasks = listOf(maxText), tags = listOf(maxTag))) is PortableBackupDataValidationResult.Valid)

        val overText = maxText.copy(description = "x".repeat(999) + ".")
        val overTag = maxTag.copy(text = "t".repeat(401), normalizedText = "t".repeat(401))
        assertTrue(PortableBackupValidator.validateData(base.copy(tasks = listOf(overText))) is PortableBackupDataValidationResult.Invalid)
        assertTrue(PortableBackupValidator.validateData(base.copy(tags = listOf(overTag))) is PortableBackupDataValidationResult.Invalid)
    }

    private suspend fun writeArchive(data: PortableBackupDataV1): ByteArray {
        val output = ByteArrayOutputStream()
        val result =
            PortableBackupArchiveWriter().write(
                PreparedPortableBackup(
                    data = data,
                    createdAt = Instant.parse("2026-09-20T15:00:00Z"),
                    producer = PortableBackupProducer("0.6.0", 6),
                    suggestedFileName = "WorqOrder_Backup_2026-09-20_150000.zip",
                ),
                output,
            )
        assertTrue(result is PortableBackupArchiveWriteResult.Success)
        return output.toByteArray()
    }

    private fun completeData(): PortableBackupDataV1 =
        PortableBackupDataV1(
            dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
            clients =
                listOf(
                    PortableBackupClientV1("client-active", "Café Client", "café client", true, 1, 2, null),
                    PortableBackupClientV1("client-archived", "Historical Client", "historical client", false, 1, 2, 3),
                ),
            consultants =
                listOf(
                    PortableBackupConsultantV1("consultant-active", "Alex Rivera", "alex rivera", true, 1, 2, null),
                    PortableBackupConsultantV1("consultant-archived", "Former Consultant", "former consultant", false, 1, 2, 3),
                ),
            tags = listOf(PortableBackupTagV1("tag-description", "DESCRIPTION", "東京 inspection 🚀", "東京 inspection 🚀", 1, 2)),
            tasks =
                listOf(
                    PortableBackupTaskV1(
                        id = "task-complete",
                        seriesId = "series-complete",
                        clientId = "client-active",
                        consultantId = "consultant-active",
                        consultantNameSnapshot = "Alex Rivera",
                        description = "Inspect network",
                        hardwareSoftwarePurchases = "Replacement cable",
                        workType = "ON_SITE",
                        billingStatus = "BILLABLE",
                        mileage = "12.5",
                        notes = "Café notes 東京 🚀",
                        workDateEpochDay = 18990,
                        zoneId = "America/New_York",
                        createdAtEpochMs = 1,
                        updatedAtEpochMs = 2,
                        tagSnapshots = listOf(PortableBackupTaskTagSnapshotV1("snapshot-description", "DESCRIPTION", "東京 inspection 🚀", "tag-description", 0, 1)),
                        intervals = listOf(PortableBackupIntervalV1("interval-complete", "task-complete", 1000, 2000, false, 1, 2)),
                    ),
                ),
            settings =
                PortableBackupSettingsV1(
                    themeMode = "DARK",
                    timeZoneMode = "MANUAL",
                    manualZoneId = "America/New_York",
                    defaultExportDestination = "GOOGLE_SHEETS",
                    lastExportAttempt = PortableBackupLastExportAttemptV1("GOOGLE_SHEETS", 18990, 3, "SUCCESS", null),
                    selectedConsultantId = "consultant-active",
                    landscapeHandedness = "RIGHT_HANDED",
                ),
            selection = PortableBackupSelectionV1("task-complete", "series-complete", 18990, "America/New_York"),
        )
}
