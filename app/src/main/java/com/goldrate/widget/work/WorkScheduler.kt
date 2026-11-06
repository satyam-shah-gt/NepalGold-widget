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
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Owns the schedules this app uses.
 *
 * The daily fetch runs at 11:05 Nepal time, shortly after the publisher has published. A
 * periodic work request cannot express a time of day - its window is the whole interval and
 * Android picks the moment inside it - so the daily run is a one-off request with an initial
 * delay. It is named after the day it belongs to and is armed again after every run.
 *
 * A periodic work request runs beside it as a safety net. It fetches every six hours, so a
 * daily run that the system deferred - a phone lying in deep Doze, for instance - is still
 * followed by a fetch later the same day. Its first run also fills the widget right after the
 * app is installed, which is what the old periodic schedule did.
 */
object WorkScheduler {

    /** The unique name of the safety net. */
    const val SAFETY_WORK = "daily_rate_sync_safety"

    /** The unique name of the manual sync. */
    const val MANUAL_WORK = "manual_rate_sync"

    /** How often the safety net runs, in hours. */
    private const val SAFETY_INTERVAL_HOURS = 6L

    /**
     * Registers the daily run at the publish time and the safety net beside it.
     *
     * Both are idempotent: the daily run is unique per day and is kept as it is, and the safety
     * net is kept with `KEEP`, so a redundant call never resets a timer.
     */
    fun ensureDaily(context: Context) {
        val appContext = context.applicationContext
        armNextDailyRun(appContext)

        val safetyNet = PeriodicWorkRequestBuilder<RateSyncWorker>(
            SAFETY_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(
                SAFETY_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                safetyNet,
            )
    }

    /**
     * Arms the daily run for the next publish moment.
     *
     * The work is named after the day it belongs to, so the run of a day that has already
     * happened is never armed a second time, and the run of the next day never collides with
     * it.
     */
    fun armNextDailyRun(context: Context) {
        val now = Instant.now()
        val target = PublishTime.nextPublishAt(now)
        val request = OneTimeWorkRequestBuilder<RateSyncWorker>()
            .setInitialDelay(PublishTime.delayUntilNextPublish(now))
            .setConstraints(networkConstraint())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                PublishTime.workName(target),
                ExistingWorkPolicy.KEEP,
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
