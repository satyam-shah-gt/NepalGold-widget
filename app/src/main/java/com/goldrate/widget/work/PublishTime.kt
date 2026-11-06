package com.goldrate.widget.work

import com.goldrate.widget.data.RateParser
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The moment of the day the daily fetch runs.
 *
 * The publisher updates between roughly 10:33 and 10:48 Nepal time, so the rate of the day is
 * online a little after eleven. The daily fetch runs at 11:05 Nepal time, which leaves the
 * publisher room to finish.
 *
 * Nepal time has no daylight saving, so the target is the same wall clock moment every day.
 * The moment is computed in Nepal time and never in the timezone of the phone, so the fetch
 * runs at the same moment everywhere.
 */
object PublishTime {

    /** The hour of the day the daily fetch runs, Nepal time. */
    const val HOUR = 11

    /** The minute of the hour the daily fetch runs, Nepal time. */
    const val MINUTE = 5

    /** The next publish moment strictly after [now]. */
    fun nextPublishAt(now: Instant): ZonedDateTime {
        val local = now.atZone(RateParser.NEPAL_ZONE)
        val candidate = local
            .withHour(HOUR)
            .withMinute(MINUTE)
            .withSecond(0)
            .withNano(0)
        return if (candidate.isAfter(local)) candidate else candidate.plusDays(1)
    }

    /** How long it is from [now] until the next publish moment. */
    fun delayUntilNextPublish(now: Instant): Duration =
        Duration.between(now, nextPublishAt(now).toInstant())

    /**
     * The unique name of the daily run that fires at [target].
     *
     * The name carries the day the run belongs to, so the run of a day that has already
     * happened is never armed a second time and the run of the next day never collides with it.
     */
    fun workName(target: ZonedDateTime): String = "daily_rate_sync_${target.toLocalDate()}"
}
