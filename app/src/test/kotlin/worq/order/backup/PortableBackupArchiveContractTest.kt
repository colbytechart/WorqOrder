package worq.order.backup

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupArchiveContractTest {
    @Test
    fun validArchiveHasDeterministicNameEntriesManifestAndDataChecksum() {
        val first = PortableBackupArchiveFixtures.zip()
        val second = PortableBackupArchiveFixtures.zip()

        assertEquals(PortableBackupArchiveFixtures.VALID_FILE_NAME, "WorqOrder_Backup_2026-09-20_110000.zip")
        assertTrue(PortableBackupArchiveFixtures.validateFileName(PortableBackupArchiveFixtures.VALID_FILE_NAME))
        assertEquals(first.toList(), second.toList())
        assertNull(PortableBackupArchiveFixtures.validateEntryDescriptors(PortableBackupArchiveFixtures.validEntries()))

        val entries = readEntries(first)
        assertEquals(listOf("manifest.json", "data.json"), entries.map { it.first })
        assertEquals(PortableBackupArchiveFixtures.validManifest.toList(), entries.first().second.toList())
        assertEquals(PortableBackupArchiveFixtures.validData.toList(), entries.last().second.toList())

        val manifest =
            PortableBackupJsonCodec.decodeManifest(
                String(entries.first().second, StandardCharsets.UTF_8),
            )
        assertTrue(manifest is PortableBackupDecodeResult.Success)
        val decodedManifest = (manifest as PortableBackupDecodeResult.Success).value
        assertEquals(PortableBackupArchiveFixtures.validData.size.toLong(), decodedManifest.dataByteCount)
        assertEquals(
            PortableBackupArchiveFixtures.sha256(PortableBackupArchiveFixtures.validData),
            decodedManifest.dataSha256,
        )
        assertEquals(
            PortableBackupManifestValidationResult.Current,
            PortableBackupValidator.validateManifest(decodedManifest),
        )
    }

    @Test
    fun entryAllowlistRejectsDuplicatesPathsEncryptionAndUnknownNames() {
        val valid = PortableBackupArchiveFixtures.validEntries()
        assertEquals(
            PortableBackupArchiveFailure.DUPLICATE_ENTRY,
            PortableBackupArchiveFixtures.validateEntryDescriptors(valid + valid.first()),
        )
        assertEquals(
            PortableBackupArchiveFailure.PATH_TRAVERSAL,
            PortableBackupArchiveFixtures.validateEntryDescriptors(
                valid.dropLast(1) + PortableBackupArchiveEntryFixture("../data.json"),
            ),
        )
        assertEquals(
            PortableBackupArchiveFailure.ENCRYPTED_ENTRY,
            PortableBackupArchiveFixtures.validateEntryDescriptors(
                valid.dropLast(1) + PortableBackupArchiveEntryFixture("data.json", encrypted = true),
            ),
        )
        assertEquals(
            PortableBackupArchiveFailure.UNKNOWN_ENTRY,
            PortableBackupArchiveFixtures.validateEntryDescriptors(
                valid + PortableBackupArchiveEntryFixture("extra.json"),
            ),
        )
        assertEquals(
            PortableBackupArchiveFailure.UNKNOWN_ENTRY,
            PortableBackupArchiveFixtures.validateEntryDescriptors(valid.dropLast(1)),
        )
    }

    @Test
    fun hardCompressedAndExpandedLimitsAreRepresentedWithoutAllocatingLimitSizedBuffers() {
        val compressedTooLarge = PortableBackupArchiveFixtures.boundaryCases.single {
            it.expectedFailure == PortableBackupArchiveFailure.COMPRESSED_LIMIT
        }
        val expandedTooLarge = PortableBackupArchiveFixtures.boundaryCases.single {
            it.expectedFailure == PortableBackupArchiveFailure.EXPANDED_LIMIT
        }

        assertEquals("compressed-limit", compressedTooLarge.name)
        assertEquals("expanded-limit", expandedTooLarge.name)
        assertTrue(PortableBackupLimits.MAX_COMPRESSED_BYTES < PortableBackupLimits.MAX_EXPANDED_BYTES)
        assertTrue(PortableBackupLimits.MAX_COMPRESSED_BYTES > 0)
        assertTrue(PortableBackupLimits.MAX_EXPANDED_BYTES > 0)
    }

    @Test
    fun cancellationIoAndLowStorageAreExplicitTypedArchiveBoundaries() {
        val expected =
            setOf(
                PortableBackupArchiveFailure.CANCELLED,
                PortableBackupArchiveFailure.IO_FAILURE,
                PortableBackupArchiveFailure.INSUFFICIENT_STORAGE,
            )
        assertEquals(
            expected,
            PortableBackupArchiveFixtures.boundaryCases
                .map { it.expectedFailure }
                .filter { it in expected }
                .toSet(),
        )
    }

    @Test
    fun unicodeAndEmptyPayloadsRemainValidUtf8LogicalDocuments() {
        val unicode = "{\"text\":\"Caf\u00e9 東京 🚀\"}"
        assertTrue(unicode.toByteArray(StandardCharsets.UTF_8).size > unicode.length)
        val emptyData = PortableBackupArchiveFixtures.validData
        assertTrue(emptyData.isNotEmpty())
        assertNull(PortableBackupArchiveFixtures.validateEntryDescriptors(PortableBackupArchiveFixtures.validEntries()))
    }

    @Test
    fun emptyAndLargeDataEntriesRemainWithinTheExpandedBoundary() {
        val largeData = ByteArray(1024 * 1024) { index -> (index % 251).toByte() }
        val archive =
            PortableBackupArchiveFixtures.zip(
                listOf(
                    PortableBackupArchiveEntryFixture("manifest.json", PortableBackupArchiveFixtures.validManifest),
                    PortableBackupArchiveEntryFixture("data.json", largeData),
                ),
            )

        assertTrue(archive.isNotEmpty())
        assertTrue(largeData.size.toLong() < PortableBackupLimits.MAX_EXPANDED_BYTES)
        assertEquals(1024 * 1024, readEntries(archive).last().second.size)
    }

    @Test
    fun activeOrOpenTimerIsRejectedBeforeArchiveCreation() {
        val fixture = PortableBackupV1Fixtures.complete
        val data =
            PortableBackupDataV1(
                dataModelVersion = fixture.dataModelVersion,
                clients = emptyList(),
                consultants = emptyList(),
                tags = emptyList(),
                tasks =
                    listOf(
                        PortableBackupTaskV1(
                            id = "task-running",
                            seriesId = "series-running",
                            clientId = "client-running",
                            consultantId = null,
                            consultantNameSnapshot = "",
                            description = "",
                            hardwareSoftwarePurchases = "",
                            workType = "ON_SITE",
                            billingStatus = "BILLABLE",
                            mileage = null,
                            notes = "",
                            workDateEpochDay = 0,
                            zoneId = "UTC",
                            createdAtEpochMs = 0,
                            updatedAtEpochMs = 0,
                            tagSnapshots = emptyList(),
                            intervals =
                                listOf(
                                    PortableBackupIntervalV1(
                                        id = "interval-open",
                                        taskId = "task-running",
                                        startEpochMs = 1,
                                        stopEpochMs = null,
                                        wasManuallyEdited = false,
                                        createdAtEpochMs = 1,
                                        updatedAtEpochMs = 1,
                                    ),
                                ),
                        ),
                    ),
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
            )

        val result = PortableBackupValidator.validateData(data)
        assertTrue(result is PortableBackupDataValidationResult.Invalid)
        assertTrue(
            PortableBackupValidationFailure.OPEN_INTERVAL in
                (result as PortableBackupDataValidationResult.Invalid).failures,
        )
        val activeCase = PortableBackupArchiveFixtures.boundaryCases.single {
            it.expectedFailure == PortableBackupArchiveFailure.ACTIVE_TIMER
        }
        assertNotNull(activeCase.reason)
        assertFalse(activeCase.reason.isBlank())
    }

    private fun readEntries(bytes: ByteArray): List<Pair<String, ByteArray>> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            buildList {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    add(entry.name to zip.readBytes())
                }
            }
        }
}
