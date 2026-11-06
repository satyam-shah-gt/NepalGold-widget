package com.goldrate.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.goldrate.widget.data.RateParser
import com.goldrate.widget.work.PublishTime
import com.goldrate.widget.work.WorkScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Verifies that the daily run is armed for the publish time.
 *
 * The check runs against a real WorkManager, so the schedule is read back from the place the
 * system keeps it. The device is not in Nepal time, which is exactly the case the schedule has
 * to survive: the run has to be armed for 11:05 Nepal time whatever the phone's timezone is.
 */
@RunWith(AndroidJUnit4::class)
class WorkSchedulerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theDailyRunIsArmedForThePublishTime() {
        WorkScheduler.ensureDaily(context)

        val target = PublishTime.nextPublishAt(Instant.now())
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(PublishTime.workName(target))
            .get(10, TimeUnit.SECONDS)

        assertTrue("The daily run must be scheduled", infos.isNotEmpty())
        val info = infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
        assertTrue("The daily run must be waiting for its moment", info != null)

        assertTrue("The daily run must have a scheduled moment", info!!.nextScheduleTimeMillis > 0)
        val scheduled = Instant.ofEpochMilli(info.nextScheduleTimeMillis)
            .atZone(RateParser.NEPAL_ZONE)
        assertEquals("The daily run must be armed for the publish hour", 11, scheduled.hour)
        assertEquals("The daily run must be armed for the publish minute", 5, scheduled.minute)

        val ahead = info.nextScheduleTimeMillis - System.currentTimeMillis()
        assertTrue(
            "The daily run must be at most a day ahead",
            ahead in 0..Duration.ofHours(24).toMillis(),
        )
    }

    @Test
    fun theSafetyNetRunsBesideIt() {
        WorkScheduler.ensureDaily(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkScheduler.SAFETY_WORK)
            .get(10, TimeUnit.SECONDS)

        assertTrue("The safety net must be scheduled", infos.isNotEmpty())
        assertTrue(
            "The safety net must be waiting for its turn",
            infos.any { it.state == WorkInfo.State.ENQUEUED },
        )
    }
}
