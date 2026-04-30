package com.example.litchrono

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color

import android.os.Build
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class QuoteWidget : AppWidgetProvider() {
    companion object {
        private const val ACTION_UPDATE = "com.example.litchrono.WIDGET_UPDATE"
        private const val ACTION_SYNC = "com.example.litchrono.WIDGET_SYNC"
        private const val ACTION_PAGE_UP = "com.example.litchrono.WIDGET_PAGE_UP"
        private const val ACTION_PAGE_DOWN = "com.example.litchrono.WIDGET_PAGE_DOWN"
        private const val LAST_FETCH_TIME_KEY = "last_fetch_time"
        private const val LAST_WIDGET_FETCH_CHECK_TIME_KEY = "last_widget_fetch_check_time"
        private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
        private const val EMPTY_CACHE_RETRY_MS = 15 * 60 * 1000L
        private const val QUOTES_BASE_URL = "https://raw.githubusercontent.com/sandsq/time_of_day_quotes/refs/heads/main/"
        private const val PAGE_INDEX_PREF_PREFIX = "widget_page_index_"
        private const val SELECTED_QUOTE_TEXT_PREFIX = "widget_selected_quote_text_"
        private const val SELECTED_QUOTE_AUTHOR_PREFIX = "widget_selected_quote_author_"
        private const val SELECTED_QUOTE_TIME_PREFIX = "widget_selected_quote_time_"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Trigger immediate update
        val updateIntent = Intent(context, QuoteWidget::class.java).apply {
            action = ACTION_SYNC
        }
        context.sendBroadcast(updateIntent)

        // The update broadcast schedules the next refresh after it runs.
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel any pending alarms
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, QuoteWidget::class.java).apply {
            action = ACTION_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE -> handleUpdate(context)
            ACTION_SYNC -> handleUpdate(context)
            ACTION_PAGE_UP -> handlePageChange(context, intent, -1)
            ACTION_PAGE_DOWN -> handlePageChange(context, intent, 1)
        }
    }

    private fun handlePageChange(context: Context, intent: Intent, delta: Int) {
        // Page intents include the widgetId so we change only that widget's page index
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (appWidgetId == -1) return
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val key = PAGE_INDEX_PREF_PREFIX + appWidgetId
        val current = prefs.getInt(key, 0)
        val newIndex = (current + delta).coerceAtLeast(0)
        prefs.edit().putInt(key, newIndex).apply()
        // Redraw widget (will apply page index)
        updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    private fun handleUpdate(context: Context) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        try {
            updateQuotesIfNeeded(appContext) {
                try {
                    updateWidget(appContext)
                    scheduleNextUpdate(appContext)
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateWidget(appContext)
            scheduleNextUpdate(appContext)
            pendingResult.finish()
        }
    }

    private fun updateQuotesIfNeeded(context: Context, onComplete: () -> Unit) {
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val cachedQuotesJson = prefs.getString(SettingsActivity.QUOTES_DATA_KEY, null)
        val now = System.currentTimeMillis()
        val lastSuccessfulFetch = prefs.getLong(LAST_FETCH_TIME_KEY, 0L)
        val lastWidgetFetchCheck = prefs.getLong(LAST_WIDGET_FETCH_CHECK_TIME_KEY, 0L)
        val hasCachedQuotes = !cachedQuotesJson.isNullOrBlank()
        val needsFreshQuotes = !hasCachedQuotes || now - lastSuccessfulFetch > ONE_DAY_MS
        val retryDelay = if (hasCachedQuotes) ONE_DAY_MS else EMPTY_CACHE_RETRY_MS

        if (!needsFreshQuotes || now - lastWidgetFetchCheck < retryDelay) {
            onComplete()
            return
        }

        prefs.edit()
            .putLong(LAST_WIDGET_FETCH_CHECK_TIME_KEY, now)
            .apply()

        val retrofit = Retrofit.Builder()
            .baseUrl(QUOTES_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(QuoteApiService::class.java)
        service.getQuotes().enqueue(object : Callback<Map<String, List<Quote>>> {
            override fun onResponse(
                call: Call<Map<String, List<Quote>>>,
                response: Response<Map<String, List<Quote>>>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    saveQuotesToCache(context, response.body()!!)
                }
                onComplete()
            }

            override fun onFailure(call: Call<Map<String, List<Quote>>>, t: Throwable) {
                t.printStackTrace()
                onComplete()
            }
        })
    }

    private fun saveQuotesToCache(context: Context, quotes: Map<String, List<Quote>>) {
        try {
            val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val quotesJson = Gson().toJson(quotes)
            prefs.edit().apply {
                putString(SettingsActivity.QUOTES_DATA_KEY, quotesJson)
                putLong(LAST_FETCH_TIME_KEY, System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWidget(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, QuoteWidget::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Get current time from phone
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        var currentTime = timeFormat.format(Calendar.getInstance().time)

        // Load settings
        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val bgLeftColorHex = prefs.getString(SettingsActivity.KEY_BG_LEFT_COLOR, SettingsActivity.DEFAULT_BG_LEFT) ?: SettingsActivity.DEFAULT_BG_LEFT
        val textColorHex = prefs.getString(SettingsActivity.KEY_TEXT_COLOR, SettingsActivity.DEFAULT_TEXT_COLOR) ?: SettingsActivity.DEFAULT_TEXT_COLOR
        val timeColorHex = prefs.getString(SettingsActivity.KEY_TIME_COLOR, SettingsActivity.DEFAULT_TIME_COLOR) ?: SettingsActivity.DEFAULT_TIME_COLOR

        // Convert hex to color integers
        val bgLeftColor = hexToColor(bgLeftColorHex)
        val textColor = hexToColor(textColorHex)

        // Load cached quotes
        val cachedQuotesJson = prefs.getString(SettingsActivity.QUOTES_DATA_KEY, null)

        var quoteText = "No quotes available"
        var authorText = ""


//        currentTime = "12:30"

        if (cachedQuotesJson != null) {
            try {
                val gson = Gson()
                val type = object : TypeToken<Map<String, List<Quote>>>() {}.type
                val allQuotes: Map<String, List<Quote>> = gson.fromJson(cachedQuotesJson, type)

                // Get quote for current time. We persist the selected quote per widget per minute
                // so paging won't cause a new random selection.
                val selectedQuoteTimeKey = SELECTED_QUOTE_TIME_PREFIX + appWidgetId
                val selectedQuoteTextKey = SELECTED_QUOTE_TEXT_PREFIX + appWidgetId
                val selectedQuoteAuthorKey = SELECTED_QUOTE_AUTHOR_PREFIX + appWidgetId
                val persistedTime = prefs.getString(selectedQuoteTimeKey, null)
                val persistedText = prefs.getString(selectedQuoteTextKey, null)
                val persistedAuthor = prefs.getString(selectedQuoteAuthorKey, null)

                if (persistedTime != null && persistedTime == currentTime && !persistedText.isNullOrBlank()) {
                    // Use persisted selection
                    quoteText = persistedText
                    authorText = persistedAuthor ?: ""
                } else {
                    val quotesForTime = allQuotes[currentTime]
                    if (quotesForTime != null && quotesForTime.isNotEmpty()) {
                        val randomQuote = quotesForTime[Random.nextInt(quotesForTime.size)]
                        quoteText = randomQuote.text
                        authorText = "${randomQuote.title} - ${randomQuote.author}"
                        // persist selection
                        prefs.edit().putString(selectedQuoteTimeKey, currentTime)
                            .putString(selectedQuoteTextKey, quoteText)
                            .putString(selectedQuoteAuthorKey, authorText)
                            .apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Extract RGB parts (first 6 chars) for HTML font tags
        val boldColorRGB = "#" + timeColorHex.substring(0, 6)
        val mainColorRGB = "#" + textColorHex.substring(0, 6)

        // Split text at <b> tags and apply colors to each section
        val beforeBold = quoteText.substringBefore("<b>")
        val boldText = quoteText.substringAfter("<b>").substringBefore("</b>")
        val afterBold = quoteText.substringAfter("</b>")

        // Reconstruct with proper color wrapping
        val quoteWithColors = "<font color='$mainColorRGB'>$beforeBold</font><u><b><font color='$boldColorRGB'>$boldText</font></b></u><font color='$mainColorRGB'>$afterBold</font>"

        // Create the RemoteViews object
        val views = RemoteViews(context.packageName, R.layout.widget_quote)

        // Apply HTML formatting to quote
        val spanned = Html.fromHtml(quoteWithColors, Html.FROM_HTML_MODE_LEGACY)

        // Pagination: determine whether the quote fits the widget; if not, split into pages.
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        var minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        var minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val density = context.resources.displayMetrics.density
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        // Fallback sizes (dp) if launcher didn't provide dimensions
        if (minWidthDp <= 0) minWidthDp = 200
        if (minHeightDp <= 0) minHeightDp = 110
        val widthPx = (minWidthDp * density).toInt()
        val heightPx = (minHeightDp * density).toInt()

        // Layout padding/margins (dp -> px)
        val containerPaddingPx = (16 * density).toInt() // LinearLayout padding
        val authorMarginTopPx = (12 * density).toInt()

        // Measure author height
        val authorPaint = android.text.TextPaint().apply {
            isAntiAlias = true
            textSize = 12f * scaledDensity
        }
        val fm = authorPaint.fontMetrics
        val authorHeightPx = (fm.bottom - fm.top).toInt()

        // Available space for the quote TextView
        val availableWidth = (widthPx - containerPaddingPx * 2).coerceAtLeast(50)
        val availableHeight = (heightPx - containerPaddingPx * 2 - authorMarginTopPx - authorHeightPx).coerceAtLeast(20)

        // Prepare paint for quote text
        val quotePaint = android.text.TextPaint().apply {
            isAntiAlias = true
            textSize = 14f * scaledDensity
        }

        // Build a StaticLayout for the full text to measure lines
        val fullLayout = android.text.StaticLayout.Builder.obtain(spanned, 0, spanned.length, quotePaint, availableWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        val totalLines = fullLayout.lineCount
        // Determine how many lines fit in one page
        var linesPerPage = 0
        for (i in 0 until totalLines) {
            val bottom = fullLayout.getLineBottom(i)
            if (bottom <= availableHeight) {
                linesPerPage = i + 1
            } else break
        }

//        val prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val pageKey = PAGE_INDEX_PREF_PREFIX + appWidgetId
        var pageIndex = prefs.getInt(pageKey, 0)

        if (linesPerPage <= 0 || totalLines <= linesPerPage) {
            // Fits in one page: show full text and hide paging buttons
            views.setTextViewText(R.id.widget_quote, spanned)
            views.setViewVisibility(R.id.widget_page_up, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_page_down, android.view.View.GONE)
            // reset stored page index
            prefs.edit().putInt(pageKey, 0).apply()
            // Center vertically when single-page
            views.setInt(R.id.widget_quote, "setGravity", android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START)
        } else {
            // For multi-page content, top-align the quote text so pages start at the top
            views.setInt(R.id.widget_quote, "setGravity", android.view.Gravity.TOP or android.view.Gravity.START)
            val totalPages = (totalLines + linesPerPage - 1) / linesPerPage
            // Clamp page index
            if (pageIndex >= totalPages) pageIndex = totalPages - 1
            if (pageIndex < 0) pageIndex = 0
            prefs.edit().putInt(pageKey, pageIndex).apply()

            val startLine = pageIndex * linesPerPage
            val endLine = kotlin.math.min(startLine + linesPerPage, totalLines)
            val startOffset = fullLayout.getLineStart(startLine)
            val endOffset = fullLayout.getLineEnd(endLine - 1)

            val rawPage = spanned.subSequence(startOffset, endOffset)

            // If there is a next page, append an ellipsis to indicate truncation.
            if (pageIndex < totalPages - 1) {
                val sb = android.text.SpannableStringBuilder(rawPage)
                // Trim trailing whitespace
                var s = sb.toString()
                var trimEndIndex = s.length
                while (trimEndIndex > 0 && s[trimEndIndex - 1].isWhitespace()) trimEndIndex--

                // Decide whether to append ellipsis or replace trailing punctuation
                val shouldAppendEllipsis = if (trimEndIndex == 0) true else {
                    val lastChar = s[trimEndIndex - 1]
                    !(lastChar == '.' || lastChar == '!' || lastChar == '?')
                }

                if (shouldAppendEllipsis) {
                    // Replace trailing whitespace with ellipsis
                    if (trimEndIndex < sb.length) {
                        sb.replace(trimEndIndex, sb.length, "...")
                    } else {
                        sb.append("...")
                    }
                }

                views.setTextViewText(R.id.widget_quote, sb)
            } else {
                views.setTextViewText(R.id.widget_quote, rawPage)
            }

            // show/hide arrows appropriately
            views.setViewVisibility(R.id.widget_page_up, if (pageIndex > 0) android.view.View.VISIBLE else android.view.View.GONE)
            views.setViewVisibility(R.id.widget_page_down, if (pageIndex < totalPages - 1) android.view.View.VISIBLE else android.view.View.GONE)

            // Attach pending intents for paging (include widget id so handler updates correct widget)
            val upIntent = Intent(context, QuoteWidget::class.java).apply {
                action = ACTION_PAGE_UP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val upPending = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, upIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_page_up, upPending)

            val downIntent = Intent(context, QuoteWidget::class.java).apply {
                action = ACTION_PAGE_DOWN
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val downPending = PendingIntent.getBroadcast(context, appWidgetId * 10 + 3, downIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_page_down, downPending)
        }

        // Set author at bottom with semi-transparent text color
        views.setTextViewText(R.id.widget_author, authorText)
        // Create a semi-transparent version of the text color (50% opacity)
        val semitransparentTextColor = Color.argb(
            Color.alpha(textColor) / 2,
            Color.red(textColor),
            Color.green(textColor),
            Color.blue(textColor)
        )
        views.setTextColor(R.id.widget_author, semitransparentTextColor)
        views.setTextColor(R.id.widget_sync_button, textColor)
        // Color the page arrows to match the configured text color
        views.setTextColor(R.id.widget_page_up, textColor)
        views.setTextColor(R.id.widget_page_down, textColor)

        // Apply the first configured background color (RemoteViews doesn't support gradients)
        views.setInt(R.id.main, "setBackgroundColor", bgLeftColor)

        val syncIntent = Intent(context, QuoteWidget::class.java).apply {
            action = ACTION_SYNC
        }
        val syncPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            syncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_sync_button, syncPendingIntent)

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun hexToColor(hexColor: String): Int {
        return try {
            val rr = hexColor.substring(0, 2).toInt(16)
            val gg = hexColor.substring(2, 4).toInt(16)
            val bb = hexColor.substring(4, 6).toInt(16)
            val aa = hexColor.substring(6, 8).toInt(16)
            Color.argb(aa, rr, gg, bb)
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    private fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val now = System.currentTimeMillis()
        // Always align to the next real clock-minute boundary so that small
        // delays in alarm delivery (Doze mode, OS batching, etc.) can never
        // accumulate into noticeable drift — each tick self-corrects.
        val nextUpdateTimeMillis = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val intent = Intent(context, QuoteWidget::class.java).apply {
            action = ACTION_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextUpdateTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextUpdateTimeMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextUpdateTimeMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextUpdateTimeMillis,
                pendingIntent
            )
        }
    }
}
