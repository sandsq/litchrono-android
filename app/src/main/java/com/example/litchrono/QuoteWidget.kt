package com.example.litchrono

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.RemoteViews
import com.example.litchrono.services.TimeTickService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class QuoteWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_UPDATE = "com.example.litchrono.WIDGET_UPDATE"
        const val EXTRA_LAST_SCHEDULED = "com.example.litchrono.EXTRA_LAST_SCHEDULED"
        const val EXTRA_FROM_TICK = "com.example.litchrono.EXTRA_FROM_TICK"
        private const val TAG = "QuoteWidget"
    }

    private fun startTimeTickService(context: Context) {
        val svcIntent = Intent(context, TimeTickService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent)
            } else {
                context.startService(svcIntent)
            }
            Log.d(TAG, "startTimeTickService: requested start")
        } catch (e: Exception) {
            Log.w(TAG, "startTimeTickService: failed to start service, falling back to alarms", e)
            // fallback to alarms
            scheduleNextUpdate(context)
        }
    }

    private fun stopTimeTickService(context: Context) {
        val svcIntent = Intent(context, TimeTickService::class.java)
        try {
            context.stopService(svcIntent)
            Log.d(TAG, "stopTimeTickService: requested stop")
        } catch (e: Exception) {
            Log.w(TAG, "stopTimeTickService: failed to stop service", e)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Start foreground service to receive ACTION_TIME_TICK reliably
        startTimeTickService(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform immediate update
        updateWidget(context)

        // Ensure service is running (best-effort); if start fails scheduled alarms will be used
        startTimeTickService(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Stop the foreground service (no widgets remain)
        stopTimeTickService(context)

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
            // If this update came from the system time tick, just update and don't schedule alarms
            val fromTick = intent.getBooleanExtra(EXTRA_FROM_TICK, false)
            if (fromTick) {
                val now = System.currentTimeMillis()
                Log.d(TAG, "onReceive: ACTION_UPDATE fromTick. now=$now")
                updateWidget(context)
                return
            }

            val now = System.currentTimeMillis()
            // Read the last scheduled time from the intent extras (the time this alarm was meant to fire)
            val lastScheduled = intent.getLongExtra(EXTRA_LAST_SCHEDULED, 0L)
            if (lastScheduled > 0L) {
                val drift = now - lastScheduled
                Log.d(TAG, "onReceive: ACTION_UPDATE fired. now=$now, lastScheduled=$lastScheduled, drift=${drift} ms")

                // If drift is large, reset the scheduling grid to now+60s to avoid very short or clustered intervals
                val maxAcceptableDrift = 10_000L // 10 seconds
                if (drift > maxAcceptableDrift) {
                    Log.w(TAG, "onReceive: large drift ($drift ms) detected — resetting schedule to now+60s")
                    updateWidget(context)
                    scheduleNextUpdate(context, null)
                } else {
                    updateWidget(context)
                    scheduleNextUpdate(context, lastScheduled)
                }
            } else {
                Log.d(TAG, "onReceive: ACTION_UPDATE fired. now=$now, no lastScheduled in intent")
                updateWidget(context)
                scheduleNextUpdate(context, null)
            }
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
        val bgRightColorHex = prefs.getString(SettingsActivity.KEY_BG_RIGHT_COLOR, SettingsActivity.DEFAULT_BG_RIGHT) ?: SettingsActivity.DEFAULT_BG_RIGHT
        val gradientAngleStr = prefs.getString(SettingsActivity.KEY_GRADIENT_ANGLE, SettingsActivity.DEFAULT_GRADIENT_ANGLE) ?: SettingsActivity.DEFAULT_GRADIENT_ANGLE
        val textColorHex = prefs.getString(SettingsActivity.KEY_TEXT_COLOR, SettingsActivity.DEFAULT_TEXT_COLOR) ?: SettingsActivity.DEFAULT_TEXT_COLOR
        val timeColorHex = prefs.getString(SettingsActivity.KEY_TIME_COLOR, SettingsActivity.DEFAULT_TIME_COLOR) ?: SettingsActivity.DEFAULT_TIME_COLOR

        // Convert hex to color integers
        val bgLeftColor = hexToColor(bgLeftColorHex)
        val bgRightColor = hexToColor(bgRightColorHex)
        val textColor = hexToColor(textColorHex)
        val gradientAngle = gradientAngleStr.toIntOrNull() ?: 0

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

        // Apply gradient background
        val angleRadians = Math.toRadians(gradientAngle.toDouble())
        val width = 300  // Approximate widget width
        val height = 300  // Approximate widget height
        val endX = width * Math.cos(angleRadians).toFloat()
        val endY = height * Math.sin(angleRadians).toFloat()

        val gradient = LinearGradient(
            0f, 0f, endX, endY,
            bgLeftColor, bgRightColor,
            Shader.TileMode.CLAMP
        )
        val drawable = ShapeDrawable().apply {
            paint.shader = gradient
        }
        // Note: RemoteViews has limited support for complex drawables,
        // so we'll use setBackgroundColor as a fallback blend
        // For true gradient support, we use the average of the two colors
        val avgColor = Color.argb(
            (Color.alpha(bgLeftColor) + Color.alpha(bgRightColor)) / 2,
            (Color.red(bgLeftColor) + Color.red(bgRightColor)) / 2,
            (Color.green(bgLeftColor) + Color.green(bgRightColor)) / 2,
            (Color.blue(bgLeftColor) + Color.blue(bgRightColor)) / 2
        )
        // Apply average color as background (RemoteViews doesn't support gradients)
        views.setInt(R.id.main, "setBackgroundColor", avgColor)

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

    // If lastScheduled is non-null, it should be the time (in millis) that the current alarm was scheduled to fire.
    // We compute nextScheduled = lastScheduled + 60_000 to avoid accumulating execution delay. If lastScheduled is null,
    // schedule roughly 60s from now.
    private fun scheduleNextUpdate(context: Context, lastScheduled: Long? = null) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val now = System.currentTimeMillis()
        // Candidate based on grid (lastScheduled + 60s) or naive now+60s for first run
        var candidate = if (lastScheduled != null && lastScheduled > 0L) {
            lastScheduled + 60_000L
        } else {
            now + 60_000L
        }

        // If candidate is in the past or too close to now (due to delivery delays), schedule ~60s from now
        // This avoids accidentally scheduling a time that's already passed (which could cause immediate firing
        // and then another alarm soon after, producing irregular intervals). Use a small safety margin.
        val minDelay = 5_000L // 5s
        if (candidate <= now + minDelay) {
            candidate = now + 60_000L
        }

        val nextScheduled = candidate

        // Log scheduling info
        Log.d(TAG, "scheduleNextUpdate: scheduling next at $nextScheduled (in ${nextScheduled - now} ms)")

        val intent = Intent(context, QuoteWidget::class.java).apply {
            action = ACTION_UPDATE
            putExtra(EXTRA_LAST_SCHEDULED, nextScheduled)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val canExact = alarmManager.canScheduleExactAlarms()
                Log.d(TAG, "scheduleNextUpdate: API >= S, canScheduleExactAlarms=$canExact")
                if (canExact) {
                    Log.d(TAG, "scheduleNextUpdate: using setExactAndAllowWhileIdle")
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextScheduled,
                        pendingIntent
                    )
                } else {
                    Log.d(TAG, "scheduleNextUpdate: cannot schedule exact alarms; using setAndAllowWhileIdle (inexact)")
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextScheduled,
                        pendingIntent
                    )
                }
            } else {
                // Use setExactAndAllowWhileIdle for API >= M, fallback to setExact for older devices
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.d(TAG, "scheduleNextUpdate: API >= M and < S; using setExactAndAllowWhileIdle")
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextScheduled,
                        pendingIntent
                    )
                } else {
                    Log.d(TAG, "scheduleNextUpdate: API < M; using setExact")
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        nextScheduled,
                        pendingIntent
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "scheduleNextUpdate: SecurityException while scheduling exact alarm; falling back to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextScheduled,
                pendingIntent
            )
        }
    }
}
