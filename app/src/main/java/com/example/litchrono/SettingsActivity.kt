package com.example.litchrono

import android.os.Bundle
import android.widget.Button
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {
    companion object {
        const val PREFS_NAME = "litchrono_prefs"
        const val QUOTES_DATA_KEY = "quotes_data"
        const val KEY_FONT_NAME = "font_name"
        const val KEY_BG_LEFT_COLOR = "bg_left_color"
        const val KEY_BG_RIGHT_COLOR = "bg_right_color"
        const val KEY_GRADIENT_ANGLE = "gradient_angle"
        const val KEY_TEXT_COLOR = "text_color"
        const val KEY_TIME_COLOR = "time_color"

        // Default colors as rgba hex strings
        const val DEFAULT_BG_LEFT = "1f3a4dcc"
        const val DEFAULT_BG_RIGHT = "8b7b7ccc"
        const val DEFAULT_TEXT_COLOR = "adebb3ff"
        const val DEFAULT_TIME_COLOR = "e1c16eff"
        const val DEFAULT_FONT = "sans-serif"
        const val DEFAULT_GRADIENT_ANGLE = "45" // 0 degrees (left to right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Get current values
        val currentFont = prefs.getString(KEY_FONT_NAME, DEFAULT_FONT) ?: DEFAULT_FONT
        val currentBgLeft = prefs.getString(KEY_BG_LEFT_COLOR, DEFAULT_BG_LEFT) ?: DEFAULT_BG_LEFT
        val currentBgRight = prefs.getString(KEY_BG_RIGHT_COLOR, DEFAULT_BG_RIGHT) ?: DEFAULT_BG_RIGHT
        val currentGradientAngle = prefs.getString(KEY_GRADIENT_ANGLE, DEFAULT_GRADIENT_ANGLE) ?: DEFAULT_GRADIENT_ANGLE
        val currentTextColor = prefs.getString(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR) ?: DEFAULT_TEXT_COLOR
        val currentTimeColor = prefs.getString(KEY_TIME_COLOR, DEFAULT_TIME_COLOR) ?: DEFAULT_TIME_COLOR

        // Set up input fields
        val fontInput = findViewById<EditText>(R.id.font_input)
        val bgLeftInput = findViewById<EditText>(R.id.bg_left_color_input)
        val bgRightInput = findViewById<EditText>(R.id.bg_right_color_input)
        val gradientAngleInput = findViewById<EditText>(R.id.gradient_angle_input)
        val textColorInput = findViewById<EditText>(R.id.text_color_input)
        val timeColorInput = findViewById<EditText>(R.id.time_color_input)
        val bgLeftPickerButton = findViewById<Button>(R.id.bg_left_color_picker)
        val bgRightPickerButton = findViewById<Button>(R.id.bg_right_color_picker)
        val textColorPickerButton = findViewById<Button>(R.id.text_color_picker)
        val timeColorPickerButton = findViewById<Button>(R.id.time_color_picker)
        val saveButton = findViewById<Button>(R.id.save_button)
        val resetButton = findViewById<Button>(R.id.reset_button)

        // Swatches
        val bgLeftSwatch = findViewById<View>(R.id.bg_left_swatch)
        val bgRightSwatch = findViewById<View>(R.id.bg_right_swatch)
        val textColorSwatch = findViewById<View>(R.id.text_color_swatch)
        val timeColorSwatch = findViewById<View>(R.id.time_color_swatch)

        fontInput.setText(currentFont)
        bgLeftInput.setText(currentBgLeft)
        bgRightInput.setText(currentBgRight)
        gradientAngleInput.setText(currentGradientAngle)
        textColorInput.setText(currentTextColor)
        timeColorInput.setText(currentTimeColor)

        // Helper: normalize for picker; returns 8-char if requireAlpha, otherwise 6-char
        fun normalizeForPicker(input: String): String {
            var s = input.trim().removePrefix("#")
            // if too long (leftover from old bugs), take last 8 chars
            if (s.length > 8) s = s.takeLast(8)
            // If 6-digit provided, append full alpha
            if (s.length == 6) s = (s + "FF")
            // If shorter or invalid, fallback to default black opaque
            if (s.length != 8) s = "000000FF"
            return s.uppercase()
        }

        fun setSwatch(view: View, hex: String, hasAlpha: Boolean) {
            try {
                val h = hex.removePrefix("#")
                val colorInt = if (hasAlpha) {
                    // h = RRGGBBAA
                    val r = Integer.parseInt(h.substring(0,2),16)
                    val g = Integer.parseInt(h.substring(2,4),16)
                    val b = Integer.parseInt(h.substring(4,6),16)
                    val a = Integer.parseInt(h.substring(6,8),16)
                    android.graphics.Color.argb(a,r,g,b)
                } else {
                    val r = Integer.parseInt(h.substring(0,2),16)
                    val g = Integer.parseInt(h.substring(2,4),16)
                    val b = Integer.parseInt(h.substring(4,6),16)
                    android.graphics.Color.rgb(r,g,b)
                }
                view.setBackgroundColor(colorInt)
            } catch (_: Exception) { /* ignore */ }
        }

        // Initialize swatches from current prefs
        setSwatch(bgLeftSwatch, currentBgLeft, true)
        setSwatch(bgRightSwatch, currentBgRight, true)
            setSwatch(textColorSwatch, currentTextColor, true)
        setSwatch(timeColorSwatch, currentTimeColor, true)

        bgLeftPickerButton.setOnClickListener {
            val normalized = normalizeForPicker(bgLeftInput.text.toString())
            bgLeftInput.setText(normalized)
            ColorPickerDialog(this, normalized) { color ->
                bgLeftInput.setText(color)
                setSwatch(bgLeftSwatch, color, true)
            }.show()
        }

        bgRightPickerButton.setOnClickListener {
            val normalized = normalizeForPicker(bgRightInput.text.toString())
            bgRightInput.setText(normalized)
            ColorPickerDialog(this, normalized) { color ->
                bgRightInput.setText(color)
                setSwatch(bgRightSwatch, color, true)
            }.show()
        }

        textColorPickerButton.setOnClickListener {
            val normalized = normalizeForPicker(textColorInput.text.toString())
            textColorInput.setText(normalized)
            // Text color should be opaque but we still keep alpha; use full RRGGBBAA
            ColorPickerDialog(this, normalized) { color ->
                textColorInput.setText(color)
                setSwatch(textColorSwatch, color, true)
            }.show()
        }

        timeColorPickerButton.setOnClickListener {
            val normalized = normalizeForPicker(timeColorInput.text.toString())
            timeColorInput.setText(normalized)
            // Time color should be opaque but stored as RRGGBBAA
            ColorPickerDialog(this, normalized) { color ->
                timeColorInput.setText(color)
                setSwatch(timeColorSwatch, color, true)
            }.show()
        }

        saveButton.setOnClickListener {
            val font = fontInput.text.toString().trim()
            val bgLeft = bgLeftInput.text.toString().trim()
            val bgRight = bgRightInput.text.toString().trim()
            val gradientAngle = gradientAngleInput.text.toString().trim()
            val textColor = textColorInput.text.toString().trim()
            val timeColor = timeColorInput.text.toString().trim()

            if (font.isEmpty() || bgLeft.isEmpty() || bgRight.isEmpty() || gradientAngle.isEmpty() || textColor.isEmpty() || timeColor.isEmpty()) {
                showMessage("All fields are required")
                return@setOnClickListener
            }

            // Validate hex colors
            if (!isValidHexColor(bgLeft) || !isValidHexColor(bgRight) || !isValidHexColor(textColor) || !isValidHexColor(timeColor)) {
                showMessage("Colors must be 8-character hex (RRGGBBAA format)")
                return@setOnClickListener
            }

            // Validate angle (0-360)
            val angle = gradientAngle.toIntOrNull()
            if (angle == null || angle < 0 || angle > 360) {
                showMessage("Gradient angle must be between 0 and 360")
                return@setOnClickListener
            }

            prefs.edit().apply {
                putString(KEY_FONT_NAME, font)
                putString(KEY_BG_LEFT_COLOR, bgLeft)
                putString(KEY_BG_RIGHT_COLOR, bgRight)
                putString(KEY_GRADIENT_ANGLE, gradientAngle)
                putString(KEY_TEXT_COLOR, textColor)
                putString(KEY_TIME_COLOR, timeColor)
                apply()
            }

            showMessage("Settings saved!")
            setResult(RESULT_OK)
            finish()
        }

        resetButton.setOnClickListener {
            fontInput.setText(DEFAULT_FONT)
            bgLeftInput.setText(DEFAULT_BG_LEFT)
            bgRightInput.setText(DEFAULT_BG_RIGHT)
            gradientAngleInput.setText(DEFAULT_GRADIENT_ANGLE)
            textColorInput.setText(DEFAULT_TEXT_COLOR)
            timeColorInput.setText(DEFAULT_TIME_COLOR)
        }
    }

    private fun isValidHexColor(color: String): Boolean {
        return color.length == 8 && color.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun showMessage(message: String) {
        findViewById<TextView>(R.id.message_text).apply {
            text = message
            visibility = android.view.View.VISIBLE
        }
    }
}
