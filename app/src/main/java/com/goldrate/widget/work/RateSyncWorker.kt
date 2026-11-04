package com.goldrate.widget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goldrate.widget.data.RateApi
import com.goldrate.widget.data.RateStore
import com.goldrate.widget.notify.Notifier
import com.goldrate.widget.widget.WidgetRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import java.time.YearMonth
import kotlinx.coroutines.withContext

/**
 * Fetches the daily rate, stores it, notifies the user and repaints the widget.
 *
 * The work always runs in the background: the app process is started by the scheduler, does its
 * job and is then torn down again.
 */
class RateSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = RateStore(applicationContext)

        return try {
            val parsed = withContext(Dispatchers.IO) { RateApi.fetch() }

            // The endpoint publishes yesterday's rates alongside today's, so both days go into
            // the history and the chart has a trend from the first fetch.
            store.appendDays(parsed.toHistoryDays())

            // The publisher also keeps a month of history, which is what fills the chart on a
            // fresh install instead of collecting one day at a time. It is a bonus: if it fails,
            // the latest rate is still stored.
            runCatching {
                RateApi.fetchRecentHistory(YearMonth.from(parsed.date))
            }.onSuccess { days ->
                store.appendDays(days)
            }
            store.markFetchSucceeded()
            Notifier.notifySuccess(
                applicationContext,
                parsed.quotes,
                store.currentLanguage(),
                store.currentMetal(),
                parsed.date,
            )
            WidgetRenderer.renderAll(applicationContext)

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Remember the failure so the widget can flag that the shown rates may be out of
            // date, and clear the spinner so the widget does not look stuck.
            store.markFetchFailed()
            WidgetRenderer.renderAll(applicationContext)
            if (isStopped) return Result.failure()
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}
