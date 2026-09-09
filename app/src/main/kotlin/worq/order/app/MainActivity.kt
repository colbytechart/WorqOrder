package worq.order.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import worq.order.timer.TimerRecoveryResult

class MainActivity : ComponentActivity() {
    private var openPendingGoogleExport by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPendingGoogleExport = intent?.action == ACTION_OPEN_PENDING_GOOGLE_EXPORT
        setContent {
            WorqOrderRoot(
                openPendingGoogleExport = openPendingGoogleExport,
                onPendingGoogleExportOpened = { openPendingGoogleExport = false },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_PENDING_GOOGLE_EXPORT) {
            openPendingGoogleExport = true
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                val worqOrderApplication = application as WorqOrderApplication
                val recovery =
                    worqOrderApplication
                        .container
                        .timerRecoveryCoordinator
                        .recover()
                if (recovery is TimerRecoveryResult.ClosedAtBoundary) {
                    worqOrderApplication.container.automaticGoogleExportManager.onTimerStopped()
                }
                runCatching {
                    worqOrderApplication
                        .container
                        .runningTimerNotificationController
                        .reconcile()
                }
                worqOrderApplication
                    .container
                    .automaticGoogleExportManager
                    .reconcile()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Main retries and presents the recoverable local-data error. Never crash or
                // alter the persisted active interval because foreground recovery failed.
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        lifecycleScope.launch {
            runCatching {
                (application as WorqOrderApplication)
                    .container
                    .runningTimerNotificationController
                    .reconcile()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_PENDING_GOOGLE_EXPORT =
            "worq.order.action.OPEN_PENDING_GOOGLE_EXPORT"
        const val ACTION_OPEN_RUNNING_TIMER =
            "worq.order.action.OPEN_RUNNING_TIMER"
    }
}
