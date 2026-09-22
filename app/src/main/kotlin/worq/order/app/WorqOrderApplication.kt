package worq.order.app

import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import worq.order.backup.PortableBackupStartupRecoveryResult
import worq.order.timer.TimerRecoveryResult

class WorqOrderApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startupRecovery = CompletableDeferred<PortableBackupStartupRecoveryResult>()

    val container: ApplicationContainer by lazy {
        DefaultApplicationContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // A journaled portable replacement owns startup until it has converged. Never let
            // timer, notification, or automatic-export recovery observe a mixed generation.
            val portableRecovery =
                runCatching {
                    container.portableBackupReplacementCoordinator.reconcileStartup()
                }.getOrElse { PortableBackupStartupRecoveryResult.Blocked }
            if (portableRecovery is PortableBackupStartupRecoveryResult.Blocked) {
                startupRecovery.complete(portableRecovery)
                return@launch
            }
            val recovery =
                runCatching { container.timerRecoveryCoordinator.recover() }.getOrNull()
            if (recovery is TimerRecoveryResult.ClosedAtBoundary) {
                runCatching { container.automaticGoogleExportManager.onTimerStopped() }
            }
            runCatching { container.runningTimerNotificationController.reconcile() }
            runCatching { container.automaticGoogleExportManager.reconcile() }
            startupRecovery.complete(portableRecovery)
        }
    }

    suspend fun awaitStartupRecovery(): PortableBackupStartupRecoveryResult =
        startupRecovery.await()
}
