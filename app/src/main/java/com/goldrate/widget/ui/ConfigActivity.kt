package com.goldrate.widget.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.goldrate.widget.R
import com.goldrate.widget.data.AppLang
import com.goldrate.widget.data.DayRates
import com.goldrate.widget.data.Format
import com.goldrate.widget.data.Metal
import com.goldrate.widget.data.RateDates
import com.goldrate.widget.data.RateSeries
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
 * It has no launcher entry - it is reached by tapping the widget body. It shows the latest rate
 * as the focus block at the top, the language and the metal as segmented controls, the stored
 * history as a table and the one action the screen has, pinned to the bottom.
 */
class ConfigActivity : ComponentActivity() {

    private lateinit var store: RateStore
    private lateinit var headerMetalChip: TextView
    private lateinit var headerDate: TextView
    private lateinit var headerAmount: TextView
    private lateinit var headerUnit: TextView
    private lateinit var headerChange: TextView
    private lateinit var notificationStatusText: TextView
    private lateinit var notificationButton: TextView
    private lateinit var languageControl: SegmentedControl
    private lateinit var metalControl: SegmentedControl
    private lateinit var historyTitleText: TextView
    private lateinit var historyCaptionText: TextView
    private lateinit var historyContainer: LinearLayout

    private var currentLang = AppLang.EN
    private var currentMetal = Metal.GOLD
    private var history: List<DayRates> = emptyList()

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

        store = RateStore(applicationContext)
        headerMetalChip = findViewById(R.id.headerMetalChip)
        headerDate = findViewById(R.id.headerDate)
        headerAmount = findViewById(R.id.headerAmount)
        headerUnit = findViewById(R.id.headerUnit)
        headerChange = findViewById(R.id.headerChange)
        notificationStatusText = findViewById(R.id.notificationStatusText)
        notificationButton = findViewById(R.id.notificationButton)
        languageControl = findViewById(R.id.languageControl)
        metalControl = findViewById(R.id.metalControl)
        historyTitleText = findViewById(R.id.historyTitleText)
        historyCaptionText = findViewById(R.id.historyCaptionText)
        historyContainer = findViewById(R.id.historyContainer)

        applyWindowInsets(
            root = findViewById(R.id.config_root),
            actionBar = findViewById(R.id.config_action_bar),
            content = findViewById(R.id.config_content),
        )

        notificationButton.setOnClickListener { requestNotificationPermission() }

        findViewById<TextView>(R.id.refreshButton).setOnClickListener {
            WorkScheduler.enqueueManual(this)
            Toast.makeText(this, R.string.config_refresh_now, Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            currentLang = store.currentLanguage()
            currentMetal = store.currentMetal()
            history = store.currentHistory()
            renderAll()
        }
    }

    private fun renderAll() {
        renderNotificationState()
        renderControls()
        renderHeader()
        renderHistory()
    }

    /**
     * Builds the two segmented controls and puts the stored selection in them.
     *
     * The labels come from the localised context, because the language the user picked is the
     * one the screen has to speak.
     */
    private fun renderControls() {
        val localized = this.withLanguage(currentLang)

        languageControl.setOptions(
            labels = listOf(
                localized.getString(R.string.config_language_english),
                localized.getString(R.string.config_language_nepali),
            ),
            accent = ContextCompat.getColor(this, R.color.ui_accent_primary),
        ) { index -> onLanguageSelected(index) }
        languageControl.select(if (currentLang == AppLang.NE) 1 else 0)

        metalControl.setOptions(
            labels = listOf(
                localized.getString(R.string.config_metal_gold),
                localized.getString(R.string.config_metal_silver),
            ),
            accent = metalAccent(),
        ) { index -> onMetalSelected(index) }
        metalControl.select(if (currentMetal == Metal.SILVER) 1 else 0)
    }

    /** The focus block at the top: the latest published rate and how it moved. */
    private fun renderHeader() {
        val localized = this.withLanguage(currentLang)
        val accent = metalAccent()

        headerMetalChip.text = localized.getString(
            if (currentMetal == Metal.GOLD) R.string.toggle_gold else R.string.toggle_silver,
        )
        headerMetalChip.backgroundTintList = ColorStateList.valueOf(accent)
        headerMetalChip.setTextColor(ContextCompat.getColor(this, R.color.ui_on_accent))

        val day = history.firstOrNull()
        val quote = RateSeries.tolaQuote(day, currentMetal)

        if (quote == null) {
            headerAmount.text = localized.getString(R.string.widget_no_data)
            headerUnit.text = ""
            headerChange.visibility = View.GONE
            headerDate.text = ""
            return
        }

        headerAmount.text = buildString {
            append(localized.getString(R.string.currency_symbol))
            append(' ')
            append(Format.amount(quote.today, currentLang))
        }
        headerUnit.text = localized.getString(R.string.rate_unit_tola)

        val change = quote.change
        val arrow = when {
            change > 0 -> "▲ "
            change < 0 -> "▼ "
            else -> ""
        }
        headerChange.text = buildString {
            append(Format.signedPercent(change, quote.yesterday, currentLang))
            append(' ')
            append(arrow)
            append('(')
            append(Format.signed(change, currentLang))
            append(')')
        }
        val changeColor = when {
            change > 0 -> ContextCompat.getColor(this, R.color.widget_up)
            change < 0 -> ContextCompat.getColor(this, R.color.widget_down)
            else -> ContextCompat.getColor(this, R.color.ui_on_surface_variant)
        }
        headerChange.setTextColor(changeColor)
        headerChange.backgroundTintList =
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(changeColor, 32))
        headerChange.visibility = View.VISIBLE

        headerDate.text = day?.date?.let { date ->
            runCatching { java.time.LocalDate.parse(date) }.getOrNull()
        }?.let { RateDates.label(it, currentLang) } ?: ""
    }

    /** The stored history, as a table of the metal the widget shows. */
    private fun renderHistory() {
        val localized = this.withLanguage(currentLang)
        val days = RateStore.HISTORY_DAYS.toString()
        val localizedDays = if (currentLang == AppLang.NE) Format.toDevanagari(days) else days

        historyTitleText.text = localized.getString(R.string.config_history_title, localizedDays)
        historyCaptionText.text = localized.getString(
            R.string.config_history_caption,
            localized.getString(
                if (currentMetal == Metal.GOLD) R.string.config_metal_gold else R.string.config_metal_silver,
            ),
        )

        historyContainer.removeAllViews()

        val rows = HistoryTable.rows(history, currentMetal, currentLang)
        if (rows.isEmpty()) {
            historyContainer.addView(
                TextView(this).apply {
                    text = localized.getString(R.string.config_history_empty)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(ContextCompat.getColor(this@ConfigActivity, R.color.ui_on_surface_variant))
                },
            )
            return
        }

        historyContainer.addView(HistoryTable.build(this, rows, currentLang))
    }

    private fun onLanguageSelected(index: Int) {
        val selected = if (index == 1) AppLang.NE else AppLang.EN
        if (selected == currentLang) return
        lifecycleScope.launch {
            store.setLanguage(selected)
            currentLang = selected
            WidgetRenderer.renderAll(applicationContext)
            // The screen re-creates itself so its own chrome follows the new language.
            recreate()
        }
    }

    private fun onMetalSelected(index: Int) {
        val selected = if (index == 1) Metal.SILVER else Metal.GOLD
        if (selected == currentMetal) return
        lifecycleScope.launch {
            store.setMetal(selected)
            currentMetal = selected
            WidgetRenderer.renderAll(applicationContext)
            renderControls()
            renderHeader()
            renderHistory()
        }
    }

    /** The accent of the metal the widget shows. */
    private fun metalAccent(): Int = ContextCompat.getColor(
        this,
        if (currentMetal == Metal.GOLD) R.color.widget_accent_gold else R.color.widget_accent_silver,
    )

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
     * settings screen would slide under the system bars. The action bar is pinned to the bottom,
     * so it takes the navigation bar inset itself and the scrolling content keeps a bottom
     * padding that clears it.
     */
    private fun applyWindowInsets(root: View, actionBar: View, content: View) {
        val actionBarHeight = resources.getDimensionPixelSize(R.dimen.ui_action_bar_height)
        val barPadding = resources.getDimensionPixelSize(R.dimen.ui_gap)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, 0)

            actionBar.setPadding(
                actionBar.paddingLeft,
                actionBar.paddingTop,
                actionBar.paddingRight,
                barPadding + bars.bottom,
            )
            content.setPadding(
                content.paddingLeft,
                content.paddingTop,
                content.paddingRight,
                actionBarHeight + bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Before Android 13 the only way to re-enable them is the system settings screen.
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        }
    }
}
