package worq.order.timer.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import worq.order.app.WorqOrderApplication

class RunningTimerNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS) return
        val intervalId = intent.getStringExtra(EXTRA_INTERVAL_ID)?.takeIf(String::isNotBlank) ?: return
        val application = context.applicationContext as? WorqOrderApplication ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                application.container.runningTimerNotificationController
                    .recordDismissal(intervalId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DISMISS = "worq.order.action.DISMISS_RUNNING_TIMER_NOTIFICATION"
        const val EXTRA_INTERVAL_ID = "running_timer_interval_id"
    }
}
