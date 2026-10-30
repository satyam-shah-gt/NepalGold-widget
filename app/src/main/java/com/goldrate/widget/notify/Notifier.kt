package com.goldrate.widget.notify

import android.Manifest
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
import com.goldrate.widget.R
import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.Format
import com.goldrate.widget.data.RateQuote
import com.goldrate.widget.data.withLanguage
import com.goldrate.widget.ui.ConfigActivity

/**
 * Posts the low-importance notification that fires after a successful daily fetch.
 */
object Notifier {

    const val CHANNEL_ID = "daily_rate_updates"

    private const val NOTIFICATION_ID = 1001

    /** Creates the notification channel. Safe to call repeatedly. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the "rate updated" notification.
     *
     * Silently does nothing when the user has not granted `POST_NOTIFICATIONS` (Android 13+) or
     * has turned notifications off for the channel.
     */
    fun notifySuccess(context: Context, quotes: List<RateQuote>, lang: AppLang) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)

        val localized = context.withLanguage(lang)
        val gold = quotes.firstOrNull { it.type.contains("सुन") && it.type.contains("१० ग्राम") }
            ?: quotes.firstOrNull()
        val rate = gold?.let { Format.amount(it.today, lang) } ?: Format.EM_DASH

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, ConfigActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.notification_title))
            .setContentText(localized.getString(R.string.notification_text, rate))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the call - nothing to do.
        }
    }

    /** Whether the app is currently allowed to post notifications. */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }
}
