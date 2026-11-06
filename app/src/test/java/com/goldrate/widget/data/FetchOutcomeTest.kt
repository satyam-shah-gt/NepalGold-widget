package com.goldrate.widget.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * What a fetched payload means.
 *
 * The endpoint keeps serving the last published payload until the publisher updates, so the
 * difference between "the rate of today" and "still yesterday's rate" decides whether the app
 * counts the run as an update at all.
 */
class FetchOutcomeTest {

    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun `the rate of today is an update`() {
        assertEquals(
            FetchOutcome.UPDATED,
            FetchOutcome.of(LocalDate.of(2026, 11, 5), today),
        )
    }

    @Test
    fun `the rate of an earlier day is not an update`() {
        assertEquals(
            FetchOutcome.NOT_PUBLISHED_YET,
            FetchOutcome.of(LocalDate.of(2026, 11, 4), today),
        )
    }

    @Test
    fun `the rate of a later day is not an update either`() {
        // The publisher's clock and the phone's may disagree by a day around midnight.
        assertEquals(
            FetchOutcome.NOT_PUBLISHED_YET,
            FetchOutcome.of(LocalDate.of(2026, 11, 6), today),
        )
    }
}
