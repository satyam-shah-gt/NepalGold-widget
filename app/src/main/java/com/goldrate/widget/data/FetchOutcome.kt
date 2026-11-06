package com.goldrate.widget.data

import java.time.LocalDate

/**
 * What a fetched payload means.
 *
 * The endpoint keeps serving the last published payload until the publisher updates in the late
 * morning, and it does not publish at all on weekends and holidays. A payload that is not the
 * rate of today is therefore **not an update**: the widget would show a number from an earlier
 * day and call it fresh, and a notification would say the rate had changed when it had not.
 *
 * Pure on purpose - no Android types - so the decision can be unit tested on the JVM.
 */
enum class FetchOutcome {

    /** The publisher has published the rate of today. */
    UPDATED,

    /** The payload is still an earlier day's rate; the publisher has not updated yet. */
    NOT_PUBLISHED_YET,

    ;

    companion object {

        /**
         * Reads the outcome from the day the payload was published.
         *
         * [today] is the day in Nepal time, the calendar the publisher publishes on.
         */
        fun of(publishedDate: LocalDate, today: LocalDate): FetchOutcome =
            if (publishedDate == today) UPDATED else NOT_PUBLISHED_YET
    }
}
