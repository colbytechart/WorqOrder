package worq.order.export.google

import android.accounts.Account
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope

internal object GoogleAuthorizationRequestFactory {
    fun connectedSpreadsheetRequest(
        accountId: String,
    ): AuthorizationRequest =
        AuthorizationRequest
            .builder()
            .setRequestedScopes(
                listOf(Scope(RestGoogleSheetsGateway.DRIVE_FILE_SCOPE)),
            ).setOptOutIncludingGrantedScopes(true)
            .setAccount(
                Account(
                    accountId.trim().also {
                        require(it.isNotEmpty()) { "Google account ID must not be blank" }
                    },
                    GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE,
                ),
            )
            .build()

    fun spreadsheetPickerRequest(
        spreadsheetId: String,
    ): AuthorizationRequest =
        AuthorizationRequest
            .builder()
            .setRequestedScopes(
                listOf(Scope(RestGoogleSheetsGateway.DRIVE_FILE_SCOPE)),
            ).setOptOutIncludingGrantedScopes(true)
            .addResourceParameter(
                AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER,
                "true",
            ).addResourceParameter(
                AuthorizationRequest.ResourceParameter.PICKER_FILE_IDS,
                spreadsheetId,
            ).addResourceParameter(
                AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES,
                RestGoogleSheetsGateway.GOOGLE_SHEETS_MIME_TYPE,
            ).build()
}
