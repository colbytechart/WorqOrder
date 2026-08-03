package worq.order.data

data class ClientImportCandidate(
    val displayName: String,
    val canonicalName: String,
)

data class ClientImportApplyResult(
    val addedCount: Int,
    val restoredCount: Int,
    val skippedActiveCount: Int,
)

interface ClientImportRepository {
    /**
     * Applies a fully validated, canonically unique import batch in one database transaction.
     */
    suspend fun applyImport(candidates: List<ClientImportCandidate>): ClientImportApplyResult
}
