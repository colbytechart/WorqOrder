package worq.order.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.testing.FakeUtcClock
import worq.order.timer.TimerOperationLock

class PortableBackupArchiveWriterTest {
    @Test
    fun writerStreamsExactlyManifestAndDataWithVerifiedDigest() = runTest {
        val data = validData()
        val output = ByteArrayOutputStream()
        val result = PortableBackupArchiveWriter().write(prepared(data), output)

        assertTrue(result is PortableBackupArchiveWriteResult.Success)
        val success = result as PortableBackupArchiveWriteResult.Success
        val entries = readEntries(output.toByteArray())
        assertEquals(listOf(PORTABLE_BACKUP_MANIFEST_ENTRY, PORTABLE_BACKUP_DATA_ENTRY), entries.map { it.first })
        assertEquals(listOf(ZipEntry.DEFLATED, ZipEntry.DEFLATED), readMethods(output.toByteArray()))
        val manifest = decodeManifest(entries[0].second)
        assertEquals(entries[1].second.size.toLong(), manifest.dataByteCount)
        assertEquals(success.expandedDataByteCount, manifest.dataByteCount)
        assertEquals(success.dataSha256, manifest.dataSha256)
        assertEquals(data, decodeData(entries[1].second))
        assertTrue(success.compressedByteCount <= PortableBackupLimits.MAX_COMPRESSED_BYTES)
    }

    @Test
    fun streamingJsonOutputDecodesToTheSameUnicodeLogicalData() = runTest {
        val data = validData()
        val output = ByteArrayOutputStream()

        PortableBackupStreamingJsonWriter().writeData(data, output)

        assertEquals(data, decodeData(output.toByteArray()))
    }

    @Test
    fun writerRefusesAnInvalidLogicalPayloadBeforeWritingAnArchive() = runTest {
        val output = ByteArrayOutputStream()
        val invalid = validData().copy(dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION + 1)

        val result = PortableBackupArchiveWriter().write(prepared(invalid), output)

        assertEquals(
            PortableBackupArchiveWriteResult.Failed(PortableBackupArchiveWriteFailure.OUTPUT),
            result,
        )
        assertEquals(0, output.size())
    }

    @Test
    fun writerEnforcesExpandedAndCompressedLimitsDuringStreaming() = runTest {
        val expandedOutput = ByteArrayOutputStream()
        val expandedResult =
            PortableBackupArchiveWriter(maxExpandedBytes = 16)
                .write(prepared(validData()), expandedOutput)
        assertEquals(
            PortableBackupArchiveWriteResult.Failed(
                PortableBackupArchiveWriteFailure.EXPANDED_LIMIT,
            ),
            expandedResult,
        )
        assertEquals(0, expandedOutput.size())

        val compressedOutput = ByteArrayOutputStream()
        val compressedResult =
            PortableBackupArchiveWriter(maxCompressedBytes = 16)
                .write(prepared(validData()), compressedOutput)
        assertEquals(
            PortableBackupArchiveWriteResult.Failed(
                PortableBackupArchiveWriteFailure.COMPRESSED_LIMIT,
            ),
            compressedResult,
        )
        assertTrue(compressedOutput.size() <= 16)
    }

    @Test
    fun writerPropagatesCancellationRatherThanClaimingACompletedBackup() = runTest {
        val cancellingOutput =
            object : OutputStream() {
                override fun write(value: Int) {
                    throw CancellationException("test")
                }
            }

        var cancellationObserved = false
        try {
            PortableBackupArchiveWriter().write(prepared(validData()), cancellingOutput)
        } catch (_: CancellationException) {
            cancellationObserved = true
        }

        assertTrue(cancellationObserved)
    }

    @Test
    fun writerReturnsTypedOutputFailureWhenDestinationRejectsBytes() = runTest {
        val failingOutput =
            object : OutputStream() {
                override fun write(value: Int) {
                    throw IOException("No space left on device")
                }
            }

        assertEquals(
            PortableBackupArchiveWriteResult.Failed(PortableBackupArchiveWriteFailure.OUTPUT),
            PortableBackupArchiveWriter().write(prepared(validData()), failingOutput),
        )
    }

    @Test
    fun coordinatorCreatesUtcSafeNameAndBlocksRunningTimerSnapshots() = runTest {
        val data = validData()
        val clock = FakeUtcClock(Instant.parse("2026-09-20T15:00:00Z"))
        val coordinator =
            PortableBackupCoordinator(
                snapshotReader = PortableBackupSnapshotReader { PortableBackupSnapshotReadResult.Ready(data) },
                timerOperationLock = TimerOperationLock(),
                clock = clock,
                producer = PortableBackupProducer("0.6.0", 6),
            )

        val ready = coordinator.prepare()
        assertTrue(ready is PreparePortableBackupResult.Ready)
        assertEquals(
            "WorqOrder_Backup_2026-09-20_150000.zip",
            (ready as PreparePortableBackupResult.Ready).backup.suggestedFileName,
        )

        val runningCoordinator =
            PortableBackupCoordinator(
                snapshotReader = PortableBackupSnapshotReader { PortableBackupSnapshotReadResult.TimerRunning },
                timerOperationLock = TimerOperationLock(),
                clock = clock,
                producer = PortableBackupProducer("0.6.0", 6),
            )
        assertEquals(PreparePortableBackupResult.TimerRunning, runningCoordinator.prepare())
    }

    @Test
    fun creationBoundaryReportsPreparationWritingAndTypedSuccess() = runTest {
        val progress = mutableListOf<PortableBackupCreationProgress>()
        val backup = prepared(validData())
        val output = ByteArrayOutputStream()
        val result =
            PortableBackupCreationCoordinator(
                backupCoordinator =
                    PortableBackupCoordinator(
                        snapshotReader =
                            PortableBackupSnapshotReader {
                                PortableBackupSnapshotReadResult.Ready(backup.data)
                            },
                        timerOperationLock = TimerOperationLock(),
                        clock = FakeUtcClock(backup.createdAt),
                        producer = backup.producer,
                    ),
                outputDestination =
                    PortableBackupDocumentOutputDestination { _, preparedBackup ->
                        when (val archive = PortableBackupArchiveWriter().write(preparedBackup, output)) {
                            is PortableBackupArchiveWriteResult.Success ->
                                PortableBackupDocumentWriteResult.Success(archive)
                            is PortableBackupArchiveWriteResult.Failed ->
                                PortableBackupDocumentWriteResult.Failed(
                                    reason = archive.reason,
                                    partialDocumentMayRemain = false,
                                )
                        }
                    },
            ).create("content://example/backup.zip") { phase -> progress += phase }

        assertEquals(
            listOf(
                PortableBackupCreationProgress.PreparingSnapshot,
                PortableBackupCreationProgress.WritingArchive,
            ),
            progress,
        )
        assertTrue(result is PortableBackupCreationResult.Success)
        assertFalse(output.size() == 0)
    }

    @Test
    fun creationBoundaryDoesNotOpenOutputWhenTimerIsRunning() = runTest {
        var outputInvoked = false
        val progress = mutableListOf<PortableBackupCreationProgress>()
        val result =
            PortableBackupCreationCoordinator(
                backupCoordinator =
                    PortableBackupCoordinator(
                        snapshotReader =
                            PortableBackupSnapshotReader {
                                PortableBackupSnapshotReadResult.TimerRunning
                            },
                        timerOperationLock = TimerOperationLock(),
                        clock = FakeUtcClock(Instant.parse("2026-09-20T15:00:00Z")),
                        producer = PortableBackupProducer("0.6.0", 6),
                    ),
                outputDestination =
                    PortableBackupDocumentOutputDestination { _, _ ->
                        outputInvoked = true
                        error("Output must not open while a timer is running")
                    },
            ).create("content://example/backup.zip") { phase -> progress += phase }

        assertEquals(PortableBackupCreationResult.TimerRunning, result)
        assertFalse(outputInvoked)
        assertEquals(listOf(PortableBackupCreationProgress.PreparingSnapshot), progress)
    }

    @Test
    fun creationBoundaryPreservesPartialDocumentWarningOnOutputFailure() = runTest {
        val backup = prepared(validData())
        val result =
            PortableBackupCreationCoordinator(
                backupCoordinator =
                    PortableBackupCoordinator(
                        snapshotReader =
                            PortableBackupSnapshotReader {
                                PortableBackupSnapshotReadResult.Ready(backup.data)
                            },
                        timerOperationLock = TimerOperationLock(),
                        clock = FakeUtcClock(backup.createdAt),
                        producer = backup.producer,
                    ),
                outputDestination =
                    PortableBackupDocumentOutputDestination { _, _ ->
                        PortableBackupDocumentWriteResult.Failed(
                            reason = PortableBackupArchiveWriteFailure.OUTPUT,
                            partialDocumentMayRemain = true,
                        )
                    },
            ).create("content://example/backup.zip")

        assertEquals(
            PortableBackupCreationResult.OutputFailed(
                reason = PortableBackupArchiveWriteFailure.OUTPUT,
                partialDocumentMayRemain = true,
            ),
            result,
        )
    }

    private fun validData(): PortableBackupDataV1 =
        decodeData(PortableBackupArchiveFixtures.validData)

    private fun prepared(data: PortableBackupDataV1): PreparedPortableBackup =
        PreparedPortableBackup(
            data = data,
            createdAt = Instant.parse("2026-09-20T15:00:00Z"),
            producer = PortableBackupProducer("0.6.0", 6),
            suggestedFileName = "WorqOrder_Backup_2026-09-20_150000.zip",
        )

    private fun decodeManifest(bytes: ByteArray): PortableBackupManifestV1 {
        val result = PortableBackupJsonCodec.decodeManifest(String(bytes, StandardCharsets.UTF_8))
        assertTrue(result is PortableBackupDecodeResult.Success)
        return (result as PortableBackupDecodeResult.Success).value
    }

    private fun decodeData(bytes: ByteArray): PortableBackupDataV1 {
        val result = PortableBackupJsonCodec.decodeData(String(bytes, StandardCharsets.UTF_8))
        assertTrue(result is PortableBackupDecodeResult.Success)
        return (result as PortableBackupDecodeResult.Success).value
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

    private fun readMethods(bytes: ByteArray): List<Int> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            buildList {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    add(entry.method)
                    zip.readBytes()
                }
            }
        }
}
