package worq.order.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import worq.order.R
import worq.order.backup.PortableBackupStartupRecoveryResult
import worq.order.timer.TimerRecoveryResult
import worq.order.ui.theme.WorqOrderTheme

class MainActivity : ComponentActivity() {
    private var openPendingGoogleExport by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPendingGoogleExport = intent?.action == ACTION_OPEN_PENDING_GOOGLE_EXPORT
        lifecycleScope.launch {
            when ((application as WorqOrderApplication).awaitStartupRecovery()) {
                PortableBackupStartupRecoveryResult.Blocked ->
                    setContent { PortableRecoveryBlockedScreen() }
                PortableBackupStartupRecoveryResult.NoRecoveryNeeded,
                PortableBackupStartupRecoveryResult.Recovered,
                ->
                    setContent {
                        WorqOrderRoot(
                            openPendingGoogleExport = openPendingGoogleExport,
                            onPendingGoogleExportOpened = { openPendingGoogleExport = false },
                        )
                    }
            }
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
                if (
                    worqOrderApplication.awaitStartupRecovery() is
                    PortableBackupStartupRecoveryResult.Blocked
                ) return@launch
                worqOrderApplication.container.withApplicationDataOperationLock {
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
                }
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
            val worqOrderApplication = application as WorqOrderApplication
            if (
                worqOrderApplication.awaitStartupRecovery() is
                PortableBackupStartupRecoveryResult.Blocked
            ) return@launch
            runCatching {
                worqOrderApplication
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

@Composable
private fun PortableRecoveryBlockedScreen() {
    WorqOrderTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.portable_recovery_blocked),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
