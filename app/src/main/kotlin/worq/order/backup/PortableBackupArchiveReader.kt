package worq.order.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

sealed interface PortableBackupArchiveReadResult {
    data class Ready(
        val manifest: PortableBackupManifestV1,
        val data: PortableBackupDataV1,
        val archiveByteCount: Long,
        val dataByteCount: Long,
        val dataSha256: String,
    ) : PortableBackupArchiveReadResult

    data class Invalid(
        val reason: PortableBackupArchiveReadFailure,
    ) : PortableBackupArchiveReadResult
}

enum class PortableBackupArchiveReadFailure {
    COMPRESSED_LIMIT,
    EXPANDED_LIMIT,
    ARCHIVE_STRUCTURE,
    INVALID_UTF8,
    MANIFEST,
    CHECKSUM,
    UNSUPPORTED_VERSION,
    DATA,
    IO,
}

/**
 * Bounded reader for the fixed two-entry portable archive. It consumes input once, accepts no
 * archive metadata as authority, and returns a validated current logical DTO only after exact
 * manifest/data verification. The caller decides whether a valid source should be staged.
 */
class PortableBackupArchiveReader(
    private val upgraderRegistry: PortableBackupUpgraderRegistry = PortableBackupUpgraderRegistry(),
    private val maxCompressedBytes: Long = PortableBackupLimits.MAX_COMPRESSED_BYTES,
    private val maxExpandedBytes: Long = PortableBackupLimits.MAX_EXPANDED_BYTES,
) {
    init {
        require(maxCompressedBytes > 0)
        require(maxExpandedBytes > 0)
    }

    fun read(file: File): PortableBackupArchiveReadResult =
        try {
            if (!file.isFile || file.length() !in 1..maxCompressedBytes) {
                return PortableBackupArchiveReadResult.Invalid(
                    PortableBackupArchiveReadFailure.COMPRESSED_LIMIT,
                )
            }
            FileInputStream(file).use { input -> read(input) }
        } catch (_: IOException) {
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.IO)
        }

    fun read(input: InputStream): PortableBackupArchiveReadResult =
        try {
            val boundedInput = BoundedInputStream(input, maxCompressedBytes)
            ZipInputStream(boundedInput).use { zip ->
                val entries = linkedMapOf<String, ByteArray>()
                var expandedByteCount = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (
                        entry.isDirectory ||
                        entry.name !in REQUIRED_ENTRIES ||
                        entry.name in entries ||
                        entry.method != ZipEntry.DEFLATED
                    ) {
                        return PortableBackupArchiveReadResult.Invalid(
                            PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE,
                        )
                    }
                    val entryLimit =
                        if (entry.name == PORTABLE_BACKUP_MANIFEST_ENTRY) {
                            MAX_MANIFEST_BYTES
                        } else {
                            maxExpandedBytes
                        }
                    val bytes = zip.readBoundedEntry(entryLimit)
                    if (bytes.size.toLong() > maxExpandedBytes - expandedByteCount) {
                        return PortableBackupArchiveReadResult.Invalid(
                            PortableBackupArchiveReadFailure.EXPANDED_LIMIT,
                        )
                    }
                    expandedByteCount += bytes.size.toLong()
                    entries[entry.name] = bytes
                    zip.closeEntry()
                }
                // The writer emits this exact order. Rejecting reordered entries makes the
                // archive contract deterministic and avoids accepting an archive which carries
                // otherwise harmless-but-unreviewed ZIP structure.
                if (entries.keys.toList() != REQUIRED_ENTRY_ORDER) {
                    return PortableBackupArchiveReadResult.Invalid(
                        PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE,
                    )
                }

                val manifestBytes = requireNotNull(entries[PORTABLE_BACKUP_MANIFEST_ENTRY])
                val dataBytes = requireNotNull(entries[PORTABLE_BACKUP_DATA_ENTRY])
                val manifest =
                    when (val decoded = PortableBackupJsonCodec.decodeManifest(manifestBytes.decodeUtf8Strict())) {
                        is PortableBackupDecodeResult.Success -> decoded.value
                        is PortableBackupDecodeResult.Invalid ->
                            return PortableBackupArchiveReadResult.Invalid(
                                PortableBackupArchiveReadFailure.MANIFEST,
                            )
                    }
                when (val manifestValidation = PortableBackupValidator.validateManifest(manifest)) {
                    PortableBackupManifestValidationResult.Current -> Unit
                    is PortableBackupManifestValidationResult.UnsupportedFutureVersion ->
                        return PortableBackupArchiveReadResult.Invalid(
                            PortableBackupArchiveReadFailure.UNSUPPORTED_VERSION,
                        )
                    is PortableBackupManifestValidationResult.Invalid ->
                        return PortableBackupArchiveReadResult.Invalid(
                            PortableBackupArchiveReadFailure.MANIFEST,
                        )
                    is PortableBackupManifestValidationResult.UpgradeRequired -> Unit
                }
                val actualDataSha256 = dataBytes.sha256()
                if (
                    manifest.dataByteCount != dataBytes.size.toLong() ||
                    !manifest.dataSha256.equals(actualDataSha256, ignoreCase = true)
                ) {
                    return PortableBackupArchiveReadResult.Invalid(
                        PortableBackupArchiveReadFailure.CHECKSUM,
                    )
                }
                val dataJson = dataBytes.decodeUtf8Strict()
                val data =
                    when (val manifestValidation = PortableBackupValidator.validateManifest(manifest)) {
                        PortableBackupManifestValidationResult.Current ->
                            when (val decoded = PortableBackupJsonCodec.decodeData(dataJson)) {
                                is PortableBackupDecodeResult.Success -> decoded.value
                                is PortableBackupDecodeResult.Invalid ->
                                    return PortableBackupArchiveReadResult.Invalid(
                                        PortableBackupArchiveReadFailure.DATA,
                                    )
                            }
                        is PortableBackupManifestValidationResult.UpgradeRequired ->
                            when (val upgraded = upgraderRegistry.upgradeToCurrent(manifest, dataJson)) {
                                is PortableBackupUpgradeResult.Upgraded -> upgraded.data
                                is PortableBackupUpgradeResult.Unsupported ->
                                    return PortableBackupArchiveReadResult.Invalid(
                                        PortableBackupArchiveReadFailure.UNSUPPORTED_VERSION,
                                    )
                            }
                        else -> error("Manifest validity was checked above")
                    }
                if (PortableBackupValidator.validateData(data) !is PortableBackupDataValidationResult.Valid) {
                    return PortableBackupArchiveReadResult.Invalid(
                        PortableBackupArchiveReadFailure.DATA,
                    )
                }
                PortableBackupArchiveReadResult.Ready(
                    manifest = manifest,
                    data = data,
                    archiveByteCount = boundedInput.byteCount,
                    dataByteCount = dataBytes.size.toLong(),
                    dataSha256 = actualDataSha256,
                )
            }
        } catch (_: CompressedLimitExceeded) {
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.COMPRESSED_LIMIT)
        } catch (_: ExpandedLimitExceeded) {
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.EXPANDED_LIMIT)
        } catch (_: CharacterCodingException) {
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.INVALID_UTF8)
        } catch (_: ZipException) {
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE)
        } catch (_: IOException) {
            PortableBackupArchiveReadResult.Invalid(PortableBackupArchiveReadFailure.IO)
        }

    private fun ZipInputStream.readBoundedEntry(limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size().toLong() > limit - count) throw ExpandedLimitExceeded()
            output.write(buffer, 0, count)
        }
    }

    private fun ByteArray.decodeUtf8Strict(): String =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()

    private fun ByteArray.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte) }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private val limit: Long,
    ) : InputStream() {
        var byteCount: Long = 0
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) count(1)
            return value
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val count = delegate.read(bytes, offset, length)
            if (count > 0) count(count.toLong())
            return count
        }

        override fun close() = delegate.close()

        private fun count(next: Long) {
            if (next < 0 || byteCount > limit - next) throw CompressedLimitExceeded()
            byteCount += next
        }
    }

    private class CompressedLimitExceeded : IOException()

    private class ExpandedLimitExceeded : IOException()

    private companion object {
        const val MAX_MANIFEST_BYTES = 1024L * 1024L
        val REQUIRED_ENTRY_ORDER = listOf(PORTABLE_BACKUP_MANIFEST_ENTRY, PORTABLE_BACKUP_DATA_ENTRY)
        val REQUIRED_ENTRIES = REQUIRED_ENTRY_ORDER.toSet()
    }
}
