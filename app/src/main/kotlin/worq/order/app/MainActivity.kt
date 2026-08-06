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
                (application as WorqOrderApplication)
                    .container
                    .timerRecoveryCoordinator
                    .recover()
                (application as WorqOrderApplication)
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

    companion object {
        const val ACTION_OPEN_PENDING_GOOGLE_EXPORT =
            "worq.order.action.OPEN_PENDING_GOOGLE_EXPORT"
    }
}
