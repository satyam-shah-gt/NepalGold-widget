package com.goldrate.widget

import android.app.Application
import com.goldrate.widget.notify.Notifier
import com.goldrate.widget.work.WorkScheduler

/**
 * Application entry point.
 *
 * The app has no launcher activity, so this is the only place that runs on every process start.
 * Both calls are idempotent: the notification channel is only created once, and the daily work
 * is enqueued with `KEEP` so an existing schedule is never reset.
 */
class GoldRateApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        WorkScheduler.ensureDaily(this)
    }
}
