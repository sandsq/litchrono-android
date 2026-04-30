package com.example.litchrono

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.Html
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class QuoteWidgetProvider : AppWidgetProvider() {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val thisAppWidget = ComponentName(context.packageName, QuoteWidgetProvider::class.java.name)
        val ids = appWidgetManager.getAppWidgetIds(thisAppWidget)
        for (appWidgetId in ids) {
            redrawWidgetFromData(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Register a runtime receiver for ACTION_TIME_TICK to get minute updates
        if (Companion.timeTickReceiver == null) {
            Companion.timeTickReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_TIME_TICK) {
                        val appWidgetManager = AppWidgetManager.getInstance(ctx)
                        val thisAppWidget = ComponentName(ctx.packageName, QuoteWidgetProvider::class.java.name)
                        val ids = appWidgetManager.getAppWidgetIds(thisAppWidget)
                        for (appWidgetId in ids) {
                            updateAppWidget(ctx, appWidgetManager, appWidgetId)
                        }
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_TIME_TICK)
            context.applicationContext.registerReceiver(Companion.timeTickReceiver, filter)
        }

        // Start the foreground service automatically for more reliable minute updates
        try {
            val svcIntent = Intent(context, QuoteUpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent)
            } else {
                context.startService(svcIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Unregister the runtime receiver when no widgets remain
        if (Companion.timeTickReceiver != null) {
            try {
                context.applicationContext.unregisterReceiver(Companion.timeTickReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            Companion.timeTickReceiver = null
        }

        // Stop the foreground service when the last widget is removed
        try {
            val svcIntent = Intent(context, QuoteUpdateService::class.java)
            context.stopService(svcIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Keep default handling (e.g., APPWIDGET_UPDATE). Runtime-registered receiver handles TIME_TICK.
    }

    companion object {
        var timeTickReceiver: BroadcastReceiver? = null

        // Create RemoteViews (centralized) for the widget based on current data
        fun createWidgetRemoteView(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quote)

            // Set background to the left background color from settings (use single color - widgets don't support dynamic gradients)
            try {
                val prefsSettings = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val bgLeftHex = prefsSettings.getString(SettingsActivity.KEY_BG_LEFT_COLOR, SettingsActivity.DEFAULT_BG_LEFT) ?: SettingsActivity.DEFAULT_BG_LEFT
                val colorInt = hexToColorARGB(bgLeftHex)
                views.setInt(R.id.widget_root, "setBackgroundColor", colorInt)
            } catch (e: Exception) {
                // ignore
            }

            // Determine current time key (HH:mm) used by the quotes map
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)

            // Load cached quote from preferences
            val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val cachedJson = prefs.getString(MainActivity.QUOTES_DATA_KEY, null)
            var display = ""
            var attributionText = ""
            var attributionColorInt = android.graphics.Color.WHITE
            if (cachedJson != null) {
                try {
                    val gson = com.google.gson.Gson()
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, List<Quote>>>() {}.type
                    val allQuotes: Map<String, List<Quote>> = gson.fromJson(cachedJson, type)
                    val quoteList = allQuotes[currentTime]
                    if (quoteList != null && quoteList.isNotEmpty()) {
                        val q = quoteList[kotlin.random.Random.nextInt(quoteList.size)]
                        val prefsSettings = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                        val timeColorHex = prefsSettings.getString(SettingsActivity.KEY_TIME_COLOR, SettingsActivity.DEFAULT_TIME_COLOR) ?: SettingsActivity.DEFAULT_TIME_COLOR
                        val textColorHex = prefsSettings.getString(SettingsActivity.KEY_TEXT_COLOR, SettingsActivity.DEFAULT_TEXT_COLOR) ?: SettingsActivity.DEFAULT_TEXT_COLOR
                        val boldColorRGB = "#" + timeColorHex.substring(0, 6)
                        val mainColorRGB = "#" + textColorHex.substring(0, 6)

                        val beforeBold = q.text.substringBefore("<b>")
                        val boldText = q.text.substringAfter("<b>").substringBefore("</b>")
                        val afterBold = q.text.substringAfter("</</b>")

                        val quoteFinalColor = "<font color='" + mainColorRGB + "'>" + beforeBold + "</font><u><b><font color='" + boldColorRGB + "'>" + boldText + "</font></b></u><font color='" + mainColorRGB + "'>" + afterBold + "</font>"

                        // Set the attribution separately via RemoteViews (so we can apply proper alpha to the color)
                        attributionText = "${q.title} - ${q.author}"
                        attributionColorInt = try {
                            hexToColorARGBWithFraction(textColorHex, 0.5f)
                        } catch (e: Exception) {
                            android.graphics.Color.WHITE
                        }

                        display = quoteFinalColor
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (display.isEmpty()) {
                display = "Loading quote..."
                attributionText = ""
            }

            val spDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Html.fromHtml(display, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(display)
            views.setTextViewText(R.id.widget_quote, spDisplay)

            // Set attribution text and color via RemoteViews so alpha works correctly
            views.setTextViewText(R.id.widget_attribution, attributionText)
            try {
                views.setInt(R.id.widget_attribution, "setTextColor", attributionColorInt)
            } catch (e: Exception) {
                // fallback: ignore
            }

            // Apply the same click behavior: open MainActivity
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_quote, pendingIntent)

            return views
        }

        fun redrawWidgetFromData(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = createWidgetRemoteView(context)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quote)

            // Set background to the left background color from settings (use single color - widgets don't support dynamic gradients)
            try {
                val prefsSettings = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                val bgLeftHex = prefsSettings.getString(SettingsActivity.KEY_BG_LEFT_COLOR, SettingsActivity.DEFAULT_BG_LEFT) ?: SettingsActivity.DEFAULT_BG_LEFT
                val colorInt = hexToColorARGB(bgLeftHex)
                views.setInt(R.id.widget_root, "setBackgroundColor", colorInt)
            } catch (e: Exception) {
                // ignore
            }

            // Determine current time key (HH:mm) used by the quotes map
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Calendar.getInstance().time)

            // Load cached quote from preferences
            val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val cachedJson = prefs.getString(MainActivity.QUOTES_DATA_KEY, null)
            var display = ""
            var attributionText = ""
            var attributionColorInt = android.graphics.Color.WHITE
            if (cachedJson != null) {
                try {
                    val gson = com.google.gson.Gson()
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, List<Quote>>>() {}.type
                    val allQuotes: Map<String, List<Quote>> = gson.fromJson(cachedJson, type)
                    val quoteList = allQuotes[currentTime]
                    if (quoteList != null && quoteList.isNotEmpty()) {
                        val q = quoteList[kotlin.random.Random.nextInt(quoteList.size)]
                        val prefsSettings = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
                        val timeColorHex = prefsSettings.getString(SettingsActivity.KEY_TIME_COLOR, SettingsActivity.DEFAULT_TIME_COLOR) ?: SettingsActivity.DEFAULT_TIME_COLOR
                        val textColorHex = prefsSettings.getString(SettingsActivity.KEY_TEXT_COLOR, SettingsActivity.DEFAULT_TEXT_COLOR) ?: SettingsActivity.DEFAULT_TEXT_COLOR
                        val boldColorRGB = "#" + timeColorHex.substring(0, 6)
                        val mainColorRGB = "#" + textColorHex.substring(0, 6)

                        val beforeBold = q.text.substringBefore("<b>")
                        val boldText = q.text.substringAfter("<b>").substringBefore("</b>")
                        val afterBold = q.text.substringAfter("</</b>")

                        val quoteFinalColor = "<font color='" + mainColorRGB + "'>" + beforeBold + "</font><u><b><font color='" + boldColorRGB + "'>" + boldText + "</font></b></u><font color='" + mainColorRGB + "'>" + afterBold + "</font>"

                        // Set the attribution separately via RemoteViews (so we can apply proper alpha to the color)
                        attributionText = "${q.title} - ${q.author}"
                        attributionColorInt = try {
                            hexToColorARGBWithFraction(textColorHex, 0.5f)
                        } catch (e: Exception) {
                            android.graphics.Color.WHITE
                        }

                        display = quoteFinalColor
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (display.isEmpty()) {
                display = "Loading quote..."
                attributionText = ""
            }

            val spDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) Html.fromHtml(display, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(display)
            views.setTextViewText(R.id.widget_quote, spDisplay)

            // Set attribution text and color via RemoteViews so alpha works correctly
            views.setTextViewText(R.id.widget_attribution, attributionText)
            try {
                views.setInt(R.id.widget_attribution, "setTextColor", attributionColorInt)
            } catch (e: Exception) {
                // fallback: ignore
            }

            // Apply the same click behavior: open MainActivity
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_quote, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun convertToArgbWithAlpha(hexRrGgBbAa: String, alphaFraction: Float): String {
            try {
                // hexRrGgBbAa is RRGGBBAA in MainActivity's prefs, we want to construct #AARRGGBB for HTML
                val rr = hexRrGgBbAa.substring(0, 2)
                val gg = hexRrGgBbAa.substring(2, 4)
                val bb = hexRrGgBbAa.substring(4, 6)
                val aa = if (hexRrGgBbAa.length >= 8) hexRrGgBbAa.substring(6, 8) else "FF"
                val alpha = (Integer.parseInt(aa, 16) * alphaFraction).toInt()
                val alphaHex = alpha.coerceIn(0, 255).toString(16).padStart(2, '0')
                return "#${rr}${gg}${bb}${alphaHex}"
            } catch (e: Exception) {
                return "#80000000"
            }
        }

        private fun hexToColorARGB(hexRrGgBbAa: String): Int {
            try {
                val rr = hexRrGgBbAa.substring(0, 2).toInt(16)
                val gg = hexRrGgBbAa.substring(2, 4).toInt(16)
                val bb = hexRrGgBbAa.substring(4, 6).toInt(16)
                val aa = if (hexRrGgBbAa.length >= 8) hexRrGgBbAa.substring(6, 8).toInt(16) else 255
                return android.graphics.Color.argb(aa, rr, gg, bb)
            } catch (e: Exception) {
                return android.graphics.Color.BLACK
            }
        }

        private fun hexToColorARGBWithFraction(hexRrGgBbAa: String, alphaFraction: Float): Int {
            try {
                val rr = hexRrGgBbAa.substring(0, 2).toInt(16)
                val gg = hexRrGgBbAa.substring(2, 4).toInt(16)
                val bb = hexRrGgBbAa.substring(4, 6).toInt(16)
                val aa = if (hexRrGgBbAa.length >= 8) hexRrGgBbAa.substring(6, 8).toInt(16) else 255
                val resultA = (aa * alphaFraction).toInt().coerceIn(0, 255)
                return android.graphics.Color.argb(resultA, rr, gg, bb)
            } catch (e: Exception) {
                return android.graphics.Color.BLACK
            }
        }
    }
}
