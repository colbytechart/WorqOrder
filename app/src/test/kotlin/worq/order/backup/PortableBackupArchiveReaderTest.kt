package worq.order.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableBackupArchiveReaderTest {
    @Test
    fun readerAcceptsWriterArchiveOnlyAfterExactManifestAndDataValidation() = runTest {
        val data =
            PortableBackupJsonCodec.decodeData(
                PortableBackupArchiveFixtures.validData.toString(StandardCharsets.UTF_8),
            )
        assertTrue(data is PortableBackupDecodeResult.Success)
        val decodedData = (data as PortableBackupDecodeResult.Success).value
        val archive = ByteArrayOutputStream()
        val write =
            PortableBackupArchiveWriter().write(
                PreparedPortableBackup(
                    data = decodedData,
                    createdAt = Instant.parse("2026-09-20T15:00:00Z"),
                    producer = PortableBackupProducer("0.6.0", 6),
                    suggestedFileName = "WorqOrder_Backup_2026-09-20_150000.zip",
                ),
                archive,
            )

        assertTrue(write is PortableBackupArchiveWriteResult.Success)
        val result = PortableBackupArchiveReader().read(ByteArrayInputStream(archive.toByteArray()))
        assertTrue(result is PortableBackupArchiveReadResult.Ready)
        assertEquals(
            decodedData,
            (result as PortableBackupArchiveReadResult.Ready).data,
        )
    }

    @Test
    fun readerRejectsReorderedEntriesAndChecksumMismatch() = runTest {
        val dataBytes = PortableBackupArchiveFixtures.validData
        val manifestBytes = PortableBackupArchiveFixtures.validManifest
        val reordered = zipOf(PORTABLE_BACKUP_DATA_ENTRY to dataBytes, PORTABLE_BACKUP_MANIFEST_ENTRY to manifestBytes)
        assertEquals(
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE),
            PortableBackupArchiveReader().read(ByteArrayInputStream(reordered)),
        )

        val mismatchedData = dataBytes.copyOf().also { bytes -> bytes[bytes.lastIndex] = 'x'.code.toByte() }
        val checksumMismatch = zipOf(PORTABLE_BACKUP_MANIFEST_ENTRY to manifestBytes, PORTABLE_BACKUP_DATA_ENTRY to mismatchedData)
        assertEquals(
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.CHECKSUM),
            PortableBackupArchiveReader().read(ByteArrayInputStream(checksumMismatch)),
        )
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
}
