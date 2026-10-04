package ly.mahd.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ly.mahd.app.MainActivity
import ly.mahd.app.R
import ly.mahd.app.util.AppLocale

object Notifications {
    private const val CHANNEL_ID = "reminders"

    /** Extras of the "open the app" intent: which profile and which tab the reminder is about. */
    const val EXTRA_PROFILE_ID = "ly.mahd.app.extra.PROFILE_ID"
    const val EXTRA_ROUTE = "ly.mahd.app.extra.ROUTE"

    fun createChannel(context: Context) {
        val res = AppLocale.wrap(context).resources
        val channel = NotificationChannel(
            CHANNEL_ID,
            res.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = res.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Opens this app's notification settings, where the mother can turn reminders on herself. */
    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Shows a reminder; tapping it opens [route] (a tab) for the profile [profileId].
     * Returns true if the notification was shown (false when notifications are blocked).
     */
    fun post(context: Context, key: String, title: String, text: String, profileId: Long, route: String): Boolean {
        if (!canPost(context)) return false
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PROFILE_ID, profileId)
            .putExtra(EXTRA_ROUTE, route)
        // One request code per reminder, so each notification keeps its own profile and tab.
        val open = PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // What a locked phone shows when it is set to hide sensitive notifications: no child's name and
        // no pregnancy week, which could reveal a pregnancy to anyone glancing at the phone.
        val lockScreenVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(AppLocale.wrap(context).resources.getString(R.string.notify_private_title))
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call; nothing to do.
            false
        }
    }
}
