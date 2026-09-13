package worq.order.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import worq.order.timer.TimerRecoveryResult

class WorqOrderApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: ApplicationContainer by lazy {
        DefaultApplicationContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val recovery =
                runCatching { container.timerRecoveryCoordinator.recover() }.getOrNull()
            if (recovery is TimerRecoveryResult.ClosedAtBoundary) {
                runCatching { container.automaticGoogleExportManager.onTimerStopped() }
            }
            runCatching { container.runningTimerNotificationController.reconcile() }
            runCatching { container.automaticGoogleExportManager.reconcile() }
        }
    }
}
