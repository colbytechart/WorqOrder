package worq.order.backup

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Deterministic archive fixtures for the M51 two-entry ZIP contract. */
data class PortableBackupArchiveEntryFixture(
    val name: String,
    val bytes: ByteArray = ByteArray(0),
    val encrypted: Boolean = false,
)

data class PortableBackupArchiveBoundaryFixture(
    val name: String,
    val reason: String,
    val expectedFailure: PortableBackupArchiveFailure,
)

enum class PortableBackupArchiveFailure {
    DUPLICATE_ENTRY,
    PATH_TRAVERSAL,
    ENCRYPTED_ENTRY,
    UNKNOWN_ENTRY,
    COMPRESSED_LIMIT,
    EXPANDED_LIMIT,
    CANCELLED,
    IO_FAILURE,
    INSUFFICIENT_STORAGE,
    ACTIVE_TIMER,
}

object PortableBackupArchiveFixtures {
    const val MANIFEST_ENTRY = "manifest.json"
    const val DATA_ENTRY = "data.json"
    const val VALID_FILE_NAME = "WorqOrder_Backup_2026-09-20_110000.zip"

    val validData: ByteArray =
        PortableBackupJsonCodec
            .encodeData(
                PortableBackupDataV1(
                    dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
                    clients = emptyList(),
                    consultants = emptyList(),
                    tags = emptyList(),
                    tasks = emptyList(),
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
                ),
            ).toByteArray(StandardCharsets.UTF_8)

    val validManifest: ByteArray =
        PortableBackupJsonCodec
            .encodeManifest(
                PortableBackupManifestV1(
                    productId = PORTABLE_BACKUP_PRODUCT_ID,
                    backupFormatVersion = PORTABLE_BACKUP_FORMAT_VERSION,
                    producerVersionName = "0.6.0",
                    producerVersionCode = 6,
                    createdAtEpochMs = 1_758_368_400_000,
                    dataModelVersion = PORTABLE_BACKUP_DATA_MODEL_VERSION,
                    dataEncoding = "UTF-8",
                    compression = "DEFLATE",
                    dataByteCount = validData.size.toLong(),
                    dataSha256 = validData.digestSha256(),
                ),
            ).toByteArray(StandardCharsets.UTF_8)

    val boundaryCases: List<PortableBackupArchiveBoundaryFixture> =
        listOf(
            PortableBackupArchiveBoundaryFixture(
                "duplicate-entry",
                "manifest.json appears twice",
                PortableBackupArchiveFailure.DUPLICATE_ENTRY,
            ),
            PortableBackupArchiveBoundaryFixture(
                "path-traversal",
                "an entry escapes the archive root",
                PortableBackupArchiveFailure.PATH_TRAVERSAL,
            ),
            PortableBackupArchiveBoundaryFixture(
                "encrypted-entry",
                "a ZIP encryption flag is present",
                PortableBackupArchiveFailure.ENCRYPTED_ENTRY,
            ),
            PortableBackupArchiveBoundaryFixture(
                "unknown-entry",
                "an entry is not manifest.json or data.json",
                PortableBackupArchiveFailure.UNKNOWN_ENTRY,
            ),
            PortableBackupArchiveBoundaryFixture(
                "compressed-limit",
                "compressed bytes exceed the hard limit",
                PortableBackupArchiveFailure.COMPRESSED_LIMIT,
            ),
            PortableBackupArchiveBoundaryFixture(
                "expanded-limit",
                "expanded bytes exceed the hard limit",
                PortableBackupArchiveFailure.EXPANDED_LIMIT,
            ),
            PortableBackupArchiveBoundaryFixture(
                "cancelled",
                "the document operation is cancelled",
                PortableBackupArchiveFailure.CANCELLED,
            ),
            PortableBackupArchiveBoundaryFixture(
                "io-failure",
                "the destination stream fails",
                PortableBackupArchiveFailure.IO_FAILURE,
            ),
            PortableBackupArchiveBoundaryFixture(
                "insufficient-storage",
                "the destination cannot provide bounded free space",
                PortableBackupArchiveFailure.INSUFFICIENT_STORAGE,
            ),
            PortableBackupArchiveBoundaryFixture(
                "active-timer",
                "a running timer blocks backup creation",
                PortableBackupArchiveFailure.ACTIVE_TIMER,
            ),
        )

    fun validEntries(): List<PortableBackupArchiveEntryFixture> =
        listOf(
            PortableBackupArchiveEntryFixture(MANIFEST_ENTRY, validManifest),
            PortableBackupArchiveEntryFixture(DATA_ENTRY, validData),
        )

    fun zip(entries: List<PortableBackupArchiveEntryFixture> = validEntries()): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { fixture ->
                    zip.putNextEntry(ZipEntry(fixture.name).apply { time = 0L })
                    zip.write(fixture.bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }

    fun validateEntryDescriptors(
        entries: List<PortableBackupArchiveEntryFixture>,
    ): PortableBackupArchiveFailure? {
        val names = entries.map { it.name }
        if (names.size != names.toSet().size) return PortableBackupArchiveFailure.DUPLICATE_ENTRY
        if (entries.any { it.encrypted }) return PortableBackupArchiveFailure.ENCRYPTED_ENTRY
        if (names.any { it.contains('/') || it.contains('\\') || it == "." || it == ".." }) {
            return PortableBackupArchiveFailure.PATH_TRAVERSAL
        }
        if (names.any { it != MANIFEST_ENTRY && it != DATA_ENTRY }) {
            return PortableBackupArchiveFailure.UNKNOWN_ENTRY
        }
        if (names.toSet() != setOf(MANIFEST_ENTRY, DATA_ENTRY)) {
            return PortableBackupArchiveFailure.UNKNOWN_ENTRY
        }
        return null
    }

    fun validateFileName(name: String): Boolean =
        Regex("WorqOrder_Backup_\\d{4}-\\d{2}-\\d{2}_\\d{6}\\.zip").matches(name)

    fun sha256(bytes: ByteArray): String = bytes.digestSha256()

    private fun ByteArray.digestSha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }
}
