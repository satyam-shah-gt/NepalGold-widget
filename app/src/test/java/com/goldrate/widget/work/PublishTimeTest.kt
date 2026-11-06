package com.goldrate.widget.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Covers the moment the daily fetch runs.
 *
 * The daily run has to happen at 11:05 Nepal time whatever the timezone of the phone is, and
 * it has to be the next occurrence of that moment, never the one that just passed.
 */
class PublishTimeTest {

    private val nepal = ZoneId.of("Asia/Kathmandu")

    private fun nepalMoment(text: String): Instant =
        ZonedDateTime.parse(text).withZoneSameInstant(nepal).toInstant()

    @Test
    fun `the run happens today when the day has not reached the publish moment yet`() {
        val target = PublishTime.nextPublishAt(nepalMoment("2026-11-05T09:00:00+05:45"))

        assertEquals(11, target.hour)
        assertEquals(5, target.minute)
        assertEquals(5, target.dayOfMonth)
        assertEquals(nepal, target.zone)
    }

    @Test
    fun `the run happens tomorrow once the publish moment has passed`() {
        val target = PublishTime.nextPublishAt(nepalMoment("2026-11-05T14:00:00+05:45"))

        assertEquals(11, target.hour)
        assertEquals(5, target.minute)
        assertEquals(6, target.dayOfMonth)
    }

    @Test
    fun `the publish moment itself belongs to the next day`() {
        val target = PublishTime.nextPublishAt(nepalMoment("2026-11-05T11:05:00+05:45"))

        assertEquals(11, target.hour)
        assertEquals(5, target.minute)
        assertEquals(6, target.dayOfMonth)
    }

    @Test
    fun `a moment just before the publish moment still belongs to the same day`() {
        val target = PublishTime.nextPublishAt(nepalMoment("2026-11-05T11:04:59+05:45"))

        assertEquals(5, target.dayOfMonth)
    }

    @Test
    fun `the delay is the time that is left until the publish moment`() {
        val now = nepalMoment("2026-11-05T09:00:00+05:45")

        assertEquals(Duration.ofMinutes(125), PublishTime.delayUntilNextPublish(now))
    }

    @Test
    fun `the delay is never negative`() {
        val now = nepalMoment("2026-11-05T11:05:00+05:45")

        assertTrue(PublishTime.delayUntilNextPublish(now).isPositive)
    }

    @Test
    fun `the publish moment does not depend on the timezone of the phone`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            // 03:15 UTC is 08:45 in Nepal, so the run is still ahead and still at 11:05.
            val target = PublishTime.nextPublishAt(Instant.parse("2026-11-05T03:15:00Z"))

            assertEquals(11, target.hour)
            assertEquals(5, target.minute)
            assertEquals(5, target.dayOfMonth)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `the day of the run is the day in Nepal time`() {
        // 20:00 UTC is already the next day in Nepal.
        val target = PublishTime.nextPublishAt(Instant.parse("2026-11-05T20:00:00Z"))

        assertEquals(6, target.dayOfMonth)
        assertEquals(11, target.hour)
        assertEquals(5, target.minute)
    }

    @Test
    fun `the work is named after the Nepal day it belongs to`() {
        val target = PublishTime.nextPublishAt(nepalMoment("2026-11-05T09:00:00+05:45"))

        assertEquals("daily_rate_sync_2026-11-05", PublishTime.workName(target))
    }
}
