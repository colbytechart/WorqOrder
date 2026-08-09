package worq.order.timer.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import worq.order.app.WorqOrderApplication

/** One-shot, post-unlock reconstruction. It never schedules ticks or background work. */
class RunningTimerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val application = context.applicationContext as? WorqOrderApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { application.container.timerRecoveryCoordinator.recover() }
                runCatching {
                    application.container.runningTimerNotificationController.reconcile()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
