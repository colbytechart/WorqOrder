package worq.order.data

import java.time.Instant
import kotlinx.coroutines.flow.Flow

data class GoogleAccountHint(
    val id: String,
    val displayName: String?,
)

data class GoogleSpreadsheetConnection(
    val accountId: String? = null,
    val accountDisplayName: String? = null,
    val spreadsheetId: String? = null,
    val spreadsheetTitle: String? = null,
    val validatedAt: Instant? = null,
    val isValidatedForCurrentAccount: Boolean = false,
    val stateVersion: Int = CURRENT_STATE_VERSION,
) {
    val hasAccountHint: Boolean
        get() = !accountId.isNullOrBlank()

    val hasSpreadsheetMetadata: Boolean
        get() = !spreadsheetId.isNullOrBlank() && !spreadsheetTitle.isNullOrBlank()

    val isConnected: Boolean
        get() =
            hasAccountHint &&
                hasSpreadsheetMetadata &&
                isValidatedForCurrentAccount

    companion object {
        const val CURRENT_STATE_VERSION = 1
    }
}

interface GoogleConnectionRepository {
    fun observeConnection(): Flow<GoogleSpreadsheetConnection>

    suspend fun readConnection(): GoogleSpreadsheetConnection

    suspend fun saveSignedInAccount(account: GoogleAccountHint)

    suspend fun saveConnectedSpreadsheet(
        spreadsheetId: String,
        spreadsheetTitle: String,
        validatedAt: Instant,
    )

    suspend fun markAuthorizationRequired()

    suspend fun disconnectSpreadsheet()

    suspend fun completeLocalSignOut()
}
