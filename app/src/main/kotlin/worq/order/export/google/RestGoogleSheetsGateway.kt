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
import org.json.JSONException
import org.json.JSONObject

class RestGoogleSheetsGateway(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GoogleSheetsGateway {
    override suspend fun validateEditableSpreadsheet(
        accessToken: GoogleAccessToken,
        spreadsheetId: String,
    ): GoogleSpreadsheetValidationResult =
        withContext(ioDispatcher) {
            validateOnIo(accessToken, spreadsheetId)
        }

    private fun validateOnIo(
        accessToken: GoogleAccessToken,
        spreadsheetId: String,
    ): GoogleSpreadsheetValidationResult {
        val driveResponse =
            executeGet(
                url =
                    "$DRIVE_FILES_ENDPOINT/$spreadsheetId" +
                        "?fields=$DRIVE_FIELDS",
                accessToken = accessToken,
            )
        driveResponse.failureOrNull()?.let { return it }
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
            executeGet(
                url =
                    "$SHEETS_ENDPOINT/$spreadsheetId" +
                        "?includeGridData=false&fields=$SHEETS_FIELDS",
                accessToken = accessToken,
            )
        sheetsResponse.failureOrNull()?.let { return it }
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

    private fun executeGet(
        url: String,
        accessToken: GoogleAccessToken,
    ): HttpResponse =
        try {
            val connection =
                (URL(url).openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = NETWORK_TIMEOUT_MILLIS
                    readTimeout = NETWORK_TIMEOUT_MILLIS
                    instanceFollowRedirects = false
                    setRequestProperty(
                        "Authorization",
                        accessToken.bearerHeader(),
                    )
                    setRequestProperty("Accept", "application/json")
                }
            try {
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
            HttpResponse(networkFailure = NetworkFailure.TIMEOUT)
        } catch (_: IOException) {
            HttpResponse(networkFailure = NetworkFailure.OFFLINE)
        } catch (_: SecurityException) {
            HttpResponse(networkFailure = NetworkFailure.OFFLINE)
        }

    private fun HttpResponse.failureOrNull():
        GoogleSpreadsheetValidationResult? {
        networkFailure?.let { failure ->
            return when (failure) {
                NetworkFailure.OFFLINE ->
                    GoogleSpreadsheetValidationResult.Offline
                NetworkFailure.TIMEOUT ->
                    GoogleSpreadsheetValidationResult.Timeout
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
        const val SHEETS_FIELDS =
            "spreadsheetId,properties(title),sheets(properties(sheetId,title))"
        private const val DRIVE_FILES_ENDPOINT =
            "https://www.googleapis.com/drive/v3/files"
        private const val SHEETS_ENDPOINT =
            "https://sheets.googleapis.com/v4/spreadsheets"
        private const val NETWORK_TIMEOUT_MILLIS = 15_000
        private const val MAX_RESPONSE_CHARACTERS = 65_536
    }
}
