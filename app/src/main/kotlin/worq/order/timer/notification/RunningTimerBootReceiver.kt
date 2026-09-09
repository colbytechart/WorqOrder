package worq.order.timer.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import worq.order.app.WorqOrderApplication
import worq.order.timer.TimerRecoveryResult

/** One-shot, post-unlock reconstruction. It never schedules ticks or background work. */
class RunningTimerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val application = context.applicationContext as? WorqOrderApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val recovery =
                    runCatching { application.container.timerRecoveryCoordinator.recover() }
                        .getOrNull()
                if (recovery is TimerRecoveryResult.ClosedAtBoundary) {
                    runCatching { application.container.automaticGoogleExportManager.onTimerStopped() }
                }
                runCatching {
                    application.container.runningTimerNotificationController.reconcile()
                }
                runCatching { application.container.automaticGoogleExportManager.reconcile() }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
