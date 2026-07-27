package worq.order.export.google

import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import worq.order.export.ExportSnapshot

class RestGoogleSheetsGateway(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GoogleSheetsGateway,
    GoogleSheetsExportGateway {
    override suspend fun validateEditableSpreadsheet(
        accessToken: GoogleAccessToken,
        spreadsheetId: String,
    ): GoogleSpreadsheetValidationResult =
        withContext(ioDispatcher) {
            validateOnIo(accessToken, spreadsheetId)
        }

    override suspend fun exportSnapshot(
        accessToken: GoogleAccessToken,
        spreadsheetId: String,
        snapshot: ExportSnapshot,
    ): GoogleSheetsGatewayExportResult =
        withContext(ioDispatcher) {
            exportOnIo(
                accessToken = accessToken,
                spreadsheetId = spreadsheetId,
                snapshot = snapshot,
            )
        }

    private fun validateOnIo(
        accessToken: GoogleAccessToken,
        spreadsheetId: String,
    ): GoogleSpreadsheetValidationResult {
        val driveResponse =
            execute(
                method = "GET",
                url =
                    "$DRIVE_FILES_ENDPOINT/$spreadsheetId" +
                        "?fields=$DRIVE_FIELDS",
                accessToken = accessToken,
            )
        driveResponse.validationFailureOrNull()?.let { return it }
        val driveMetadata =
            parseDriveMetadata(driveResponse.body)
                ?: return GoogleSpreadsheetValidationResult.MalformedResponse
        if (driveMetadata.id != spreadsheetId) {
            return GoogleSpreadsheetValidationResult.MalformedResponse
        }
        if (driveMetadata.mimeType != GOOGLE_SHEETS_MIME_TYPE) {
            return GoogleSpreadsheetValidationResult.NotGoogleSpreadsheet
        }
        if (driveMetadata.trashed) {
            return GoogleSpreadsheetValidationResult.NotFoundOrNotGranted
        }
        if (!driveMetadata.canEdit) {
            return GoogleSpreadsheetValidationResult.ReadOnly
        }
        if (driveMetadata.canModifyContent == false) {
            return GoogleSpreadsheetValidationResult
                .ContentModificationRestricted
        }

        val sheetsResponse =
            execute(
                method = "GET",
                url =
                    "$SHEETS_ENDPOINT/$spreadsheetId" +
                        "?includeGridData=false&fields=$VALIDATION_SHEETS_FIELDS",
                accessToken = accessToken,
            )
        sheetsResponse.validationFailureOrNull()?.let { return it }
        return parseSheetsMetadata(sheetsResponse.body)
            ?.takeIf { it.id == spreadsheetId }
            ?.let {
                GoogleSpreadsheetValidationResult.Success(
                    ValidatedGoogleSpreadsheet(
                        id = it.id,
                        title = it.title,
                    ),
                )
            }
            ?: GoogleSpreadsheetValidationResult.MalformedResponse
    }

    private fun exportOnIo(
        accessToken: GoogleAccessToken,
        spreadsheetId: String,
        snapshot: ExportSnapshot,
    ): GoogleSheetsGatewayExportResult {
        val structureResponse =
            execute(
                method = "GET",
                url =
                    "$SHEETS_ENDPOINT/$spreadsheetId" +
                        "?includeGridData=false&fields=$EXPORT_STRUCTURE_FIELDS",
                accessToken = accessToken,
            )
        structureResponse.exportFailureOrNull()?.let { return it }
        val structure =
            parseSpreadsheetStructure(structureResponse.body)
                ?: return GoogleSheetsGatewayExportResult.MalformedResponse
        if (structure.spreadsheetId != spreadsheetId) {
            return GoogleSheetsGatewayExportResult.MalformedResponse
        }
        val inspectedStructure =
            if (
                structure.sheets.none {
                    it.title ==
                        GoogleSheetsExportPlanner.tabName(
                            snapshot.workDate.toString(),
                        )
                } &&
                structure.developerMetadata.isEmpty()
            ) {
                val blanknessResponse =
                    execute(
                        method = "GET",
                        url =
                            "$SHEETS_ENDPOINT/$spreadsheetId" +
                                "?includeGridData=true&fields=" +
                                BLANKNESS_FIELDS,
                        accessToken = accessToken,
                    )
                blanknessResponse.exportFailureOrNull()?.let { return it }
                val isCompletelyBlank =
                    parseSpreadsheetBlankness(blanknessResponse.body) ?: false
                structure.copy(isCompletelyBlank = isCompletelyBlank)
            } else {
                structure
            }
        val plan =
            when (
                val result =
                    GoogleSheetsExportPlanner.plan(
                        spreadsheet = inspectedStructure,
                        snapshot = snapshot,
                    )
            ) {
                is GoogleSheetsPlanResult.Ready -> result.plan
                is GoogleSheetsPlanResult.TabNameConflict ->
                    return GoogleSheetsGatewayExportResult.TabNameConflict(
                        result.tabName,
                    )
                is GoogleSheetsPlanResult.SchemaConflict ->
                    return GoogleSheetsGatewayExportResult.SchemaConflict(
                        result.tabName,
                    )
            }
        val batchResponse =
            execute(
                method = "POST",
                url = "$SHEETS_ENDPOINT/$spreadsheetId:batchUpdate",
                accessToken = accessToken,
                requestBody = GoogleSheetsBatchJsonEncoder.encode(plan),
            )
        batchResponse.exportFailureOrNull()?.let { return it }
        return if (
            parseBatchUpdateSpreadsheetId(batchResponse.body) == spreadsheetId
        ) {
            GoogleSheetsGatewayExportResult.Success
        } else {
            GoogleSheetsGatewayExportResult.MalformedResponse
        }
    }

    private fun execute(
        method: String,
        url: String,
        accessToken: GoogleAccessToken,
        requestBody: String? = null,
    ): HttpResponse {
        var mutationMayHaveReachedServer = false
        return try {
            val connection =
                (URL(url).openConnection() as HttpsURLConnection).apply {
                    requestMethod = method
                    connectTimeout = NETWORK_TIMEOUT_MILLIS
                    readTimeout = NETWORK_TIMEOUT_MILLIS
                    instanceFollowRedirects = false
                    setRequestProperty(
                        "Authorization",
                        accessToken.bearerHeader(),
                    )
                    setRequestProperty("Accept", "application/json")
                    if (requestBody != null) {
                        doOutput = true
                        setRequestProperty(
                            "Content-Type",
                            "application/json; charset=UTF-8",
                        )
                    }
                }
            try {
                if (requestBody != null) {
                    val bytes = requestBody.toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { output ->
                        mutationMayHaveReachedServer = true
                        output.write(bytes)
                    }
                }
                val statusCode = connection.responseCode
                val responseStream =
                    if (statusCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                HttpResponse(
                    statusCode = statusCode,
                    body = responseStream?.readBoundedUtf8().orEmpty(),
                    retryAfterSeconds =
                        connection
                            .getHeaderField("Retry-After")
                            ?.toLongOrNull(),
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: UnknownHostException) {
            HttpResponse(networkFailure = NetworkFailure.OFFLINE)
        } catch (_: ConnectException) {
            HttpResponse(networkFailure = NetworkFailure.OFFLINE)
        } catch (_: SocketTimeoutException) {
            HttpResponse(
                networkFailure =
                    if (mutationMayHaveReachedServer) {
                        NetworkFailure.AMBIGUOUS
                    } else {
                        NetworkFailure.TIMEOUT
                    },
            )
        } catch (_: IOException) {
            HttpResponse(
                networkFailure =
                    if (mutationMayHaveReachedServer) {
                        NetworkFailure.AMBIGUOUS
                    } else {
                        NetworkFailure.OFFLINE
                    },
            )
        } catch (_: SecurityException) {
            HttpResponse(networkFailure = NetworkFailure.OFFLINE)
        }
    }

    private fun HttpResponse.validationFailureOrNull():
        GoogleSpreadsheetValidationResult? {
        networkFailure?.let { failure ->
            return when (failure) {
                NetworkFailure.OFFLINE ->
                    GoogleSpreadsheetValidationResult.Offline
                NetworkFailure.TIMEOUT,
                NetworkFailure.AMBIGUOUS,
                -> GoogleSpreadsheetValidationResult.Timeout
            }
        }
        return when (statusCode) {
            in 200..299 -> null
            401 -> GoogleSpreadsheetValidationResult.Unauthorized
            403 -> GoogleSpreadsheetValidationResult.WorkspacePolicyBlocked
            404 -> GoogleSpreadsheetValidationResult.NotFoundOrNotGranted
            408 -> GoogleSpreadsheetValidationResult.Timeout
            429 ->
                GoogleSpreadsheetValidationResult.RateLimited(
                    retryAfterSeconds,
                )
            in 500..599 -> GoogleSpreadsheetValidationResult.ServerFailure
            else -> GoogleSpreadsheetValidationResult.MalformedResponse
        }
    }

    private fun HttpResponse.exportFailureOrNull():
        GoogleSheetsGatewayExportResult? {
        networkFailure?.let { failure ->
            return when (failure) {
                NetworkFailure.OFFLINE ->
                    GoogleSheetsGatewayExportResult.Offline
                NetworkFailure.TIMEOUT ->
                    GoogleSheetsGatewayExportResult.Timeout
                NetworkFailure.AMBIGUOUS ->
                    GoogleSheetsGatewayExportResult.AmbiguousRemoteResult
            }
        }
        return when (statusCode) {
            in 200..299 -> null
            401 -> GoogleSheetsGatewayExportResult.Unauthorized
            403 ->
                if (body.indicatesRateLimit()) {
                    GoogleSheetsGatewayExportResult.RateLimited(
                        retryAfterSeconds,
                    )
                } else {
                    GoogleSheetsGatewayExportResult.PermissionDenied
                }
            404 -> GoogleSheetsGatewayExportResult.NotFoundOrNotGranted
            408 -> GoogleSheetsGatewayExportResult.Timeout
            429 ->
                GoogleSheetsGatewayExportResult.RateLimited(
                    retryAfterSeconds,
                )
            in 500..599 -> GoogleSheetsGatewayExportResult.ServerFailure
            else -> GoogleSheetsGatewayExportResult.MalformedResponse
        }
    }

    private fun String.indicatesRateLimit(): Boolean =
        contains("rateLimitExceeded") ||
            contains("userRateLimitExceeded") ||
            contains("quotaExceeded")

    private fun parseDriveMetadata(body: String): DriveMetadata? =
        try {
            val root = JSONObject(body)
            val capabilities = root.optJSONObject("capabilities") ?: return null
            val canModifyContent =
                if (capabilities.has("canModifyContent")) {
                    capabilities.optBoolean("canModifyContent")
                } else {
                    null
                }
            DriveMetadata(
                id = root.requireNonBlankString("id"),
                mimeType = root.requireNonBlankString("mimeType"),
                trashed = root.optBoolean("trashed", false),
                canEdit = capabilities.optBoolean("canEdit", false),
                canModifyContent = canModifyContent,
            )
        } catch (_: JSONException) {
            null
        }

    private fun parseSheetsMetadata(body: String): SheetsMetadata? =
        try {
            val root = JSONObject(body)
            val properties = root.optJSONObject("properties") ?: return null
            SheetsMetadata(
                id = root.requireNonBlankString("spreadsheetId"),
                title = properties.requireNonBlankString("title"),
            )
        } catch (_: JSONException) {
            null
        }

    internal fun parseSpreadsheetStructure(
        body: String,
    ): GoogleSpreadsheetStructure? =
        try {
            val root = JSONObject(body)
            val sheetsJson = root.optJSONArray("sheets") ?: JSONArray()
            val metadata = mutableListOf<GoogleSheetDeveloperMetadata>()
            val sheets =
                buildList {
                    for (index in 0 until sheetsJson.length()) {
                        val sheet = sheetsJson.getJSONObject(index)
                        val properties = sheet.getJSONObject("properties")
                        val sheetId = properties.getInt("sheetId")
                        add(
                            GoogleSheetDescriptor(
                                sheetId = sheetId,
                                title = properties.requireNonBlankString("title"),
                            ),
                        )
                        metadata +=
                            sheet
                                .optJSONArray("developerMetadata")
                                .parseDeveloperMetadata(
                                    fallbackSheetId = sheetId,
                                )
                    }
                }
            metadata +=
                root
                    .optJSONArray("developerMetadata")
                    .parseDeveloperMetadata()
            GoogleSpreadsheetStructure(
                spreadsheetId = root.requireNonBlankString("spreadsheetId"),
                sheets = sheets,
                developerMetadata = metadata.distinct(),
            )
        } catch (_: JSONException) {
            null
        }

    internal fun parseSpreadsheetBlankness(body: String): Boolean? {
        return try {
            val root = JSONObject(body)
            if (root.hasNonEmptyArray("namedRanges")) {
                false
            } else if (root.hasNonEmptyArray("developerMetadata")) {
                false
            } else {
                val sheets = root.optJSONArray("sheets") ?: JSONArray()
                var isBlank = true
                for (sheetIndex in 0 until sheets.length()) {
                    if (
                        sheets
                            .getJSONObject(sheetIndex)
                            .hasSheetLevelContent()
                    ) {
                        isBlank = false
                        break
                    }
                }
                isBlank
            }
        } catch (_: JSONException) {
            null
        }
    }

    private fun JSONObject.hasSheetLevelContent(): Boolean {
        if (
            SHEET_COLLECTION_CONTENT_FIELDS.any { hasNonEmptyArray(it) } ||
            optJSONObject("basicFilter")?.length()?.let { it > 0 } == true
        ) {
            return true
        }
        val gridData = optJSONArray("data") ?: return false
        for (gridIndex in 0 until gridData.length()) {
            val rows =
                gridData
                    .getJSONObject(gridIndex)
                    .optJSONArray("rowData") ?: continue
            for (rowIndex in 0 until rows.length()) {
                val values =
                    rows
                        .getJSONObject(rowIndex)
                        .optJSONArray("values") ?: continue
                for (cellIndex in 0 until values.length()) {
                    if (values.getJSONObject(cellIndex).length() > 0) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun JSONObject.hasNonEmptyArray(key: String): Boolean =
        optJSONArray(key)?.length()?.let { it > 0 } == true

    private fun JSONArray?.parseDeveloperMetadata(
        fallbackSheetId: Int? = null,
    ): List<GoogleSheetDeveloperMetadata> {
        if (this == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                val location = item.optJSONObject("location")
                val sheetId =
                    when {
                        location?.has("sheetId") == true ->
                            location.getInt("sheetId")
                        fallbackSheetId != null -> fallbackSheetId
                        else -> continue
                    }
                add(
                    GoogleSheetDeveloperMetadata(
                        sheetId = sheetId,
                        key = item.requireNonBlankString("metadataKey"),
                        value = item.requireNonBlankString("metadataValue"),
                    ),
                )
            }
        }
    }

    private fun parseBatchUpdateSpreadsheetId(body: String): String? =
        try {
            JSONObject(body).requireNonBlankString("spreadsheetId")
        } catch (_: JSONException) {
            null
        }

    private fun JSONObject.requireNonBlankString(key: String): String =
        getString(key).trim().also {
            if (it.isEmpty()) {
                throw JSONException("$key is blank")
            }
        }

    private fun InputStream.readBoundedUtf8(): String =
        bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4_096)
            while (result.length < MAX_RESPONSE_CHARACTERS) {
                val remaining = MAX_RESPONSE_CHARACTERS - result.length
                val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) {
                    break
                }
                result.append(buffer, 0, count)
            }
            result.toString()
        }

    private data class HttpResponse(
        val statusCode: Int = 0,
        val body: String = "",
        val retryAfterSeconds: Long? = null,
        val networkFailure: NetworkFailure? = null,
    )

    private enum class NetworkFailure {
        OFFLINE,
        TIMEOUT,
        AMBIGUOUS,
    }

    private data class DriveMetadata(
        val id: String,
        val mimeType: String,
        val trashed: Boolean,
        val canEdit: Boolean,
        val canModifyContent: Boolean?,
    )

    private data class SheetsMetadata(
        val id: String,
        val title: String,
    )

    companion object {
        const val GOOGLE_SHEETS_MIME_TYPE =
            "application/vnd.google-apps.spreadsheet"
        const val DRIVE_FILE_SCOPE =
            "https://www.googleapis.com/auth/drive.file"
        const val DRIVE_FIELDS =
            "id,name,mimeType,trashed,capabilities(canEdit,canModifyContent)"
        const val VALIDATION_SHEETS_FIELDS =
            "spreadsheetId,properties(title),sheets(properties(sheetId,title))"
        const val EXPORT_STRUCTURE_FIELDS =
            "spreadsheetId,sheets(properties(sheetId,title," +
                "gridProperties(rowCount,columnCount))," +
                "developerMetadata(metadataId,metadataKey,metadataValue," +
                "location(sheetId)))," +
                "developerMetadata(metadataId,metadataKey,metadataValue," +
                "location(sheetId))"
        const val BLANKNESS_FIELDS =
            "namedRanges,developerMetadata," +
                "sheets(data(rowData(values(userEnteredValue," +
                "userEnteredFormat,note,dataValidation,textFormatRuns)))," +
                "merges,conditionalFormats,filterViews,protectedRanges," +
                "basicFilter,charts,bandedRanges,developerMetadata," +
                "rowGroups,columnGroups,slicers,tables)"
        private const val DRIVE_FILES_ENDPOINT =
            "https://www.googleapis.com/drive/v3/files"
        private const val SHEETS_ENDPOINT =
            "https://sheets.googleapis.com/v4/spreadsheets"
        private const val NETWORK_TIMEOUT_MILLIS = 15_000
        private const val MAX_RESPONSE_CHARACTERS = 1_048_576
        private val SHEET_COLLECTION_CONTENT_FIELDS =
            listOf(
                "merges",
                "conditionalFormats",
                "filterViews",
                "protectedRanges",
                "charts",
                "bandedRanges",
                "developerMetadata",
                "rowGroups",
                "columnGroups",
                "slicers",
                "tables",
            )
    }
}
