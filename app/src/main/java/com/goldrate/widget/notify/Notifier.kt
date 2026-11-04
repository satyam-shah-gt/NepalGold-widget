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
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateQuote
import com.goldrate.widget.data.withLanguage
import com.goldrate.widget.ui.ConfigActivity
import java.time.LocalDate

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
     * The notification shows the same rate the widget shows - the one tola rate of the metal the
     * widget is set to - together with the change against the previous day and the day the rate
     * belongs to. The day is what makes a notification that is no longer current recognisable.
     *
     * Silently does nothing when the user has not granted `POST_NOTIFICATIONS` (Android 13+) or
     * has turned notifications off for the channel.
     */
    fun notifySuccess(
        context: Context,
        quotes: List<RateQuote>,
        lang: AppLang,
        metal: Metal,
        date: LocalDate,
    ) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)

        val localized = context.withLanguage(lang)
        val quote = quotes.firstOrNull { metal.matches(it) }
        val metalName = localized.getString(
            if (metal == Metal.GOLD) R.string.config_metal_gold else R.string.config_metal_silver,
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, ConfigActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(localized.getString(R.string.notification_title, metalName))
            .setContentText(
                localized.getString(
                    R.string.notification_text,
                    localized.getString(R.string.notification_unit_tola),
                    RateSummary.rate(quotes, metal, lang),
                    RateSummary.change(quote, lang),
                    RateSummary.day(date, lang),
                ),
            )
            .setSubText(localized.getString(R.string.notification_subtext))
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
