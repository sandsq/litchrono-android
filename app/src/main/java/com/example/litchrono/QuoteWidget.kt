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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class QuoteWidget : AppWidgetProvider() {
    companion object {
        private const val ACTION_UPDATE = "com.example.litchrono.WIDGET_UPDATE"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Trigger immediate update
        val updateIntent = Intent(context, QuoteWidget::class.java).apply {
            action = ACTION_UPDATE
        }
        context.sendBroadcast(updateIntent)

        // Schedule next update at the next minute boundary
        scheduleNextUpdate(context)
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
        if (intent.action == ACTION_UPDATE) {
            updateWidget(context)
            scheduleNextUpdate(context)
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
        val currentTime = timeFormat.format(Calendar.getInstance().time)

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

        if (cachedQuotesJson != null) {
            try {
                val gson = Gson()
                val type = object : TypeToken<Map<String, List<Quote>>>() {}.type
                val allQuotes: Map<String, List<Quote>> = gson.fromJson(cachedQuotesJson, type)

                // Get quote for current time
                val quotesForTime = allQuotes[currentTime]
                if (quotesForTime != null && quotesForTime.isNotEmpty()) {
                    val randomQuote = quotesForTime[Random.nextInt(quotesForTime.size)]
                    quoteText = randomQuote.text
                    authorText = "${randomQuote.title} - ${randomQuote.author}"
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

        // Set quote with HTML formatted text (colors applied via HTML tags)
        views.setTextViewText(R.id.widget_quote, spanned)

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

        // Apply the first configured background color (RemoteViews doesn't support gradients)
        views.setInt(R.id.main, "setBackgroundColor", bgLeftColor)

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

        // Calculate the next minute boundary
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

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
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}
