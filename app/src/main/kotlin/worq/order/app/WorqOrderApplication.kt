package worq.order.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorqOrderApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: ApplicationContainer by lazy {
        DefaultApplicationContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { container.timerRecoveryCoordinator.recover() }
            runCatching { container.runningTimerNotificationController.reconcile() }
            runCatching { container.automaticGoogleExportManager.reconcile() }
        }
    }
}
