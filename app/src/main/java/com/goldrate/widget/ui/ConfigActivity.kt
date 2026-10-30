package com.goldrate.widget.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.goldrate.widget.R
import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.DayRates
import com.goldrate.widget.data.Format
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateLabels
import com.goldrate.widget.data.RateQuote
import com.goldrate.widget.data.RateStore
import com.goldrate.widget.data.withLanguage
import com.goldrate.widget.notify.Notifier
import com.goldrate.widget.widget.WidgetRenderer
import com.goldrate.widget.work.WorkScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The settings screen.
 *
 * It has no launcher entry - it is reached by tapping the widget body. It lets the user pick the
 * widget language, grant the notification permission and review the stored history.
 */
class ConfigActivity : ComponentActivity() {

    private lateinit var store: RateStore
    private lateinit var notificationStatusText: TextView
    private lateinit var notificationButton: Button
    private lateinit var languageGroup: RadioGroup
    private lateinit var metalGroup: RadioGroup
    private lateinit var historyTitleText: TextView
    private lateinit var historyContainer: LinearLayout

    private var currentLang = AppLang.EN
    private var currentMetal = Metal.GOLD

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            renderNotificationState()
        }

    /**
     * Applies the stored language to the activity's own resources so the settings screen and the
     * widget always speak the same language.
     */
    override fun attachBaseContext(newBase: Context) {
        val lang = runCatching {
            runBlocking { RateStore(newBase).currentLanguage() }
        }.getOrDefault(AppLang.EN)
        super.attachBaseContext(newBase.withLanguage(lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        applyWindowInsets(findViewById(R.id.config_root))

        store = RateStore(applicationContext)
        notificationStatusText = findViewById(R.id.notificationStatusText)
        notificationButton = findViewById(R.id.notificationButton)
        languageGroup = findViewById(R.id.languageGroup)
        metalGroup = findViewById(R.id.metalGroup)
        historyTitleText = findViewById(R.id.historyTitleText)
        historyContainer = findViewById(R.id.historyContainer)

        notificationButton.setOnClickListener { requestNotificationPermission() }

        metalGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = if (checkedId == R.id.metalSilver) Metal.SILVER else Metal.GOLD
            if (selected == currentMetal) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                store.setMetal(selected)
                currentMetal = selected
                WidgetRenderer.renderAll(applicationContext)
            }
        }

        findViewById<Button>(R.id.refreshButton).setOnClickListener {
            WorkScheduler.enqueueManual(this)
            Toast.makeText(this, R.string.config_refresh_now, Toast.LENGTH_SHORT).show()
        }

        languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val selected = if (checkedId == R.id.languageNepali) AppLang.NE else AppLang.EN
            if (selected == currentLang) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                store.setLanguage(selected)
                currentLang = selected
                WidgetRenderer.renderAll(applicationContext)
                recreate()
            }
        }

        lifecycleScope.launch {
            currentLang = store.currentLanguage()
            currentMetal = store.currentMetal()
            renderAll()
        }
    }

    private fun renderAll() {
        renderNotificationState()
        languageGroup.check(
            if (currentLang == AppLang.NE) R.id.languageNepali else R.id.languageEnglish,
        )
        metalGroup.check(
            if (currentMetal == Metal.SILVER) R.id.metalSilver else R.id.metalGold,
        )
        lifecycleScope.launch {
            renderHistory(store.currentHistory())
        }
    }

    private fun renderNotificationState() {
        val enabled = Notifier.canPostNotifications(this)
        if (enabled) {
            notificationStatusText.setText(R.string.config_notifications_enabled)
            notificationButton.visibility = View.GONE
        } else {
            notificationStatusText.setText(R.string.config_notifications_disabled)
            notificationButton.visibility = View.VISIBLE
        }
    }

    /**
     * Keeps the content clear of the status and navigation bars.
     *
     * Android 15 (API 35) and newer lay activities out edge to edge, so without this the
     * settings screen would slide under the system bars.
     */
    private fun applyWindowInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun requestNotificationPermission() {        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Before Android 13 the only way to re-enable them is the system settings screen.
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
    }

    private fun renderHistory(days: List<DayRates>) {
        val localized = this.withLanguage(currentLang)
        historyTitleText.text = localized.getString(R.string.config_history_title, RateStore.HISTORY_DAYS)
        historyContainer.removeAllViews()

        if (days.isEmpty()) {
            historyContainer.addView(
                TextView(this).apply {
                    text = localized.getString(R.string.config_history_empty)
                    textSize = 13f
                },
            )
            return
        }

        days.forEach { day ->
            historyContainer.addView(
                TextView(this).apply {
                    text = day.date
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, 16, 0, 2)
                },
            )
            // Only the tola rates, matching what the widget shows.
            Metal.entries.forEach { metal ->
                day.quotes.firstOrNull { metal.matches(it) }?.let { quote ->
                    historyContainer.addView(buildQuoteRow(quote))
                }
            }
        }
    }

    private fun buildQuoteRow(quote: RateQuote): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }
        left.addView(
            TextView(this).apply {
                text = RateLabels.display(quote.type, currentLang)
                textSize = 13f
            },
        )
        left.addView(
            TextView(this).apply {
                text = Format.signed(quote.change, currentLang)
                textSize = 11f
            },
        )

        val value = TextView(this).apply {
            text = Format.amount(quote.today, currentLang)
            textSize = 13f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }

        row.addView(left)
        row.addView(value)
        return row
    }
}
