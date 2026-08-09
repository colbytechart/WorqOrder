package worq.order.export.google

import java.time.Instant
import java.time.LocalDate
import worq.order.export.ExportSnapshot

data class GoogleSheetExportReceipt(
    val workDate: LocalDate,
    val exportedAt: Instant,
    val spreadsheetId: String,
    val spreadsheetTitle: String,
    val tabName: String,
    val dataRowCount: Int,
)

sealed interface GoogleSheetsExportOperationResult {
    data class Success(
        val receipt: GoogleSheetExportReceipt,
    ) : GoogleSheetsExportOperationResult

    data object Canceled : GoogleSheetsExportOperationResult

    data object SetupRequired : GoogleSheetsExportOperationResult

    data object AuthorizationRequired : GoogleSheetsExportOperationResult

    data object ClockChanged : GoogleSheetsExportOperationResult

    data object ActiveTimerChanged : GoogleSheetsExportOperationResult

    data class Failed(
        val reason: GoogleSheetsExportFailure,
        val tabName: String? = null,
    ) : GoogleSheetsExportOperationResult
}

enum class GoogleSheetsExportFailure {
    LOCAL_STORAGE,
    PLAY_SERVICES_UNAVAILABLE,
    OFFLINE,
    TIMEOUT,
    NOT_FOUND_OR_NOT_GRANTED,
    PERMISSION_DENIED,
    RATE_LIMITED,
    SERVER_FAILURE,
    MALFORMED_RESPONSE,
    TAB_NAME_CONFLICT,
    SCHEMA_CONFLICT,
    AMBIGUOUS_REMOTE_RESULT,
}

sealed interface GoogleSheetsGatewayExportResult {
    data object Success : GoogleSheetsGatewayExportResult

    data class TabNameConflict(
        val tabName: String,
    ) : GoogleSheetsGatewayExportResult

    data class SchemaConflict(
        val tabName: String,
    ) : GoogleSheetsGatewayExportResult

    data object Offline : GoogleSheetsGatewayExportResult

    data object Timeout : GoogleSheetsGatewayExportResult

    data object Unauthorized : GoogleSheetsGatewayExportResult

    data object NotFoundOrNotGranted : GoogleSheetsGatewayExportResult

    data object PermissionDenied : GoogleSheetsGatewayExportResult

    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : GoogleSheetsGatewayExportResult

    data object ServerFailure : GoogleSheetsGatewayExportResult

    data object MalformedResponse : GoogleSheetsGatewayExportResult

    data object AmbiguousRemoteResult : GoogleSheetsGatewayExportResult
}

interface GoogleSheetsExportGateway {
    suspend fun exportSnapshot(
        accessToken: GoogleAccessToken,
        spreadsheetId: String,
        snapshot: ExportSnapshot,
    ): GoogleSheetsGatewayExportResult
}

internal data class GoogleSheetDescriptor(
    val sheetId: Int,
    val title: String,
)

internal data class GoogleSheetDeveloperMetadata(
    val sheetId: Int,
    val key: String,
    val value: String,
    val metadataId: Int? = null,
)

internal data class GoogleSpreadsheetStructure(
    val spreadsheetId: String,
    val sheets: List<GoogleSheetDescriptor>,
    val developerMetadata: List<GoogleSheetDeveloperMetadata>,
    val isCompletelyBlank: Boolean = false,
)

internal sealed interface GoogleSheetsBatchRequest {
    data class AddSheet(
        val sheetId: Int,
        val title: String,
        val rowCount: Int,
        val columnCount: Int,
    ) : GoogleSheetsBatchRequest

    data class RenameSheet(
        val sheetId: Int,
        val title: String,
    ) : GoogleSheetsBatchRequest

    data class CreateSheetMetadata(
        val sheetId: Int,
        val key: String,
        val value: String,
    ) : GoogleSheetsBatchRequest

    data class UpdateSheetMetadataValue(
        val metadataId: Int,
        val value: String,
    ) : GoogleSheetsBatchRequest

    data class ResizeSheet(
        val sheetId: Int,
        val rowCount: Int,
        val columnCount: Int,
    ) : GoogleSheetsBatchRequest

    data class ReplaceCells(
        val sheetId: Int,
        val rows: List<List<String>>,
    ) : GoogleSheetsBatchRequest
}

internal data class GoogleSheetsBatchPlan(
    val tabName: String,
    val requests: List<GoogleSheetsBatchRequest>,
)

internal sealed interface GoogleSheetsPlanResult {
    data class Ready(
        val plan: GoogleSheetsBatchPlan,
    ) : GoogleSheetsPlanResult

    data class TabNameConflict(
        val tabName: String,
    ) : GoogleSheetsPlanResult

    data class SchemaConflict(
        val tabName: String,
    ) : GoogleSheetsPlanResult
}
