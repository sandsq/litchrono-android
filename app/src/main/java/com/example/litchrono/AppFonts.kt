package com.example.litchrono

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import java.util.Locale

object AppFonts {
    val suggestions = listOf(
        "Open Sans", "Lora", "sans-serif", "serif", "monospace",
        "sans-serif-light", "sans-serif-condensed", "casual", "cursive"
    )

    private val bundledFonts = mutableMapOf<Int, Typeface>()

    @Synchronized
    fun load(context: Context, name: String, style: Int = Typeface.NORMAL): Typeface {
        val resource = when (name.trim().lowercase(Locale.ROOT).replace(Regex("[\\s_-]+"), "")) {
            "opensans" -> R.font.open_sans
            "lora" -> R.font.lora
            else -> null
        }
        val family = if (resource != null) {
            bundledFonts.getOrPut(resource) {
                requireNotNull(ResourcesCompat.getFont(context.applicationContext, resource))
            }
        } else {
            Typeface.create(name.trim(), Typeface.NORMAL)
        }
        return Typeface.create(family, style)
    }
}
