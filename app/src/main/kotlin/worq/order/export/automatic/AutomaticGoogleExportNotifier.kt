package worq.order.export.automatic

import android.annotation.SuppressLint
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

interface AutomaticGoogleExportAttentionNotifier {
    fun requiresRuntimePermission(): Boolean

    fun notificationsDisabledInSettings(): Boolean

    fun postAttentionRequired()

    fun cancel()
}

class AutomaticGoogleExportNotifier(
    private val context: Context,
) : AutomaticGoogleExportAttentionNotifier {
    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pending_google_export_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.pending_google_export_channel_description)
            },
        )
    }

    override fun requiresRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED

    override fun notificationsDisabledInSettings(): Boolean {
        val notificationsDisabled = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelDisabled =
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
                ?.importance == NotificationManager.IMPORTANCE_NONE
        return notificationsDisabled || channelDisabled
    }

    @SuppressLint("MissingPermission")
    override fun postAttentionRequired() {
        if (requiresRuntimePermission() || notificationsDisabledInSettings()) return
        createChannel()
        val intent =
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PENDING_GOOGLE_EXPORT)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.pending_google_export_title))
                .setContentText(context.getString(R.string.pending_google_export_message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    override fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "pending_google_export"
        const val NOTIFICATION_ID = 2601
        const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    }
}
