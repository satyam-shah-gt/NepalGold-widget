package com.goldrate.widget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.goldrate.widget.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the home screen widget lifecycle broadcasts.
 *
 * Adding the widget to the home screen is the first moment the app process is guaranteed to
 * start, so this is where the daily schedule gets registered. The registration is idempotent.
 */
class GoldRateWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WorkScheduler.ensureDaily(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WorkScheduler.ensureDaily(context)
        renderAsync(context, appWidgetIds)
    }

    /** The launcher resized the widget, so the number of visible rows may have changed. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        renderAsync(context, intArrayOf(appWidgetId))
    }

    private fun renderAsync(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetRenderer.render(context, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }
}
