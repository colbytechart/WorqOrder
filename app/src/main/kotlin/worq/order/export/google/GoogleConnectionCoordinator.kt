package worq.order.export.google

import java.time.Instant
import kotlinx.coroutines.CancellationException
import worq.order.data.GoogleConnectionRepository

class GoogleConnectionCoordinator(
    private val authorizer: GoogleAccountAuthorizer,
    private val sheetsGateway: GoogleSheetsGateway,
    private val connectionRepository: GoogleConnectionRepository,
    private val now: () -> Instant,
) {
    suspend fun signIn(): GoogleConnectionOperationResult =
        when (val result = authorizer.signIn()) {
            is GoogleSignInResult.SignedIn -> {
                localResult {
                    connectionRepository.saveSignedInAccount(result.account)
                }.fold(
                    onSuccess = {
                        GoogleConnectionOperationResult.SignedIn(result.account)
                    },
                    onFailure = {
                        GoogleConnectionOperationResult.Failed(
                            GoogleConnectionFailure.LOCAL_STORAGE,
                        )
                    },
                )
            }
            GoogleSignInResult.Canceled ->
                GoogleConnectionOperationResult.Canceled
            GoogleSignInResult.NoCredential ->
                GoogleConnectionOperationResult.Failed(
                    GoogleConnectionFailure.NO_CREDENTIAL,
                )
            GoogleSignInResult.ProviderUnavailable ->
                GoogleConnectionOperationResult.Failed(
                    GoogleConnectionFailure.CREDENTIAL_PROVIDER_UNAVAILABLE,
                )
            GoogleSignInResult.Failed ->
                GoogleConnectionOperationResult.Failed(
                    GoogleConnectionFailure.SIGN_IN_FAILED,
                )
        }

    suspend fun validateAndConnect(
        spreadsheetInput: String,
    ): GoogleConnectionOperationResult {
        val parsed =
            when (val result = SpreadsheetIdParser.parse(spreadsheetInput)) {
                is SpreadsheetInputParseResult.Valid -> result.spreadsheetId
                is SpreadsheetInputParseResult.Invalid ->
                    return GoogleConnectionOperationResult
                        .InvalidSpreadsheetInput(result.reason)
            }
        val connection =
            localResult { connectionRepository.readConnection() }
                .getOrElse {
                    return GoogleConnectionOperationResult.Failed(
                        GoogleConnectionFailure.LOCAL_STORAGE,
                    )
                }
        if (!connection.hasAccountHint) {
            return GoogleConnectionOperationResult.Failed(
                GoogleConnectionFailure.AUTHORIZATION_REQUIRED,
            )
        }
        val authorized =
            when (
                val result = authorizer.authorizeSpreadsheet(parsed)
            ) {
                is GoogleAuthorizationResult.Authorized -> result
                GoogleAuthorizationResult.Canceled ->
                    return GoogleConnectionOperationResult.Canceled
                GoogleAuthorizationResult.AuthorizationRequired -> {
                    markAuthorizationRequired()
                    return GoogleConnectionOperationResult.Failed(
                        GoogleConnectionFailure.AUTHORIZATION_REQUIRED,
                    )
                }
                GoogleAuthorizationResult.PlayServicesUnavailable ->
                    return GoogleConnectionOperationResult.Failed(
                        GoogleConnectionFailure.PLAY_SERVICES_UNAVAILABLE,
                    )
                GoogleAuthorizationResult.Failed ->
                    return GoogleConnectionOperationResult.Failed(
                        GoogleConnectionFailure.AUTHORIZATION_REQUIRED,
                    )
            }
        if (
            authorized.pickedFileIds.isNotEmpty() &&
            authorized.pickedFileIds != setOf(parsed)
        ) {
            return GoogleConnectionOperationResult.Failed(
                GoogleConnectionFailure.PICKER_RETURNED_DIFFERENT_FILE,
            )
        }
        return when (
            val validation =
                sheetsGateway.validateEditableSpreadsheet(
                    accessToken = authorized.accessToken,
                    spreadsheetId = parsed,
                )
        ) {
            is GoogleSpreadsheetValidationResult.Success -> {
                if (validation.spreadsheet.id != parsed) {
                    GoogleConnectionOperationResult.Failed(
                        GoogleConnectionFailure.MALFORMED_RESPONSE,
                    )
                } else {
                    localResult {
                        connectionRepository.saveConnectedSpreadsheet(
                            spreadsheetId = validation.spreadsheet.id,
                            spreadsheetTitle = validation.spreadsheet.title,
                            validatedAt = now(),
                        )
                        connectionRepository.readConnection()
                    }.fold(
                        onSuccess = {
                            GoogleConnectionOperationResult.Connected(it)
                        },
                        onFailure = {
                            GoogleConnectionOperationResult.Failed(
                                GoogleConnectionFailure.LOCAL_STORAGE,
                            )
                        },
                    )
                }
            }
            GoogleSpreadsheetValidationResult.NotFoundOrNotGranted ->
                failed(GoogleConnectionFailure.NOT_FOUND_OR_NOT_GRANTED)
            GoogleSpreadsheetValidationResult.NotGoogleSpreadsheet ->
                failed(GoogleConnectionFailure.NOT_GOOGLE_SPREADSHEET)
            GoogleSpreadsheetValidationResult.ReadOnly ->
                failed(GoogleConnectionFailure.READ_ONLY)
            GoogleSpreadsheetValidationResult.ContentModificationRestricted ->
                failed(GoogleConnectionFailure.CONTENT_MODIFICATION_RESTRICTED)
            GoogleSpreadsheetValidationResult.Offline ->
                failed(GoogleConnectionFailure.OFFLINE)
            GoogleSpreadsheetValidationResult.Timeout ->
                failed(GoogleConnectionFailure.TIMEOUT)
            GoogleSpreadsheetValidationResult.Unauthorized -> {
                try {
                    authorizer.clearToken(authorized.accessToken)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                }
                markAuthorizationRequired()
                failed(GoogleConnectionFailure.UNAUTHORIZED)
            }
            GoogleSpreadsheetValidationResult.WorkspacePolicyBlocked ->
                failed(GoogleConnectionFailure.WORKSPACE_POLICY_BLOCKED)
            is GoogleSpreadsheetValidationResult.RateLimited ->
                failed(GoogleConnectionFailure.RATE_LIMITED)
            GoogleSpreadsheetValidationResult.ServerFailure ->
                failed(GoogleConnectionFailure.SERVER_FAILURE)
            GoogleSpreadsheetValidationResult.MalformedResponse ->
                failed(GoogleConnectionFailure.MALFORMED_RESPONSE)
        }
    }

    suspend fun disconnectSpreadsheet(): GoogleConnectionOperationResult =
        localResult { connectionRepository.disconnectSpreadsheet() }.fold(
            onSuccess = { GoogleConnectionOperationResult.Disconnected },
            onFailure = {
                failed(GoogleConnectionFailure.LOCAL_STORAGE)
            },
        )

    suspend fun signOut(): GoogleConnectionOperationResult {
        val accountId =
            localResult { connectionRepository.readConnection() }
                .getOrNull()
                ?.accountId
        val remoteResult = authorizer.signOut(accountId)
        val localResult =
            localResult { connectionRepository.completeLocalSignOut() }
        if (localResult.isFailure) {
            return failed(GoogleConnectionFailure.LOCAL_STORAGE)
        }
        return if (remoteResult == GoogleSignOutResult.Complete) {
            GoogleConnectionOperationResult.SignedOut
        } else {
            GoogleConnectionOperationResult.SignOutPartiallyCompleted
        }
    }

    private suspend fun markAuthorizationRequired() {
        localResult { connectionRepository.markAuthorizationRequired() }
    }

    private fun failed(
        reason: GoogleConnectionFailure,
    ): GoogleConnectionOperationResult =
        GoogleConnectionOperationResult.Failed(reason)

    private suspend fun <T> localResult(
        block: suspend () -> T,
    ): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
}
