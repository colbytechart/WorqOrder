package worq.order.export.google

import worq.order.data.GoogleAccountHint
import worq.order.data.GoogleSpreadsheetConnection

class GoogleAccessToken private constructor(
    private val value: String,
) {
    internal fun bearerHeader(): String = "Bearer $value"

    internal fun rawValue(): String = value

    override fun toString(): String = "[redacted Google access token]"

    companion object {
        internal fun from(rawValue: String): GoogleAccessToken {
            require(rawValue.isNotBlank()) { "Google access token must not be blank" }
            return GoogleAccessToken(rawValue)
        }
    }
}

sealed interface GoogleSignInResult {
    data class SignedIn(
        val account: GoogleAccountHint,
    ) : GoogleSignInResult

    data object Canceled : GoogleSignInResult

    data object NoCredential : GoogleSignInResult

    data object ProviderUnavailable : GoogleSignInResult

    data object Failed : GoogleSignInResult
}

sealed interface GoogleAuthorizationResult {
    data class Authorized(
        val accessToken: GoogleAccessToken,
        val pickedFileIds: Set<String>,
    ) : GoogleAuthorizationResult

    data object Canceled : GoogleAuthorizationResult

    data object AuthorizationRequired : GoogleAuthorizationResult

    data object PlayServicesUnavailable : GoogleAuthorizationResult

    data object Failed : GoogleAuthorizationResult
}

sealed interface GoogleSignOutResult {
    data object Complete : GoogleSignOutResult

    data object RevocationFailed : GoogleSignOutResult
}

interface GoogleAccountAuthorizer {
    suspend fun signIn(): GoogleSignInResult

    suspend fun authorizeSpreadsheet(
        spreadsheetId: String,
    ): GoogleAuthorizationResult

    suspend fun authorizeConnectedSpreadsheet(): GoogleAuthorizationResult

    suspend fun clearToken(accessToken: GoogleAccessToken)

    suspend fun signOut(accountId: String?): GoogleSignOutResult
}

data class ValidatedGoogleSpreadsheet(
    val id: String,
    val title: String,
)

sealed interface GoogleSpreadsheetValidationResult {
    data class Success(
        val spreadsheet: ValidatedGoogleSpreadsheet,
    ) : GoogleSpreadsheetValidationResult

    data object NotFoundOrNotGranted : GoogleSpreadsheetValidationResult

    data object NotGoogleSpreadsheet : GoogleSpreadsheetValidationResult

    data object ReadOnly : GoogleSpreadsheetValidationResult

    data object ContentModificationRestricted :
        GoogleSpreadsheetValidationResult

    data object Offline : GoogleSpreadsheetValidationResult

    data object Timeout : GoogleSpreadsheetValidationResult

    data object Unauthorized : GoogleSpreadsheetValidationResult

    data object WorkspacePolicyBlocked : GoogleSpreadsheetValidationResult

    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : GoogleSpreadsheetValidationResult

    data object ServerFailure : GoogleSpreadsheetValidationResult

    data object MalformedResponse : GoogleSpreadsheetValidationResult
}

interface GoogleSheetsGateway {
    suspend fun validateEditableSpreadsheet(
        accessToken: GoogleAccessToken,
        spreadsheetId: String,
    ): GoogleSpreadsheetValidationResult
}

sealed interface GoogleConnectionOperationResult {
    data class SignedIn(
        val account: GoogleAccountHint,
    ) : GoogleConnectionOperationResult

    data class Connected(
        val connection: GoogleSpreadsheetConnection,
    ) : GoogleConnectionOperationResult

    data object Disconnected : GoogleConnectionOperationResult

    data object SignedOut : GoogleConnectionOperationResult

    data object SignOutPartiallyCompleted : GoogleConnectionOperationResult

    data object Canceled : GoogleConnectionOperationResult

    data class InvalidSpreadsheetInput(
        val reason: SpreadsheetInputError,
    ) : GoogleConnectionOperationResult

    data class Failed(
        val reason: GoogleConnectionFailure,
    ) : GoogleConnectionOperationResult
}

enum class GoogleConnectionFailure {
    NO_CREDENTIAL,
    CREDENTIAL_PROVIDER_UNAVAILABLE,
    SIGN_IN_FAILED,
    AUTHORIZATION_REQUIRED,
    PLAY_SERVICES_UNAVAILABLE,
    PICKER_RETURNED_DIFFERENT_FILE,
    NOT_FOUND_OR_NOT_GRANTED,
    NOT_GOOGLE_SPREADSHEET,
    READ_ONLY,
    CONTENT_MODIFICATION_RESTRICTED,
    OFFLINE,
    TIMEOUT,
    UNAUTHORIZED,
    WORKSPACE_POLICY_BLOCKED,
    RATE_LIMITED,
    SERVER_FAILURE,
    MALFORMED_RESPONSE,
    LOCAL_STORAGE,
}
