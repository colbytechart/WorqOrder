package worq.order.testing

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import worq.order.data.GoogleAccountHint
import worq.order.data.GoogleConnectionRepository
import worq.order.data.GoogleSpreadsheetConnection

class FakeGoogleConnectionRepository(
    initial: GoogleSpreadsheetConnection = GoogleSpreadsheetConnection(),
) : GoogleConnectionRepository {
    private val state = MutableStateFlow(initial)

    override fun observeConnection(): Flow<GoogleSpreadsheetConnection> = state

    override suspend fun readConnection(): GoogleSpreadsheetConnection = state.value

    override suspend fun saveSignedInAccount(account: GoogleAccountHint) {
        state.value =
            state.value.copy(
                accountId = account.id,
                accountDisplayName = account.displayName,
                isValidatedForCurrentAccount =
                    state.value.accountId == account.id &&
                        state.value.isValidatedForCurrentAccount,
            )
    }

    override suspend fun saveConnectedSpreadsheet(
        spreadsheetId: String,
        spreadsheetTitle: String,
        validatedAt: Instant,
    ) {
        require(state.value.hasAccountHint)
        state.value =
            state.value.copy(
                spreadsheetId = spreadsheetId,
                spreadsheetTitle = spreadsheetTitle,
                validatedAt = validatedAt,
                isValidatedForCurrentAccount = true,
            )
    }

    override suspend fun markAuthorizationRequired() {
        state.value =
            state.value.copy(isValidatedForCurrentAccount = false)
    }

    override suspend fun disconnectSpreadsheet() {
        state.value =
            state.value.copy(
                spreadsheetId = null,
                spreadsheetTitle = null,
                validatedAt = null,
                isValidatedForCurrentAccount = false,
            )
    }

    override suspend fun completeLocalSignOut() {
        state.value =
            state.value.copy(
                accountId = null,
                accountDisplayName = null,
                spreadsheetId = null,
                spreadsheetTitle = null,
                validatedAt = null,
                isValidatedForCurrentAccount = false,
            )
    }
}
