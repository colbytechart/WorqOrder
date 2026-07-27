package worq.order.export.google

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.GoogleAccountHint
import worq.order.testing.FakeGoogleConnectionRepository

class GoogleConnectionCoordinatorTest {
    @Test
    fun signInAndSuccessfulValidationPersistOnlySafeMetadata() =
        runTest {
            val fixture = Fixture()

            assertTrue(
                fixture.coordinator.signIn() is
                    GoogleConnectionOperationResult.SignedIn,
            )
            assertTrue(
                fixture.coordinator.validateAndConnect(SPREADSHEET_ID) is
                    GoogleConnectionOperationResult.Connected,
            )

            val stored = fixture.repository.readConnection()
            assertEquals("person@example.com", stored.accountId)
            assertEquals("Person", stored.accountDisplayName)
            assertEquals(SPREADSHEET_ID, stored.spreadsheetId)
            assertEquals("Work Log", stored.spreadsheetTitle)
            assertEquals(NOW, stored.validatedAt)
            assertTrue(stored.isConnected)
            assertEquals(
                "[redacted Google access token]",
                fixture.authorizer.token.toString(),
            )
        }

    @Test
    fun nonemptyPickerResultMustReturnExactlyThePastedSpreadsheet() =
        runTest {
            val fixture = Fixture()
            fixture.coordinator.signIn()
            fixture.authorizer.pickedIds = setOf("different_file_id_123456789")

            val result =
                fixture.coordinator.validateAndConnect(SPREADSHEET_ID)

            assertEquals(
                GoogleConnectionOperationResult.Failed(
                    GoogleConnectionFailure.PICKER_RETURNED_DIFFERENT_FILE,
                ),
                result,
            )
            assertEquals(0, fixture.gateway.callCount)
            assertFalse(fixture.repository.readConnection().isConnected)
        }

    @Test
    fun reconnectAcceptsReusedGrantWithoutRepeatedPickerIds() =
        runTest {
            val fixture = Fixture()
            fixture.coordinator.signIn()
            fixture.coordinator.validateAndConnect(SPREADSHEET_ID)
            fixture.coordinator.disconnectSpreadsheet()
            fixture.authorizer.pickedIds = emptySet()

            val result =
                fixture.coordinator.validateAndConnect(SPREADSHEET_ID)

            assertTrue(result is GoogleConnectionOperationResult.Connected)
            assertEquals(SPREADSHEET_ID, fixture.gateway.lastSpreadsheetId)
            assertTrue(fixture.repository.readConnection().isConnected)
        }

    @Test
    fun validationFailuresAreStructuredAndDoNotReplaceMetadata() =
        runTest {
            val fixture = Fixture()
            fixture.coordinator.signIn()
            val cases =
                listOf(
                    GoogleSpreadsheetValidationResult.NotFoundOrNotGranted to
                        GoogleConnectionFailure.NOT_FOUND_OR_NOT_GRANTED,
                    GoogleSpreadsheetValidationResult.ReadOnly to
                        GoogleConnectionFailure.READ_ONLY,
                    GoogleSpreadsheetValidationResult.Offline to
                        GoogleConnectionFailure.OFFLINE,
                    GoogleSpreadsheetValidationResult.Timeout to
                        GoogleConnectionFailure.TIMEOUT,
                )

            cases.forEach { (gatewayResult, expectedFailure) ->
                fixture.gateway.result = gatewayResult
                assertEquals(
                    GoogleConnectionOperationResult.Failed(expectedFailure),
                    fixture.coordinator.validateAndConnect(SPREADSHEET_ID),
                )
                assertNull(fixture.repository.readConnection().spreadsheetId)
            }
        }

    @Test
    fun unauthorizedClearsExactTokenAndMarksConnectionForRenewal() =
        runTest {
            val fixture = Fixture()
            fixture.coordinator.signIn()
            fixture.gateway.result =
                GoogleSpreadsheetValidationResult.Success(
                    ValidatedGoogleSpreadsheet(SPREADSHEET_ID, "Work Log"),
                )
            fixture.coordinator.validateAndConnect(SPREADSHEET_ID)
            fixture.gateway.result =
                GoogleSpreadsheetValidationResult.Unauthorized

            val result =
                fixture.coordinator.validateAndConnect(SPREADSHEET_ID)

            assertEquals(
                GoogleConnectionOperationResult.Failed(
                    GoogleConnectionFailure.UNAUTHORIZED,
                ),
                result,
            )
            assertEquals(1, fixture.authorizer.clearedTokens)
            assertFalse(
                fixture.repository
                    .readConnection()
                    .isValidatedForCurrentAccount,
            )
        }

    @Test
    fun disconnectPreservesAccountAndSignOutDisconnectsSpreadsheet() =
        runTest {
            val fixture = Fixture()
            fixture.coordinator.signIn()
            fixture.coordinator.validateAndConnect(SPREADSHEET_ID)

            assertEquals(
                GoogleConnectionOperationResult.Disconnected,
                fixture.coordinator.disconnectSpreadsheet(),
            )
            assertTrue(fixture.repository.readConnection().hasAccountHint)
            assertFalse(
                fixture.repository.readConnection().hasSpreadsheetMetadata,
            )

            fixture.coordinator.validateAndConnect(SPREADSHEET_ID)
            assertEquals(
                GoogleConnectionOperationResult.SignedOut,
                fixture.coordinator.signOut(),
            )
            val signedOut = fixture.repository.readConnection()
            assertFalse(signedOut.hasAccountHint)
            assertFalse(signedOut.hasSpreadsheetMetadata)
            assertNull(signedOut.spreadsheetId)
            assertNull(signedOut.spreadsheetTitle)
            assertNull(signedOut.validatedAt)
            assertFalse(signedOut.isConnected)
            assertEquals(
                "person@example.com",
                fixture.authorizer.signedOutAccountId,
            )
        }

    @Test
    fun remoteRevocationFailureIsNotReportedAsSuccessfulSignOut() =
        runTest {
            val fixture = Fixture()
            fixture.coordinator.signIn()
            fixture.coordinator.validateAndConnect(SPREADSHEET_ID)
            fixture.authorizer.signOutResult =
                GoogleSignOutResult.RevocationFailed

            assertEquals(
                GoogleConnectionOperationResult.SignOutPartiallyCompleted,
                fixture.coordinator.signOut(),
            )
            val signedOut = fixture.repository.readConnection()
            assertFalse(signedOut.hasAccountHint)
            assertFalse(signedOut.hasSpreadsheetMetadata)
        }

    private class Fixture {
        val repository = FakeGoogleConnectionRepository()
        val authorizer = FakeAuthorizer()
        val gateway = FakeGateway()
        val coordinator =
            GoogleConnectionCoordinator(
                authorizer = authorizer,
                sheetsGateway = gateway,
                connectionRepository = repository,
                now = { NOW },
            )
    }

    private class FakeAuthorizer : GoogleAccountAuthorizer {
        val token = GoogleAccessToken.from("test-access-token")
        var pickedIds: Set<String> = setOf(SPREADSHEET_ID)
        var clearedTokens = 0
        var signOutResult: GoogleSignOutResult = GoogleSignOutResult.Complete
        var signedOutAccountId: String? = null

        override suspend fun signIn(): GoogleSignInResult =
            GoogleSignInResult.SignedIn(
                GoogleAccountHint(
                    id = "person@example.com",
                    displayName = "Person",
                ),
            )

        override suspend fun authorizeSpreadsheet(
            spreadsheetId: String,
        ): GoogleAuthorizationResult =
            GoogleAuthorizationResult.Authorized(token, pickedIds)

        override suspend fun authorizeConnectedSpreadsheet():
            GoogleAuthorizationResult =
            GoogleAuthorizationResult.Authorized(token, emptySet())

        override suspend fun clearToken(accessToken: GoogleAccessToken) {
            assertTrue(accessToken === token)
            clearedTokens += 1
        }

        override suspend fun signOut(
            accountId: String?,
        ): GoogleSignOutResult {
            signedOutAccountId = accountId
            return signOutResult
        }
    }

    private class FakeGateway : GoogleSheetsGateway {
        var result: GoogleSpreadsheetValidationResult =
            GoogleSpreadsheetValidationResult.Success(
                ValidatedGoogleSpreadsheet(SPREADSHEET_ID, "Work Log"),
            )
        var callCount = 0
        var lastSpreadsheetId: String? = null

        override suspend fun validateEditableSpreadsheet(
            accessToken: GoogleAccessToken,
            spreadsheetId: String,
        ): GoogleSpreadsheetValidationResult {
            callCount += 1
            lastSpreadsheetId = spreadsheetId
            return result
        }
    }

    private companion object {
        const val SPREADSHEET_ID =
            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
        val NOW: Instant = Instant.parse("2026-07-26T14:00:00Z")
    }
}
