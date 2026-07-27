package worq.order.export.google

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import worq.order.data.GoogleAccountHint

class AndroidGoogleAccountAuthorizer(
    private val activity: ComponentActivity,
    private val webClientId: String,
) : GoogleAccountAuthorizer {
    private val credentialManager = CredentialManager.create(activity)
    private val authorizationClient = Identity.getAuthorizationClient(activity)
    private val driveFileScope = Scope(RestGoogleSheetsGateway.DRIVE_FILE_SCOPE)

    override suspend fun signIn(): GoogleSignInResult {
        val option =
            GetSignInWithGoogleOption
                .Builder(webClientId)
                .build()
        val request =
            GetCredentialRequest
                .Builder()
                .addCredentialOption(option)
                .build()
        return try {
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential
            if (
                credential !is CustomCredential ||
                credential.type !=
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleSignInResult.Failed
            } else {
                val googleCredential =
                    GoogleIdTokenCredential.createFrom(credential.data)
                // This client has no backend. The ID token is intentionally not
                // retained, logged, or used to authorize Google API calls.
                GoogleSignInResult.SignedIn(
                    GoogleAccountHint(
                        id = googleCredential.id,
                        displayName = googleCredential.displayName,
                    ),
                )
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInResult.Canceled
        } catch (_: NoCredentialException) {
            GoogleSignInResult.NoCredential
        } catch (_: GetCredentialProviderConfigurationException) {
            GoogleSignInResult.ProviderUnavailable
        } catch (_: GetCredentialException) {
            GoogleSignInResult.Failed
        } catch (_: IllegalArgumentException) {
            GoogleSignInResult.Failed
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }

    override suspend fun authorizeSpreadsheet(
        spreadsheetId: String,
    ): GoogleAuthorizationResult =
        authorize(
            GoogleAuthorizationRequestFactory
                .spreadsheetPickerRequest(spreadsheetId),
        )

    override suspend fun authorizeConnectedSpreadsheet():
        GoogleAuthorizationResult =
        authorize(
            GoogleAuthorizationRequestFactory.connectedSpreadsheetRequest(),
        )

    private suspend fun authorize(
        request: com.google.android.gms.auth.api.identity.AuthorizationRequest,
    ): GoogleAuthorizationResult {
        return try {
            val initialResult = authorizationClient.authorize(request).await()
            val finalResult =
                if (initialResult.hasResolution()) {
                    val intent =
                        launchResolution(
                            initialResult.pendingIntent
                                ?: return
                                    GoogleAuthorizationResult
                                        .AuthorizationRequired,
                        )
                            ?: return GoogleAuthorizationResult.Canceled
                    authorizationClient.getAuthorizationResultFromIntent(intent)
                } else {
                    initialResult
                }
            finalResult.toApplicationResult()
        } catch (error: ApiException) {
            error.toAuthorizationFailure()
        } catch (_: SecurityException) {
            GoogleAuthorizationResult.AuthorizationRequired
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            GoogleAuthorizationResult.Failed
        }
    }

    override suspend fun clearToken(accessToken: GoogleAccessToken) {
        try {
            authorizationClient
                .clearToken(
                    ClearTokenRequest
                        .builder()
                        .setToken(accessToken.rawValue())
                        .build(),
                ).await()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The caller still marks authorization as requiring renewal.
        }
    }

    override suspend fun signOut(accountId: String?): GoogleSignOutResult {
        val revokeRequest =
            RevokeAccessRequest
                .builder()
                .setScopes(listOf(driveFileScope))
                .apply {
                    accountId
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { id ->
                            setAccount(
                                Account(
                                    id,
                                    GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE,
                                ),
                            )
                        }
                }.build()
        val revoked =
            try {
                authorizationClient
                    .revokeAccess(revokeRequest)
                    .await()
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
        val credentialStateCleared =
            try {
                credentialManager.clearCredentialState(
                    ClearCredentialStateRequest(),
                )
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
        return if (revoked && credentialStateCleared) {
            GoogleSignOutResult.Complete
        } else {
            GoogleSignOutResult.RevocationFailed
        }
    }

    private suspend fun launchResolution(
        pendingIntent: PendingIntent,
    ): Intent? =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val key = "google-authorization-${UUID.randomUUID()}"
                lateinit var launcher:
                    androidx.activity.result.ActivityResultLauncher<
                        IntentSenderRequest,
                    >
                launcher =
                    activity.activityResultRegistry.register(
                        key,
                        ActivityResultContracts.StartIntentSenderForResult(),
                    ) { result ->
                        launcher.unregister()
                        if (continuation.isActive) {
                            continuation.resume(
                                result.data.takeIf {
                                    result.resultCode == Activity.RESULT_OK
                                },
                            )
                        }
                    }
                continuation.invokeOnCancellation {
                    activity.runOnUiThread {
                        launcher.unregister()
                    }
                }
                launcher.launch(
                    IntentSenderRequest
                        .Builder(pendingIntent.intentSender)
                        .build(),
                )
            }
        }

    private fun AuthorizationResult.toApplicationResult():
        GoogleAuthorizationResult {
        val token =
            accessToken
                ?.takeIf(String::isNotBlank)
                ?: return GoogleAuthorizationResult.AuthorizationRequired
        return GoogleAuthorizationResult.Authorized(
            accessToken = GoogleAccessToken.from(token),
            pickedFileIds = tokenResponseParams.pickedFileIds(),
        )
    }

    @Suppress("DEPRECATION")
    private fun Bundle?.pickedFileIds(): Set<String> {
        val value = this?.get(PICKED_FILE_IDS_RESPONSE_KEY)
        return when (value) {
            is String ->
                value
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split(',')
                    .map { it.trim().trim('"') }
                    .filter(String::isNotEmpty)
                    .toSet()
            is ArrayList<*> ->
                value.filterIsInstance<String>().map(String::trim).toSet()
            is Array<*> ->
                value.filterIsInstance<String>().map(String::trim).toSet()
            else -> emptySet()
        }
    }

    private fun ApiException.toAuthorizationFailure():
        GoogleAuthorizationResult =
        when (statusCode) {
            CommonStatusCodes.CANCELED -> GoogleAuthorizationResult.Canceled
            CommonStatusCodes.SIGN_IN_REQUIRED ->
                GoogleAuthorizationResult.AuthorizationRequired
            ConnectionResult.SERVICE_DISABLED,
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            -> GoogleAuthorizationResult.PlayServicesUnavailable
            else -> GoogleAuthorizationResult.Failed
        }

    private companion object {
        const val PICKED_FILE_IDS_RESPONSE_KEY = "picked_file_ids"
    }
}
