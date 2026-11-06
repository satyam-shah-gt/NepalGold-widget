package com.goldrate.widget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goldrate.widget.data.FetchOutcome
import com.goldrate.widget.data.RateApi
import com.goldrate.widget.data.RateStore
import com.goldrate.widget.notify.Notifier
import com.goldrate.widget.notify.RateSummary
import com.goldrate.widget.widget.WidgetRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import java.time.YearMonth
import kotlinx.coroutines.withContext

/**
 * Fetches the daily rate, stores it, notifies the user and repaints the widget.
 *
 * The work always runs in the background: the app process is started by the scheduler, does its
 * job and is then torn down again. Every run arms the next daily run at the publish time, so
 * the schedule survives the process that carried it out.
 *
 * A payload that is not the rate of today is not an update: the publisher had not published yet.
 * Such a run leaves the widget as it was, keeps the time of the last rate that really was
 * published, and says nothing.
 */
class RateSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = RateStore(applicationContext)

        return try {
            val parsed = withContext(Dispatchers.IO) { RateApi.fetch() }

            // The day the publisher published, read before the fetched days go into the history.
            val storedLatest = store.currentHistory().firstOrNull()?.date

            // Today in Nepal time - the calendar the publisher publishes on. Until the publisher
            // updates, the endpoint keeps serving the last published payload, and on weekends and
            // holidays it does not update at all.
            val today = RateStore.today()
            val outcome = FetchOutcome.of(parsed.date, today)

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

            when (outcome) {
                FetchOutcome.UPDATED -> {
                    store.markFetchSucceeded()
                    if (RateSummary.shouldNotify(storedLatest, parsed.date, today)) {
                        Notifier.notifySuccess(
                            applicationContext,
                            parsed.quotes,
                            store.currentLanguage(),
                            store.currentMetal(),
                            parsed.date,
                        )
                    }
                }

                FetchOutcome.NOT_PUBLISHED_YET -> {
                    // The publisher has not published yet. The rate on the widget is a day old,
                    // so the widget keeps the time of the last rate that really was published,
                    // and the app stays quiet about it.
                    store.markFetchStale()
                }
            }
            WidgetRenderer.renderAll(applicationContext)

            if (outcome == FetchOutcome.UPDATED) Result.success() else retryOrFail()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Remember the failure so the widget can flag that the shown rates may be out of
            // date, and clear the spinner so the widget does not look stuck.
            store.markFetchFailed()
            WidgetRenderer.renderAll(applicationContext)
            retryOrFail()
        } finally {
            // Whatever this run did, the next daily run is armed. It is named after the day it
            // belongs to, so arming it again for a day that already ran changes nothing.
            WorkScheduler.armNextDailyRun(applicationContext)
        }
    }

    /**
     * The result of a run that brought no rate of today.
     *
     * The publisher may still publish later, so the run is tried again after the backoff of the
     * request. When the attempts are used up the safety net takes over.
     */
    private fun retryOrFail(): Result =
        if (isStopped) {
            Result.failure()
        } else if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure()
        }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}
