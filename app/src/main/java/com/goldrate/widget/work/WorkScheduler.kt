package com.goldrate.widget.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns the two WorkManager schedules this app uses.
 *
 * The daily schedule is unique and enqueued with `KEEP`, so calling [ensurePeriodic] repeatedly
 * (on every widget update, for instance) never resets the timer.
 */
object WorkScheduler {

    private const val PERIODIC_WORK = "daily_rate_sync"
    private const val MANUAL_WORK = "manual_rate_sync"

    private const val REFRESH_INTERVAL_HOURS = 24L

    /**
     * Registers the daily fetch if it is not already registered.
     *
     * WorkManager periodic work is inexact: Android decides when the window actually opens, so the
     * notification can arrive at any point inside the 24 hour period rather than at a fixed hour.
     */
    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RateSyncWorker>(
            REFRESH_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
    }

    /** Runs a one-off sync, used by the widget's refresh button. */
    fun enqueueManual(context: Context) {
        val request = OneTimeWorkRequestBuilder<RateSyncWorker>()
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(MANUAL_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun networkConstraint() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
