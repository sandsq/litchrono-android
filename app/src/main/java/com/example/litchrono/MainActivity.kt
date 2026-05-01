package com.example.litchrono

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var timeTextView: TextView
    private lateinit var quoteTextView: TextView
    private lateinit var cacheInfoTextView: TextView
    private var allQuotes: Map<String, List<Quote>> = emptyMap()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())
    private var lastDisplayedMinute = -1
    private var hasRequestedExactAlarmPermission = false

    companion object {
        private const val PREFS_NAME = "litchrono_prefs"
        private const val QUOTES_DATA_KEY = "quotes_data"
        private const val LAST_FETCH_TIME_KEY = "last_fetch_time"
        private const val SETTINGS_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views first
        timeTextView = findViewById(R.id.timeTextView)
        quoteTextView = findViewById(R.id.quoteTextView)
        cacheInfoTextView = findViewById(R.id.cacheInfoTextView)

        // Add settings button
        val settingsButton = findViewById<ImageButton>(R.id.settings_button)
        settingsButton.setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_REQUEST_CODE)
        }

        // Apply custom settings after views are initialized
        applyCustomSettings()

        requestExactAlarmPermissionIfNeeded()

        // Load cached quotes; fetch only once when the app has no cached data yet.
        loadQuotes()

        // Update time immediately
        updateTime()
        // Start continuous time updates
        startTimeUpdates()
    }

    override fun onResume() {
        super.onResume()
        applyCustomSettings()
        updateTime()
        requestWidgetUpdate()
        startTimeUpdates()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            // Settings were saved, apply them immediately
            applyCustomSettings()
            updateQuote()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(timeUpdateRunnable)
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasRequestedExactAlarmPermission) {
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            return
        }

        hasRequestedExactAlarmPermission = true
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun requestWidgetUpdate() {
        sendBroadcast(
            Intent(this, QuoteWidget::class.java).apply {
                action = "com.example.litchrono.WIDGET_SYNC"
            }
        )
    }

    private fun applyCustomSettings() {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

        // Get custom settings
        val fontName = prefs.getString(SettingsActivity.KEY_FONT_NAME, SettingsActivity.DEFAULT_FONT) ?: SettingsActivity.DEFAULT_FONT
        val bgLeftColorHex = prefs.getString(SettingsActivity.KEY_BG_LEFT_COLOR, SettingsActivity.DEFAULT_BG_LEFT) ?: SettingsActivity.DEFAULT_BG_LEFT
        val bgRightColorHex = prefs.getString(SettingsActivity.KEY_BG_RIGHT_COLOR, SettingsActivity.DEFAULT_BG_RIGHT) ?: SettingsActivity.DEFAULT_BG_RIGHT
        val gradientAngleStr = prefs.getString(SettingsActivity.KEY_GRADIENT_ANGLE, SettingsActivity.DEFAULT_GRADIENT_ANGLE) ?: SettingsActivity.DEFAULT_GRADIENT_ANGLE
        val textColorHex = prefs.getString(SettingsActivity.KEY_TEXT_COLOR, SettingsActivity.DEFAULT_TEXT_COLOR) ?: SettingsActivity.DEFAULT_TEXT_COLOR
        val timeColorHex = prefs.getString(SettingsActivity.KEY_TIME_COLOR, SettingsActivity.DEFAULT_TIME_COLOR) ?: SettingsActivity.DEFAULT_TIME_COLOR

        // Convert hex to color integers
        val bgLeftColor = hexToColor(bgLeftColorHex)
        val bgRightColor = hexToColor(bgRightColorHex)
        val textColor = hexToColor(textColorHex)
        val timeColor = hexToColor(timeColorHex)

        // Parse gradient angle (in degrees)
        val gradientAngle = gradientAngleStr.toIntOrNull() ?: 0

        // Apply gradient background with angle
        val mainView = findViewById<android.view.ViewGroup>(R.id.main)

        // Use ViewTreeObserver to ensure layout is measured
        mainView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mainView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val angleRadians = Math.toRadians(gradientAngle.toDouble())
                val width = mainView.width.toFloat()
                val height = mainView.height.toFloat()
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
                mainView.background = drawable
            }
        })

        // Apply text colors immediately
        timeTextView.setTextColor(timeColor)
        // Don't set color on quoteTextView - let HTML font tags control colors
        // quoteTextView.setTextColor(textColor)
        cacheInfoTextView.setTextColor(textColor)

        // Apply font (using default typeface since custom fonts require files)
        try {
            val typeface = android.graphics.Typeface.create(fontName, android.graphics.Typeface.NORMAL)
            timeTextView.typeface = typeface
            quoteTextView.typeface = typeface
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hexToColor(hexColor: String): Int {
        return try {
            // Convert RRGGBBAA format to color
            val rr = hexColor.substring(0, 2).toInt(16)
            val gg = hexColor.substring(2, 4).toInt(16)
            val bb = hexColor.substring(4, 6).toInt(16)
            val aa = hexColor.substring(6, 8).toInt(16)
            Color.argb(aa, rr, gg, bb)
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    private fun loadQuotes() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val cachedQuotesJson = prefs.getString(QUOTES_DATA_KEY, null)

        // Load cached data if available
        if (cachedQuotesJson != null) {
            try {
                val gson = Gson()
                val type = object : com.google.gson.reflect.TypeToken<Map<String, List<Quote>>>() {}.type
                allQuotes = gson.fromJson(cachedQuotesJson, type)
                updateQuote()
                updateCacheInfo(cachedQuotesJson)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // App-side fetching is only for the initial cache fill after install.
        // Ongoing refreshes are handled by the widget.
        if (cachedQuotesJson == null) {
            fetchQuotesFromServer()
        } else {
            updateCacheInfo(cachedQuotesJson)
        }
    }

    private fun updateCacheInfo(cachedQuotesJson: String?) {
        if (cachedQuotesJson.isNullOrBlank() || allQuotes.isEmpty()) {
            cacheInfoTextView.text = "Quote cache: not loaded yet"
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastFetchTime = prefs.getLong(LAST_FETCH_TIME_KEY, 0L)
        val totalQuoteCount = allQuotes.values.sumOf { it.size }
        val timeSlotCount = allQuotes.size
        val lastUpdatedText = if (lastFetchTime > 0L) {
            val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            dateFormat.format(java.util.Date(lastFetchTime))
        } else {
            "unknown"
        }

        cacheInfoTextView.text = "Last updated: $lastUpdatedText • $totalQuoteCount quotes"
    }

    private fun fetchQuotesFromServer() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://raw.githubusercontent.com/sandsq/time_of_day_quotes/refs/heads/main/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(QuoteApiService::class.java)
        val call = service.getQuotes()

        call.enqueue(object : Callback<Map<String, List<Quote>>> {
            override fun onResponse(call: Call<Map<String, List<Quote>>>, response: Response<Map<String, List<Quote>>>) {
                if (response.isSuccessful && response.body() != null) {
                    allQuotes = response.body()!!
                    // Save to local storage
                    saveQuotesToCache(allQuotes)
                    updateQuote()
                    updateCacheInfo(Gson().toJson(allQuotes))
                }
            }

            override fun onFailure(call: Call<Map<String, List<Quote>>>, t: Throwable) {
                // Only show error if we don't have cached data
                if (allQuotes.isEmpty()) {
                    quoteTextView.text = "Failed to load quotes"
                    updateCacheInfo(null)
                }
                t.printStackTrace()
            }
        })
    }

    private fun saveQuotesToCache(quotes: Map<String, List<Quote>>) {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val gson = Gson()
            val quotesJson = gson.toJson(quotes)
            prefs.edit().apply {
                putString(QUOTES_DATA_KEY, quotesJson)
                putLong(LAST_FETCH_TIME_KEY, System.currentTimeMillis())
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTime() {
        val currentTime = timeFormat.format(Calendar.getInstance().time)
        // Support HTML tags like <u>text</u> for underline
        timeTextView.text = Html.fromHtml(currentTime, Html.FROM_HTML_MODE_LEGACY)
        // Add margins to quote text
        val params = quoteTextView.layoutParams as android.view.ViewGroup.MarginLayoutParams
        params.setMargins(100, 16, 100, 16)  // left, top, right, bottom
        quoteTextView.layoutParams = params
    }

    private fun updateQuote() {
        if (allQuotes.isEmpty()) {
            return // Don't crash, just skip if quotes aren't loaded yet
        }

        // Get current time in HH:mm format
        val currentTime = timeFormat.format(Calendar.getInstance().time)

        // Get quotes for this time period
        val quotesForTime = allQuotes[currentTime]

        if (quotesForTime != null && quotesForTime.isNotEmpty()) {
            // Pick a random quote from this time period
            val randomQuote = quotesForTime[Random.nextInt(quotesForTime.size)]
            // Wrap <b> tags with <u> and <font color> tags for underline and color
            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            val timeColorHex = prefs.getString(SettingsActivity.KEY_TIME_COLOR, SettingsActivity.DEFAULT_TIME_COLOR) ?: SettingsActivity.DEFAULT_TIME_COLOR
            val textColorHex = prefs.getString(SettingsActivity.KEY_TEXT_COLOR, SettingsActivity.DEFAULT_TEXT_COLOR) ?: SettingsActivity.DEFAULT_TEXT_COLOR

            // Extract just RGB part (first 6 chars) for HTML font tags (HTML doesn't support alpha in font color)
            val boldColorRGB = "#" + timeColorHex.substring(0, 6)
            val mainColorRGB = "#" + textColorHex.substring(0, 6)

            // Process <b> tags (may be multiple). Build the output by wrapping each non-bold
            // segment in the main color and each <b>...</b> in the bold/time color. This avoids
            // wrapping the whole quote in a single <font> which can override nested colors.
            val sb = StringBuilder()
            val pattern = Regex("(?s)<b>(.*?)</b>")
            val text = randomQuote.text
            var lastIndex = 0
            for (m in pattern.findAll(text)) {
                val start = m.range.first
                if (start > lastIndex) {
                    val before = text.substring(lastIndex, start)
                    sb.append("<font color='$mainColorRGB'>").append(before).append("</font>")
                }
                val inner = m.groupValues[1]
                sb.append("<u><b><font color='$boldColorRGB'>").append(inner).append("</font></b></u>")
                lastIndex = m.range.last + 1
            }
            if (lastIndex < text.length) {
                val tail = text.substring(lastIndex)
                sb.append("<font color='$mainColorRGB'>").append(tail).append("</font>")
            }
            val quoteFinalColor = sb.toString()

            // Convert HTML tags to formatted text and add attribution
            val displayText = "$quoteFinalColor<br><br><font color='$mainColorRGB'>${randomQuote.title} - ${randomQuote.author}</font>"
            val spanned = Html.fromHtml(displayText, Html.FROM_HTML_MODE_LEGACY)

            quoteTextView.text = spanned
        }
    }

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            // Update time every second to ensure it always shows current time
            updateTime()

            // Update quote only when minute changes
            val calendar = Calendar.getInstance()
            val currentMinute = calendar.get(Calendar.MINUTE)
            if (currentMinute != lastDisplayedMinute) {
                lastDisplayedMinute = currentMinute
                updateQuote()
            }

            // Schedule next update in 500ms (twice per second for smooth updates)
            handler.postDelayed(this, 500)
        }
    }

    private fun startTimeUpdates() {
        handler.post(timeUpdateRunnable)
    }
}
