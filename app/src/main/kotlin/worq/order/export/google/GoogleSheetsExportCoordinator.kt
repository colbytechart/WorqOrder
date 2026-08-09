package worq.order.export.google

import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import worq.order.data.GoogleConnectionRepository
import worq.order.export.ExportSnapshotProvider
import worq.order.export.PrepareExportSnapshotResult

class GoogleSheetsExportCoordinator(
    private val authorizer: GoogleAccountAuthorizer,
    private val gateway: GoogleSheetsExportGateway,
    private val connectionRepository: GoogleConnectionRepository,
    private val snapshotProvider: ExportSnapshotProvider,
    private val operationMutex: Mutex = Mutex(),
) {
    suspend fun export(workDate: LocalDate): GoogleSheetsExportOperationResult =
        operationMutex.withLock { exportLocked(workDate) }

    private suspend fun exportLocked(workDate: LocalDate): GoogleSheetsExportOperationResult {
        val connection =
            localResult { connectionRepository.readConnection() }
                .getOrElse {
                    return failed(GoogleSheetsExportFailure.LOCAL_STORAGE)
                }
        if (!connection.hasAccountHint || !connection.hasSpreadsheetMetadata) {
            return GoogleSheetsExportOperationResult.SetupRequired
        }
        if (!connection.isConnected) {
            return GoogleSheetsExportOperationResult.AuthorizationRequired
        }
        val spreadsheetId =
            connection.spreadsheetId
                ?: return GoogleSheetsExportOperationResult.SetupRequired
        val spreadsheetTitle =
            connection.spreadsheetTitle
                ?: return GoogleSheetsExportOperationResult.SetupRequired
        val snapshot =
            when (val result = snapshotProvider.prepare(workDate)) {
                PrepareExportSnapshotResult.ActiveTimerChanged ->
                    return GoogleSheetsExportOperationResult.ActiveTimerChanged
                PrepareExportSnapshotResult.ClockChanged ->
                    return GoogleSheetsExportOperationResult.ClockChanged
                is PrepareExportSnapshotResult.Ready -> result.snapshot
            }
        val authorization =
            when (val result = authorizer.authorizeConnectedSpreadsheet()) {
                is GoogleAuthorizationResult.Authorized -> result
                GoogleAuthorizationResult.Canceled ->
                    return GoogleSheetsExportOperationResult.Canceled
                GoogleAuthorizationResult.AuthorizationRequired -> {
                    markAuthorizationRequired()
                    return GoogleSheetsExportOperationResult.AuthorizationRequired
                }
                GoogleAuthorizationResult.PlayServicesUnavailable ->
                    return failed(
                        GoogleSheetsExportFailure.PLAY_SERVICES_UNAVAILABLE,
                    )
                GoogleAuthorizationResult.Failed -> {
                    markAuthorizationRequired()
                    return GoogleSheetsExportOperationResult.AuthorizationRequired
                }
            }
        return when (
            val result =
                gateway.exportSnapshot(
                    accessToken = authorization.accessToken,
                    spreadsheetId = spreadsheetId,
                    snapshot = snapshot,
                )
        ) {
            GoogleSheetsGatewayExportResult.Success ->
                GoogleSheetsExportOperationResult.Success(
                    GoogleSheetExportReceipt(
                        workDate = snapshot.workDate,
                        exportedAt = snapshot.exportedAt,
                        spreadsheetId = spreadsheetId,
                        spreadsheetTitle = spreadsheetTitle,
                        tabName =
                            GoogleSheetsExportPlanner.tabName(
                                snapshot.workDate.toString(),
                            ),
                        dataRowCount = snapshot.rows.size,
                    ),
                )
            is GoogleSheetsGatewayExportResult.TabNameConflict ->
                failed(
                    reason = GoogleSheetsExportFailure.TAB_NAME_CONFLICT,
                    tabName = result.tabName,
                )
            is GoogleSheetsGatewayExportResult.SchemaConflict ->
                failed(
                    reason = GoogleSheetsExportFailure.SCHEMA_CONFLICT,
                    tabName = result.tabName,
                )
            GoogleSheetsGatewayExportResult.Offline ->
                failed(GoogleSheetsExportFailure.OFFLINE)
            GoogleSheetsGatewayExportResult.Timeout ->
                failed(GoogleSheetsExportFailure.TIMEOUT)
            GoogleSheetsGatewayExportResult.Unauthorized -> {
                try {
                    authorizer.clearToken(authorization.accessToken)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                }
                markAuthorizationRequired()
                GoogleSheetsExportOperationResult.AuthorizationRequired
            }
            GoogleSheetsGatewayExportResult.NotFoundOrNotGranted ->
                failed(GoogleSheetsExportFailure.NOT_FOUND_OR_NOT_GRANTED)
            GoogleSheetsGatewayExportResult.PermissionDenied ->
                failed(GoogleSheetsExportFailure.PERMISSION_DENIED)
            is GoogleSheetsGatewayExportResult.RateLimited ->
                failed(GoogleSheetsExportFailure.RATE_LIMITED)
            GoogleSheetsGatewayExportResult.ServerFailure ->
                failed(GoogleSheetsExportFailure.SERVER_FAILURE)
            GoogleSheetsGatewayExportResult.MalformedResponse ->
                failed(GoogleSheetsExportFailure.MALFORMED_RESPONSE)
            GoogleSheetsGatewayExportResult.AmbiguousRemoteResult ->
                failed(GoogleSheetsExportFailure.AMBIGUOUS_REMOTE_RESULT)
        }
    }

    private suspend fun markAuthorizationRequired() {
        localResult { connectionRepository.markAuthorizationRequired() }
    }

    private fun failed(
        reason: GoogleSheetsExportFailure,
        tabName: String? = null,
    ): GoogleSheetsExportOperationResult =
        GoogleSheetsExportOperationResult.Failed(reason, tabName)

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
