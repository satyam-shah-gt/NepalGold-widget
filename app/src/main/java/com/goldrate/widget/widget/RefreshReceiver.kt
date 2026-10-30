package com.goldrate.widget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goldrate.widget.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the widget's refresh button.
 *
 * It schedules the sync and immediately repaints the widget with the spinner visible, so the
 * tap is acknowledged straight away rather than after the network round trip.
 */
class RefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        WorkScheduler.enqueueManual(context)

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetRenderer.renderAll(context, showSpinner = true)
            } finally {
                pending.finish()
            }
        }
    }
}
