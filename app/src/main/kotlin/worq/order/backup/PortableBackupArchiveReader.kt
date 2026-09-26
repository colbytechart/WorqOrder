package worq.order.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
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
    private val maxMaterializedDataBytes: Long = defaultMaterializedDataLimit(),
) {
    init {
        require(maxCompressedBytes > 0)
        require(maxExpandedBytes > 0)
        require(maxMaterializedDataBytes > 0)
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
                val manifestEntry = zip.nextEntry
                if (!manifestEntry.isAcceptedEntry(PORTABLE_BACKUP_MANIFEST_ENTRY)) {
                    return PortableBackupArchiveReadResult.Invalid(
                        PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE,
                    )
                }
                val manifestBytes = zip.readBoundedEntry(MAX_MANIFEST_BYTES)
                zip.closeEntry()
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

                val effectiveDataLimit =
                    minOf(
                        maxExpandedBytes - manifestBytes.size.toLong(),
                        maxMaterializedDataBytes,
                    )
                if (manifest.dataByteCount !in 1..effectiveDataLimit) {
                    return PortableBackupArchiveReadResult.Invalid(
                        PortableBackupArchiveReadFailure.EXPANDED_LIMIT,
                    )
                }

                val dataEntry = zip.nextEntry
                if (!dataEntry.isAcceptedEntry(PORTABLE_BACKUP_DATA_ENTRY)) {
                    return PortableBackupArchiveReadResult.Invalid(
                        PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE,
                    )
                }
                val digestInput = BoundedDigestInputStream(zip, effectiveDataLimit)
                val manifestValidation = PortableBackupValidator.validateManifest(manifest)
                val decodedData =
                    when (manifestValidation) {
                        PortableBackupManifestValidationResult.Current -> {
                            val decoder =
                                Charsets.UTF_8
                                    .newDecoder()
                                    .onMalformedInput(CodingErrorAction.REPORT)
                                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                            PortableBackupJsonCodec.decodeData(InputStreamReader(digestInput, decoder))
                        }
                        is PortableBackupManifestValidationResult.UpgradeRequired -> {
                            val bytes = digestInput.readBoundedBytes(effectiveDataLimit)
                            val json = bytes.decodeUtf8Strict()
                            when (val upgraded = upgraderRegistry.upgradeToCurrent(manifest, json)) {
                                is PortableBackupUpgradeResult.Upgraded ->
                                    PortableBackupDecodeResult.Success(upgraded.data)
                                is PortableBackupUpgradeResult.Unsupported -> {
                                    digestInput.drain()
                                    zip.closeEntry()
                                    return PortableBackupArchiveReadResult.Invalid(
                                        PortableBackupArchiveReadFailure.UNSUPPORTED_VERSION,
                                    )
                                }
                            }
                        }
                        else -> error("Manifest validity was checked above")
                    }
                // A strict decoder can fail before consuming the entry. Drain through the same
                // counter/digest so checksum precedence and ZIP-bomb bounds remain authoritative.
                digestInput.drain()
                val dataByteCount = digestInput.byteCount
                val actualDataSha256 = digestInput.sha256()
                zip.closeEntry()
                if (zip.nextEntry != null) {
                    return PortableBackupArchiveReadResult.Invalid(
                        PortableBackupArchiveReadFailure.ARCHIVE_STRUCTURE,
                    )
                }
                if (
                    manifest.dataByteCount != dataByteCount ||
                    !manifest.dataSha256.equals(actualDataSha256, ignoreCase = true)
                ) {
                    return PortableBackupArchiveReadResult.Invalid(
                        PortableBackupArchiveReadFailure.CHECKSUM,
                    )
                }
                val data =
                    when (decodedData) {
                        is PortableBackupDecodeResult.Success -> decodedData.value
                        is PortableBackupDecodeResult.Invalid ->
                            return PortableBackupArchiveReadResult.Invalid(
                                PortableBackupArchiveReadFailure.DATA,
                            )
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
                    dataByteCount = dataByteCount,
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

    private fun ZipEntry?.isAcceptedEntry(expectedName: String): Boolean =
        this != null &&
            !isDirectory &&
            name == expectedName &&
            method == ZipEntry.DEFLATED

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

    private fun InputStream.readBoundedBytes(limit: Long): ByteArray {
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

    private class BoundedDigestInputStream(
        private val delegate: InputStream,
        private val limit: Long,
    ) : InputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")

        var byteCount: Long = 0
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                count(1)
                digest.update(value.toByte())
            }
            return value
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val count = delegate.read(bytes, offset, length)
            if (count > 0) {
                count(count.toLong())
                digest.update(bytes, offset, count)
            }
            return count
        }

        fun drain() {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (read(buffer) >= 0) {
                // Counting and digesting happen in read().
            }
        }

        fun sha256(): String = digest.digest().joinToString("") { byte -> "%02x".format(byte) }

        private fun count(next: Long) {
            if (next < 0 || byteCount > limit - next) throw ExpandedLimitExceeded()
            byteCount += next
        }
    }

    private class CompressedLimitExceeded : IOException()

    private class ExpandedLimitExceeded : IOException()

    private companion object {
        const val MAX_MANIFEST_BYTES = 1024L * 1024L

        fun defaultMaterializedDataLimit(): Long =
            (Runtime.getRuntime().maxMemory() / 8L)
                .coerceAtLeast(8L * 1024L * 1024L)
                .coerceAtMost(PortableBackupLimits.MAX_EXPANDED_BYTES)
    }
}
