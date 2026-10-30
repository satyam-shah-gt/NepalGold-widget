package com.goldrate.widget.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the GOLD / SILVER switch on the widget.
 *
 * It stores the chosen metal and repaints every placed widget with the new series.
 */
class ToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val metal = Metal.from(intent.getStringExtra(EXTRA_METAL))
        val appContext = context.applicationContext

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                RateStore(appContext).setMetal(metal)
                WidgetRenderer.renderAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_METAL = "metal"
    }
}
