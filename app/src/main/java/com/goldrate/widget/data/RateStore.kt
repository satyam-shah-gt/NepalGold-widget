package com.goldrate.widget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/**
 * The single DataStore delegate for this app. Declaring it once at file scope guarantees a
 * single DataStore instance for the underlying file - declaring a second delegate for the same
 * file name would throw at runtime.
 */
private val Context.rateDataStore: DataStore<Preferences> by preferencesDataStore(name = "gold_rate_store")

/** The languages the widget can be rendered in. */
enum class AppLang(val code: String) {
    EN("en"),
    NE("ne");

    companion object {
        fun from(code: String?): AppLang = if (code == "ne") NE else EN
    }
}

/**
 * Local storage for the rate history and the user's language preference.
 *
 * The history is kept as one JSON array under `history_json`; the language lives under
 * `language_preference` as either `en` or `ne`.
 */
class RateStore(context: Context) {

    private val store = context.applicationContext.rateDataStore

    /** The language the user picked in the settings screen. */
    val language: Flow<AppLang> = store.data.map { AppLang.from(it[KEY_LANGUAGE]) }

    /** The stored history, newest day first. */
    val history: Flow<List<DayRates>> = store.data.map { decode(it[KEY_HISTORY]) }

    /** The metal the widget is currently showing. */
    val metal: Flow<Metal> = store.data.map { Metal.from(it[KEY_METAL]) }

    /** Epoch millis of the last successful fetch, 0 if there has never been one. */
    val lastSuccessAt: Flow<Long> = store.data.map { it[KEY_LAST_SUCCESS] ?: 0L }

    /** Epoch millis of the last failed fetch attempt, 0 if there has never been one. */
    val lastErrorAt: Flow<Long> = store.data.map { it[KEY_LAST_ERROR] ?: 0L }

    suspend fun currentLanguage(): AppLang = language.first()

    suspend fun currentMetal(): Metal = metal.first()

    suspend fun setMetal(metal: Metal) {
        store.edit { it[KEY_METAL] = metal.code }
    }

    suspend fun setLanguage(lang: AppLang) {
        store.edit { it[KEY_LANGUAGE] = lang.code }
    }

    suspend fun currentHistory(): List<DayRates> = history.first()

    /** Records a successful fetch and clears any previous failure. */
    suspend fun markFetchSucceeded() {
        store.edit {
            it[KEY_LAST_SUCCESS] = System.currentTimeMillis()
            it[KEY_LAST_ERROR] = 0L
        }
    }

    /** Records that the last fetch attempt failed. */
    suspend fun markFetchFailed() {
        store.edit { it[KEY_LAST_ERROR] = System.currentTimeMillis() }
    }

    /** True when the most recent attempt failed, i.e. the shown rates may be out of date. */
    suspend fun isStale(): Boolean {
        val success = lastSuccessAt.first()
        val error = lastErrorAt.first()
        return error > success
    }

    /**
     * Inserts [day], replacing any entry already stored for the same date, then prunes the
     * history so it never exceeds [HISTORY_DAYS] days.
     */
    suspend fun appendDay(day: DayRates) = appendDays(listOf(day))

    /**
     * Inserts [days], replacing any entries already stored for the same dates, then prunes the
     * history so it never exceeds [HISTORY_DAYS] days.
     */
    suspend fun appendDays(days: List<DayRates>) {
        if (days.isEmpty()) return
        store.edit { prefs ->
            val dates = days.map { it.date }.toSet()
            val merged = decode(prefs[KEY_HISTORY])
                .filterNot { it.date in dates }
                .plus(days)
                .sortedByDescending { it.date }
            prefs[KEY_HISTORY] = encode(pruneHistory(merged, today()))
        }
    }

    private fun encode(days: List<DayRates>): String {
        val array = JSONArray()
        days.forEach { day ->
            val quotes = JSONArray()
            day.quotes.forEach { quote ->
                quotes.put(
                    JSONObject().apply {
                        put("type", quote.type)
                        put("today", quote.today)
                        put("yesterday", quote.yesterday)
                    },
                )
            }
            array.put(
                JSONObject().apply {
                    put("date", day.date)
                    put("quotes", quotes)
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<DayRates> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val date = obj.optString("date").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val quotesArray = obj.optJSONArray("quotes") ?: JSONArray()
                val quotes = (0 until quotesArray.length()).mapNotNull { j ->
                    val q = quotesArray.optJSONObject(j) ?: return@mapNotNull null
                    RateQuote(
                        type = q.optString("type"),
                        today = q.optDouble("today", Double.NaN),
                        yesterday = q.optDouble("yesterday", Double.NaN),
                    )
                }
                DayRates(date = date, quotes = quotes)
            }.sortedByDescending { it.date }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    companion object {
        /** How many past days of history are kept. */
        const val HISTORY_DAYS = 30

        private val KEY_HISTORY = stringPreferencesKey("history_json")
        private val KEY_LANGUAGE = stringPreferencesKey("language_preference")
        private val KEY_METAL = stringPreferencesKey("metal_preference")
        private val KEY_LAST_SUCCESS = longPreferencesKey("last_success_at")
        private val KEY_LAST_ERROR = longPreferencesKey("last_error_at")

        /** Today's date in Nepal local time. */
        fun today(): LocalDate = LocalDate.now(RateParser.NEPAL_ZONE)

        /**
         * Pure implementation of the retention rule: keeps the days that fall inside the
         * [HISTORY_DAYS] day window, newest first.
         */
        fun pruneHistory(days: List<DayRates>, today: LocalDate): List<DayRates> {
            val cutoff = today.minusDays((HISTORY_DAYS - 1).toLong())
            return days
                .filter { runCatching { LocalDate.parse(it.date) >= cutoff }.getOrDefault(true) }
                .take(HISTORY_DAYS)
        }
    }
}
