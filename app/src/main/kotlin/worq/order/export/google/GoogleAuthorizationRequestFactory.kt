package worq.order.export.google

import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope

internal object GoogleAuthorizationRequestFactory {
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
