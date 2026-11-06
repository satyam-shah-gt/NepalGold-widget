package com.goldrate.widget

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrate.widget.data.RateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The marks the store keeps about the last fetch.
 *
 * The two of them mean different things to the widget: a fetch that worked leaves the time of
 * the last published rate, and a fetch that brought no rate of today leaves the mark that says
 * the rates on the widget may be out of date.
 */
@RunWith(AndroidJUnit4::class)
class RateStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aFetchThatBroughtNoRateOfTodayIsNotAFreshOne() = runBlocking {
        val store = RateStore(context)

        store.markFetchStale()
        assertTrue(
            "The shown rates may be out of date",
            store.isStale(),
        )

        store.markFetchSucceeded()
        assertFalse(
            "A rate that really was published clears the mark",
            store.isStale(),
        )
    }
}
