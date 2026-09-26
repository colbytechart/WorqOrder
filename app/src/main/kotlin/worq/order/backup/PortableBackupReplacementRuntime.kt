package worq.order.backup

import worq.order.export.automatic.AutomaticGoogleExportAttentionNotifier
import worq.order.export.automatic.AutomaticGoogleExportWorkScheduler
import worq.order.timer.notification.RunningTimerNotificationController

/**
 * Local-only runtime cleanup for a portable replacement. It deliberately has no remote export,
 * Google account, or spreadsheet operation capability.
 */
class LocalPortableBackupReplacementRuntime(
    private val automaticScheduler: AutomaticGoogleExportWorkScheduler,
    private val automaticAttentionNotifier: AutomaticGoogleExportAttentionNotifier,
    private val runningTimerNotifications: RunningTimerNotificationController,
    private val clearLiveTimerSession: () -> Unit,
    private val reconcileAutomaticScheduleOnly: suspend () -> Unit,
) : PortableBackupReplacementRuntime {
    override suspend fun resetForReplacement() {
        automaticScheduler.cancel()
        automaticAttentionNotifier.cancel()
        clearLiveTimerSession()
        runningTimerNotifications.reconcile()
    }

    override suspend fun reconcileAfterRollback() {
        automaticScheduler.cancel()
        automaticAttentionNotifier.cancel()
        clearLiveTimerSession()
        runningTimerNotifications.reconcile()
        // This reconciles only persisted schedule metadata; it cannot run a Google export.
        reconcileAutomaticScheduleOnly()
    }
}
