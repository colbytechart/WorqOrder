package worq.order.export.google

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.GoogleAccountHint
import worq.order.export.ExportRow
import worq.order.export.ExportSchema
import worq.order.export.ExportSnapshot
import worq.order.export.ExportSnapshotProvider
import worq.order.export.PrepareExportSnapshotResult
import worq.order.testing.FakeGoogleConnectionRepository

class GoogleSheetsExportCoordinatorTest {
    @Test
    fun connectedExportPassesCanonicalSnapshotUnchanged() =
        runTest {
            val fixture = Fixture()
            fixture.connect()

            val result = fixture.coordinator.export(WORK_DATE)

            assertTrue(result is GoogleSheetsExportOperationResult.Success)
            val receipt =
                (result as GoogleSheetsExportOperationResult.Success).receipt
            assertEquals(WORK_DATE, receipt.workDate)
            assertEquals("Work Log", receipt.spreadsheetTitle)
            assertEquals("WorqOrder_2026-07-24", receipt.tabName)
            assertEquals(1, receipt.dataRowCount)
            assertSame(fixture.snapshot, fixture.gateway.receivedSnapshot)
            assertEquals(SPREADSHEET_ID, fixture.gateway.receivedSpreadsheetId)
        }

    @Test
    fun missingConnectionDoesNotPrepareOrAuthorize() =
        runTest {
            val fixture = Fixture()

            assertEquals(
                GoogleSheetsExportOperationResult.SetupRequired,
                fixture.coordinator.export(WORK_DATE),
            )
            assertEquals(0, fixture.snapshotProvider.calls)
            assertEquals(0, fixture.authorizer.authorizationCalls)
            assertEquals(0, fixture.gateway.calls)
        }

    @Test
    fun expiredAuthorizationClearsTokenAndMarksConnectionStale() =
        runTest {
            val fixture = Fixture()
            fixture.connect()
            fixture.gateway.result =
                GoogleSheetsGatewayExportResult.Unauthorized

            assertEquals(
                GoogleSheetsExportOperationResult.AuthorizationRequired,
                fixture.coordinator.export(WORK_DATE),
            )
            assertEquals(1, fixture.authorizer.clearedTokens)
            assertFalse(
                fixture.repository
                    .readConnection()
                    .isValidatedForCurrentAccount,
            )
        }

    @Test
    fun authorizationRequiredCanBeRetriedInteractivelyAndRestoresConnection() =
        runTest {
            val fixture = Fixture()
            fixture.connect()
            fixture.authorizer.authorizationResult =
                GoogleAuthorizationResult.AuthorizationRequired

            assertEquals(
                GoogleSheetsExportOperationResult.AuthorizationRequired,
                fixture.coordinator.export(WORK_DATE),
            )
            assertFalse(fixture.repository.readConnection().isConnected)

            fixture.authorizer.authorizationResult =
                GoogleAuthorizationResult.Authorized(
                    fixture.authorizer.token,
                    emptySet(),
                )
            assertTrue(
                fixture.coordinator.export(WORK_DATE) is
                    GoogleSheetsExportOperationResult.Success,
            )
            assertTrue(fixture.repository.readConnection().isConnected)
            assertEquals(
                "person@example.com",
                fixture.authorizer.requestedAccountIds.last(),
            )
        }

    @Test
    fun gatewayFailuresRemainTypedAndRetryableWithoutConnectionMutation() =
        runTest {
            val fixture = Fixture()
            fixture.connect()
            val cases =
                listOf(
                    GoogleSheetsGatewayExportResult.Offline to
                        GoogleSheetsExportFailure.OFFLINE,
                    GoogleSheetsGatewayExportResult.PermissionDenied to
                        GoogleSheetsExportFailure.PERMISSION_DENIED,
                    GoogleSheetsGatewayExportResult.RateLimited(30) to
                        GoogleSheetsExportFailure.RATE_LIMITED,
                    GoogleSheetsGatewayExportResult.AmbiguousRemoteResult to
                        GoogleSheetsExportFailure.AMBIGUOUS_REMOTE_RESULT,
                )

            cases.forEach { (gatewayResult, expected) ->
                fixture.gateway.result = gatewayResult
                assertEquals(
                    GoogleSheetsExportOperationResult.Failed(expected),
                    fixture.coordinator.export(WORK_DATE),
                )
                assertTrue(fixture.repository.readConnection().isConnected)
            }
        }

    @Test
    fun ownershipConflictIncludesExactTabAndNeverClaimsSuccess() =
        runTest {
            val fixture = Fixture()
            fixture.connect()
            fixture.gateway.result =
                GoogleSheetsGatewayExportResult.TabNameConflict(TAB_NAME)

            assertEquals(
                GoogleSheetsExportOperationResult.Failed(
                    reason = GoogleSheetsExportFailure.TAB_NAME_CONFLICT,
                    tabName = TAB_NAME,
                ),
                fixture.coordinator.export(WORK_DATE),
            )
        }

    @Test
    fun canceledAuthorizationMakesNoGatewayCall() =
        runTest {
            val fixture = Fixture()
            fixture.connect()
            fixture.authorizer.authorizationResult =
                GoogleAuthorizationResult.Canceled

            assertEquals(
                GoogleSheetsExportOperationResult.Canceled,
                fixture.coordinator.export(WORK_DATE),
            )
            assertEquals(0, fixture.gateway.calls)
        }

    @Test
    fun snapshotNormalizationFailuresMakeNoGoogleCall() =
        runTest {
            val fixture = Fixture()
            fixture.connect()
            fixture.snapshotProvider.result =
                PrepareExportSnapshotResult.ActiveTimerChanged

            assertEquals(
                GoogleSheetsExportOperationResult.ActiveTimerChanged,
                fixture.coordinator.export(WORK_DATE),
            )
            assertEquals(0, fixture.authorizer.authorizationCalls)
            assertEquals(0, fixture.gateway.calls)
        }

    private class Fixture {
        val repository = FakeGoogleConnectionRepository()
        val snapshot =
            ExportSnapshot(
                schemaVersion = ExportSchema.VERSION,
                workDate = WORK_DATE,
                exportedAt = NOW,
                rows =
                    listOf(
                        ExportRow(
                            listOf(
                                "07/24/2026",
                                "07/24/2026",
                                "Employee",
                                "Client",
                                "Task",
                                "",
                                "On-Site",
                                "Billable",
                                "",
                                "09:00 AM",
                                "10:00 AM",
                                "01:00:00",
                                "60",
                            ),
                        ),
                    ),
            )
        val snapshotProvider = FakeSnapshotProvider(snapshot)
        val authorizer = FakeAuthorizer()
        val gateway = FakeGateway()
        val coordinator =
            GoogleSheetsExportCoordinator(
                authorizer = authorizer,
                gateway = gateway,
                connectionRepository = repository,
                snapshotProvider = snapshotProvider,
            )

        suspend fun connect() {
            repository.saveSignedInAccount(
                GoogleAccountHint(
                    id = "person@example.com",
                    displayName = "Person",
                ),
            )
            repository.saveConnectedSpreadsheet(
                spreadsheetId = SPREADSHEET_ID,
                spreadsheetTitle = "Work Log",
                validatedAt = NOW,
            )
        }
    }

    private class FakeSnapshotProvider(
        snapshot: ExportSnapshot,
    ) : ExportSnapshotProvider {
        var result: PrepareExportSnapshotResult =
            PrepareExportSnapshotResult.Ready(snapshot)
        var calls = 0

        override suspend fun prepare(
            workDate: LocalDate,
        ): PrepareExportSnapshotResult {
            calls += 1
            assertEquals(WORK_DATE, workDate)
            return result
        }
    }

    private class FakeAuthorizer : GoogleAccountAuthorizer {
        val token = GoogleAccessToken.from("test-token")
        var authorizationResult: GoogleAuthorizationResult =
            GoogleAuthorizationResult.Authorized(token, emptySet())
        var authorizationCalls = 0
        var clearedTokens = 0
        val requestedAccountIds = mutableListOf<String>()

        override suspend fun signIn(): GoogleSignInResult =
            error("Not used by export")

        override suspend fun authorizeSpreadsheet(
            spreadsheetId: String,
        ): GoogleAuthorizationResult = error("Not used by export")

        override suspend fun authorizeConnectedSpreadsheet(
            accountId: String,
        ): GoogleAuthorizationResult {
            authorizationCalls += 1
            requestedAccountIds += accountId
            return authorizationResult
        }

        override suspend fun clearToken(accessToken: GoogleAccessToken) {
            assertSame(token, accessToken)
            clearedTokens += 1
        }

        override suspend fun signOut(
            accountId: String?,
        ): GoogleSignOutResult =
            error("Not used by export")
    }

    private class FakeGateway : GoogleSheetsExportGateway {
        var result: GoogleSheetsGatewayExportResult =
            GoogleSheetsGatewayExportResult.Success
        var calls = 0
        var receivedSnapshot: ExportSnapshot? = null
        var receivedSpreadsheetId: String? = null

        override suspend fun exportSnapshot(
            accessToken: GoogleAccessToken,
            spreadsheetId: String,
            snapshot: ExportSnapshot,
        ): GoogleSheetsGatewayExportResult {
            calls += 1
            receivedSpreadsheetId = spreadsheetId
            receivedSnapshot = snapshot
            return result
        }
    }

    private companion object {
        val WORK_DATE: LocalDate = LocalDate.of(2026, 7, 24)
        val NOW: Instant = Instant.parse("2026-07-24T18:00:00Z")
        const val SPREADSHEET_ID =
            "1AbCdEfGhIjKlMnOpQrStUvWxYz_123456789"
        const val TAB_NAME = "WorqOrder_2026-07-24"
    }
}
