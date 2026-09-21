package worq.order.data

/**
 * Installation-local Google transport identity. It is intentionally excluded from portable
 * backup data: importing on another device must not claim the source device's hidden Google rows.
 */
data class ExportOriginState(
    val originId: String,
    val legacyV1AdoptionAllowed: Boolean,
)

interface ExportOriginRepository {
    /** Creates the initial installation identity exactly once, or returns the stored identity. */
    suspend fun readOrCreate(): ExportOriginState

    /** Called only after a successful portable replacement commits. */
    suspend fun rotateAfterPortableImport(): ExportOriginState

    /** Prevents any later legacy-v1 Google row adoption for this installation. */
    suspend fun markLegacyV1AdoptionComplete()
}

fun interface ExportOriginIdGenerator {
    fun newOriginId(): String
}
