package worq.order.export.google

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleAuthorizationRequestFactoryTest {
    @Test
    fun connectedSpreadsheetRequestUsesOnlyDriveFileWithoutPickerFilters() {
        val request =
            GoogleAuthorizationRequestFactory.connectedSpreadsheetRequest()

        assertEquals(
            listOf(RestGoogleSheetsGateway.DRIVE_FILE_SCOPE),
            request.requestedScopes.map { it.scopeUri },
        )
        assertTrue(request.optOutIncludingGrantedScopes)
        assertEquals(
            null,
            request.getResourceParameter(
                AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER,
            ),
        )
        assertEquals(
            null,
            request.getResourceParameter(
                AuthorizationRequest.ResourceParameter.PICKER_FILE_IDS,
            ),
        )
    }

    @Test
    fun pickerRequestUsesOnlyDriveFileAndExactFileFilters() {
        val request =
            GoogleAuthorizationRequestFactory
                .spreadsheetPickerRequest(SPREADSHEET_ID)

        assertEquals(
            listOf(RestGoogleSheetsGateway.DRIVE_FILE_SCOPE),
            request.requestedScopes.map { it.scopeUri },
        )
        assertTrue(request.optOutIncludingGrantedScopes)
        assertEquals(
            "true",
            request.getResourceParameter(
                AuthorizationRequest.ResourceParameter.PICKER_OAUTH_TRIGGER,
            ),
        )
        assertEquals(
            SPREADSHEET_ID,
            request.getResourceParameter(
                AuthorizationRequest.ResourceParameter.PICKER_FILE_IDS,
            ),
        )
        assertEquals(
            RestGoogleSheetsGateway.GOOGLE_SHEETS_MIME_TYPE,
            request.getResourceParameter(
                AuthorizationRequest.ResourceParameter.PICKER_MIMETYPES,
            ),
        )
    }

    private companion object {
        const val SPREADSHEET_ID =
            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
    }
}
