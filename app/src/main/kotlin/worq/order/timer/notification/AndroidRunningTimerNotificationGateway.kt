package worq.order.timer.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import worq.order.R
import worq.order.app.MainActivity

class AndroidRunningTimerNotificationGateway(
    private val context: Context,
) : RunningTimerNotificationGateway {
    override fun availability(): RunningTimerNotificationAvailability {
        createChannel()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return RunningTimerNotificationAvailability.RUNTIME_PERMISSION_REQUIRED
        }
        val manager = NotificationManagerCompat.from(context)
        val channelDisabled =
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
                ?.importance == NotificationManager.IMPORTANCE_NONE
        return if (!manager.areNotificationsEnabled() || channelDisabled) {
            RunningTimerNotificationAvailability.DISABLED_IN_SETTINGS
        } else {
            RunningTimerNotificationAvailability.AVAILABLE
        }
    }

    @SuppressLint("MissingPermission")
    override fun post(content: RunningTimerNotificationContent): Boolean {
        if (availability() != RunningTimerNotificationAvailability.AVAILABLE) return false
        val openApp =
            PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                Intent(context, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_RUNNING_TIMER)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val deleteIntent =
            PendingIntent.getBroadcast(
                context,
                DISMISS_REQUEST_CODE,
                Intent(context, RunningTimerNotificationDismissReceiver::class.java)
                    .setAction(RunningTimerNotificationDismissReceiver.ACTION_DISMISS)
                    .putExtra(
                        RunningTimerNotificationDismissReceiver.EXTRA_INTERVAL_ID,
                        content.intervalId,
                    ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val publicVersion =
            notificationBuilder(
                contentTitle = context.getString(R.string.app_name),
                contentText = null,
                chronometerBaseEpochMillis = content.chronometerBaseEpochMillis,
            ).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
        val notification =
            notificationBuilder(
                contentTitle = context.getString(R.string.app_name),
                contentText =
                    context.getString(
                        R.string.running_timer_notification_private_text,
                        content.clientName,
                        content.taskDescription,
                    ),
                chronometerBaseEpochMillis = content.chronometerBaseEpochMillis,
            ).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setContentIntent(openApp)
                .setDeleteIntent(deleteIntent)
                .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    override fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun notificationBuilder(
        contentTitle: String,
        contentText: String?,
        chronometerBaseEpochMillis: Long,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setWhen(chronometerBaseEpochMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(false)
            .setOngoing(false)

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.running_timer_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description =
                    context.getString(R.string.running_timer_notification_channel_description)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "running_timer"
        const val NOTIFICATION_ID = 2801
        private const val OPEN_APP_REQUEST_CODE = 2801
        private const val DISMISS_REQUEST_CODE = 2802
    }
}
