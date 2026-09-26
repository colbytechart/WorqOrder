package worq.order.backup

import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException

const val PORTABLE_BACKUP_MIME_TYPE = "application/zip"
const val PORTABLE_BACKUP_MANIFEST_ENTRY = "manifest.json"
const val PORTABLE_BACKUP_DATA_ENTRY = "data.json"

data class PortableBackupProducer(
    val versionName: String,
    val versionCode: Int,
)

data class PreparedPortableBackup(
    val data: PortableBackupDataV1,
    val createdAt: Instant,
    val producer: PortableBackupProducer,
    val suggestedFileName: String,
)

sealed interface PortableBackupArchiveWriteResult {
    data class Success(
        val compressedByteCount: Long,
        val expandedDataByteCount: Long,
        val dataSha256: String,
    ) : PortableBackupArchiveWriteResult

    data class Failed(
        val reason: PortableBackupArchiveWriteFailure,
    ) : PortableBackupArchiveWriteResult
}

enum class PortableBackupArchiveWriteFailure {
    COMPRESSED_LIMIT,
    EXPANDED_LIMIT,
    OUTPUT,
}

/**
 * Streams a prevalidated logical snapshot into the fixed two-entry Deflate ZIP contract. Data is
 * rendered once to calculate its UTF-8 length/digest and once into the ZIP, avoiding a full JSON
 * byte-array allocation while still writing the manifest before the data entry.
 */
class PortableBackupArchiveWriter internal constructor(
    private val dataWriter: PortableBackupStreamingJsonWriter = PortableBackupStreamingJsonWriter(),
    private val maxCompressedBytes: Long = PortableBackupLimits.MAX_COMPRESSED_BYTES,
    private val maxExpandedBytes: Long = PortableBackupLimits.MAX_EXPANDED_BYTES,
) {
    init {
        require(maxCompressedBytes > 0)
        require(maxExpandedBytes > 0)
    }

    suspend fun write(
        backup: PreparedPortableBackup,
        output: OutputStream,
    ): PortableBackupArchiveWriteResult =
        try {
            if (PortableBackupValidator.validateData(backup.data) !is PortableBackupDataValidationResult.Valid) {
                return PortableBackupArchiveWriteResult.Failed(
                    PortableBackupArchiveWriteFailure.OUTPUT,
                )
            }
            val dataDigest = DigestCountingOutputStream(maxExpandedBytes)
            dataWriter.writeData(backup.data, dataDigest)
            val dataSha256 = dataDigest.sha256()
            val manifest =
                PortableBackupManifestV1(
                    productId = PORTABLE_BACKUP_PRODUCT_ID,
                    backupFormatVersion = PORTABLE_BACKUP_FORMAT_VERSION,
                    producerVersionName = backup.producer.versionName,
                    producerVersionCode = backup.producer.versionCode,
                    createdAtEpochMs = backup.createdAt.toEpochMilli(),
                    dataModelVersion = backup.data.dataModelVersion,
                    dataEncoding = "UTF-8",
                    compression = "DEFLATE",
                    dataByteCount = dataDigest.byteCount,
                    dataSha256 = dataSha256,
                )
            if (PortableBackupValidator.validateManifest(manifest) !is PortableBackupManifestValidationResult.Current) {
                return PortableBackupArchiveWriteResult.Failed(
                    PortableBackupArchiveWriteFailure.OUTPUT,
                )
            }

            val boundedOutput = BoundedOutputStream(output, maxCompressedBytes)
            ZipOutputStream(boundedOutput).use { zip ->
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                writeEntry(
                    zip = zip,
                    name = PORTABLE_BACKUP_MANIFEST_ENTRY,
                    createdAt = backup.createdAt,
                ) { entryOutput ->
                    entryOutput.write(
                        PortableBackupJsonCodec.encodeManifest(manifest).toByteArray(Charsets.UTF_8),
                    )
                }
                writeEntry(
                    zip = zip,
                    name = PORTABLE_BACKUP_DATA_ENTRY,
                    createdAt = backup.createdAt,
                ) { entryOutput ->
                    dataWriter.writeData(backup.data, entryOutput)
                }
            }
            PortableBackupArchiveWriteResult.Success(
                compressedByteCount = boundedOutput.byteCount,
                expandedDataByteCount = dataDigest.byteCount,
                dataSha256 = dataSha256,
            )
        } catch (_: ExpandedLimitExceeded) {
            PortableBackupArchiveWriteResult.Failed(PortableBackupArchiveWriteFailure.EXPANDED_LIMIT)
        } catch (_: CompressedLimitExceeded) {
            PortableBackupArchiveWriteResult.Failed(PortableBackupArchiveWriteFailure.COMPRESSED_LIMIT)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            PortableBackupArchiveWriteResult.Failed(PortableBackupArchiveWriteFailure.OUTPUT)
        }

    private suspend fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        createdAt: Instant,
        write: suspend (OutputStream) -> Unit,
    ) {
        zip.putNextEntry(ZipEntry(name).apply { time = createdAt.toEpochMilli() })
        write(zip)
        zip.closeEntry()
    }

    private class DigestCountingOutputStream(
        private val limit: Long,
    ) : OutputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")
        var byteCount: Long = 0
            private set

        override fun write(value: Int) {
            ensureCapacity(1)
            digest.update(value.toByte())
            byteCount += 1
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            ensureCapacity(length.toLong())
            digest.update(bytes, offset, length)
            byteCount += length
        }

        fun sha256(): String = digest.digest().joinToString("") { byte -> "%02x".format(byte) }

        private fun ensureCapacity(nextBytes: Long) {
            if (nextBytes < 0 || byteCount > limit - nextBytes) {
                throw ExpandedLimitExceeded()
            }
        }
    }

    private class BoundedOutputStream(
        private val delegate: OutputStream,
        private val limit: Long,
    ) : OutputStream() {
        var byteCount: Long = 0
            private set

        override fun write(value: Int) {
            ensureCapacity(1)
            delegate.write(value)
            byteCount += 1
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            ensureCapacity(length.toLong())
            delegate.write(bytes, offset, length)
            byteCount += length
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()

        private fun ensureCapacity(nextBytes: Long) {
            if (nextBytes < 0 || byteCount > limit - nextBytes) {
                throw CompressedLimitExceeded()
            }
        }
    }

    private class ExpandedLimitExceeded : IOException()

    private class CompressedLimitExceeded : IOException()
}
