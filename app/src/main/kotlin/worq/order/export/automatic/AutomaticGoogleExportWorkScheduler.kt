package worq.order.export.automatic

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class AutomaticGoogleExportTarget(
    val workDate: LocalDate,
    val zoneId: ZoneId,
    val connectionKey: String,
)

interface AutomaticGoogleExportWorkScheduler {
    fun schedule(target: AutomaticGoogleExportTarget, now: Instant)

    fun cancel()
}

class WorkManagerAutomaticGoogleExportScheduler(
    private val workManager: WorkManager,
) : AutomaticGoogleExportWorkScheduler {
    override fun schedule(target: AutomaticGoogleExportTarget, now: Instant) {
        val due = automaticGoogleExportBoundary(target.workDate, target.zoneId)
        val delayMillis = Duration.between(now, due).toMillis().coerceAtLeast(0L)
        val input =
            Data.Builder()
                .putLong(AutomaticGoogleExportWorker.KEY_WORK_DATE, target.workDate.toEpochDay())
                .putString(AutomaticGoogleExportWorker.KEY_ZONE_ID, target.zoneId.id)
                .putString(AutomaticGoogleExportWorker.KEY_CONNECTION_KEY, target.connectionKey)
                .build()
        val request =
            OneTimeWorkRequestBuilder<AutomaticGoogleExportWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .addTag(WORK_TAG)
                .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "worqorder-automatic-google-export"
        const val WORK_TAG = "automatic-google-export"
    }
}
