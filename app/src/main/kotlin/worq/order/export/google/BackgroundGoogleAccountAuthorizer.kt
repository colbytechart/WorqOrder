package worq.order.export.google

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import worq.order.data.GoogleAccountHint

/** Performs only silent, already-granted authorization. It never launches UI from background work. */
class BackgroundGoogleAccountAuthorizer(
    context: Context,
) : GoogleAccountAuthorizer {
    private val authorizationClient = Identity.getAuthorizationClient(context.applicationContext)

    override suspend fun signIn(): GoogleSignInResult = GoogleSignInResult.NoCredential

    override suspend fun authorizeSpreadsheet(spreadsheetId: String): GoogleAuthorizationResult =
        authorizeSilently(GoogleAuthorizationRequestFactory.spreadsheetPickerRequest(spreadsheetId))

    override suspend fun authorizeConnectedSpreadsheet(): GoogleAuthorizationResult =
        authorizeSilently(GoogleAuthorizationRequestFactory.connectedSpreadsheetRequest())

    private suspend fun authorizeSilently(
        request: com.google.android.gms.auth.api.identity.AuthorizationRequest,
    ): GoogleAuthorizationResult =
        try {
            val result = authorizationClient.authorize(request).await()
            if (result.hasResolution()) {
                GoogleAuthorizationResult.AuthorizationRequired
            } else {
                result.toApplicationResult()
            }
        } catch (error: ApiException) {
            when (error.statusCode) {
                CommonStatusCodes.CANCELED -> GoogleAuthorizationResult.Canceled
                CommonStatusCodes.SIGN_IN_REQUIRED -> GoogleAuthorizationResult.AuthorizationRequired
                ConnectionResult.SERVICE_DISABLED,
                ConnectionResult.SERVICE_MISSING,
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
                -> GoogleAuthorizationResult.PlayServicesUnavailable
                else -> GoogleAuthorizationResult.Failed
            }
        } catch (_: SecurityException) {
            GoogleAuthorizationResult.AuthorizationRequired
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            GoogleAuthorizationResult.Failed
        }

    override suspend fun clearToken(accessToken: GoogleAccessToken) {
        try {
            authorizationClient.clearToken(
                ClearTokenRequest.builder().setToken(accessToken.rawValue()).build(),
            ).await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The connection repository still records authorization-required state.
        }
    }

    override suspend fun signOut(accountId: String?): GoogleSignOutResult {
        val request =
            RevokeAccessRequest.builder()
                .setScopes(listOf(Scope(RestGoogleSheetsGateway.DRIVE_FILE_SCOPE)))
                .apply {
                    accountId?.takeIf(String::isNotBlank)?.let {
                        setAccount(Account(it, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
                    }
                }.build()
        return try {
            authorizationClient.revokeAccess(request).await()
            GoogleSignOutResult.Complete
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            GoogleSignOutResult.RevocationFailed
        }
    }

    private fun AuthorizationResult.toApplicationResult(): GoogleAuthorizationResult {
        val token = accessToken?.takeIf(String::isNotBlank)
            ?: return GoogleAuthorizationResult.AuthorizationRequired
        return GoogleAuthorizationResult.Authorized(
            accessToken = GoogleAccessToken.from(token),
            pickedFileIds = emptySet(),
        )
    }
}
