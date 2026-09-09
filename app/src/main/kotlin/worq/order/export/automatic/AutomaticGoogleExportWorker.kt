package worq.order.export.automatic

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import worq.order.app.WorqOrderApplication

class AutomaticGoogleExportWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val workDateEpochDay = inputData.getLong(KEY_WORK_DATE, Long.MIN_VALUE)
        val zoneId = inputData.getString(KEY_ZONE_ID)
        val connectionKey = inputData.getString(KEY_CONNECTION_KEY)
        if (workDateEpochDay == Long.MIN_VALUE || zoneId == null || connectionKey == null) {
            return Result.failure()
        }
        return try {
            val application = applicationContext as WorqOrderApplication
            application
                .container
                .automaticGoogleExportManager
                .runScheduled(workDateEpochDay, zoneId, connectionKey)
            runCatching {
                application.container.runningTimerNotificationController.reconcile()
            }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // There is no automatic retry. A later foreground reconcile can restore a valid
            // durable target; local data and the Google sheet remain authoritative/unmodified.
            Result.failure()
        }
    }

    companion object {
        const val KEY_WORK_DATE = "work_date_epoch_day"
        const val KEY_ZONE_ID = "zone_id"
        const val KEY_CONNECTION_KEY = "connection_key"
    }
}
