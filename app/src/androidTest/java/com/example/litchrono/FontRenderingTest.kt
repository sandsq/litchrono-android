package com.example.litchrono

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.Parcel
import android.text.Html
import android.text.TextPaint
import android.text.style.StyleSpan
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontRenderingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledFamiliesLoadWithAliasesAndStyles() {
        for (name in listOf("Open Sans", "Lora")) {
            val regular = AppFonts.load(context, name)
            assertNotEquals(Typeface.DEFAULT, regular)
            assertEquals(Typeface.NORMAL, regular.style)
            assertEquals(Typeface.BOLD, AppFonts.load(context, name, Typeface.BOLD).style)
            assertEquals(Typeface.ITALIC, AppFonts.load(context, name, Typeface.ITALIC).style)
            assertEquals(Typeface.BOLD_ITALIC, AppFonts.load(context, name, Typeface.BOLD_ITALIC).style)
        }
        assertEquals(AppFonts.load(context, "Open Sans"), AppFonts.load(context, " open_sans "))
        assertEquals(AppFonts.load(context, "Open Sans"), AppFonts.load(context, "open-sans"))
        assertEquals(AppFonts.load(context, "Lora"), AppFonts.load(context, "LORA"))
        assertEquals(Typeface.create("serif", Typeface.NORMAL), AppFonts.load(context, "serif"))
    }

    @Test
    fun styledTextMeasuresAndRendersWithEachFont() {
        val text = Html.fromHtml(
            "A quote with <b><u><font color='#ff0000'>time</font></u></b> in it.",
            Html.FROM_HTML_MODE_LEGACY
        )
        for (name in listOf("Open Sans", "Lora", "serif", "monospace")) {
            val paint = TextPaint().apply {
                isAntiAlias = true
                textSize = 28f
                typeface = AppFonts.load(context, name)
                color = Color.WHITE
            }
            val layout = WidgetTextRenderer.layout(text, paint, 240)
            assertSame(paint.typeface, layout.paint.typeface)
            assertTrue(layout.lineCount > 1)
            assertTrue(text.getSpans(0, text.length, StyleSpan::class.java).isNotEmpty())
            val bitmap = WidgetTextRenderer.bitmap(layout)
            assertEquals(240, bitmap.width)
            assertEquals(layout.height, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue(pixels.any { Color.alpha(it) > 0 })
            assertTrue(pixels.any { Color.alpha(it) > 0 && Color.red(it) > Color.green(it) })
            bitmap.recycle()
        }
    }

    @Test
    fun bitmapMemoryAndPageHeightAreBounded() {
        val paint = TextPaint().apply {
            textSize = 28f
            typeface = AppFonts.load(context, "Lora")
        }
        val page = WidgetTextRenderer.layout("A long quote ".repeat(100), paint, 240, maxLines = 2)
        assertEquals(2, page.lineCount)
        assertTrue(page.getEllipsisCount(1) > 0)
        val bitmap = WidgetTextRenderer.bitmap(page, height = 4000, centerVertically = true)
        assertTrue(bitmap.width <= 1024 && bitmap.height <= 1024)
        assertTrue(bitmap.allocationByteCount <= 262144 * 4)
        bitmap.recycle()
        val smallBudget = WidgetTextRenderer.bitmap(page, height = 4000, maxPixels = 16384)
        assertTrue(smallBudget.allocationByteCount <= 16384 * 4)
        smallBudget.recycle()
    }

    @Test
    fun widgetImagesAndAccessibilitySurviveRemoteViewsParcel() {
        val paint = TextPaint().apply {
            textSize = 28f
            typeface = AppFonts.load(context, "Open Sans")
        }
        val bitmap = WidgetTextRenderer.bitmap(WidgetTextRenderer.layout("A quote", paint, 240))
        val views = RemoteViews(context.packageName, R.layout.widget_quote).apply {
            setImageViewBitmap(R.id.widget_quote, bitmap)
            setImageViewBitmap(R.id.widget_author, bitmap)
            setContentDescription(R.id.widget_quote, "A quote")
            setContentDescription(R.id.widget_author, "An author")
        }
        val parcel = Parcel.obtain()
        try {
            views.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = RemoteViews.CREATOR.createFromParcel(parcel)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val root = restored.apply(context, null)
                val quote = root.findViewById<ImageView>(R.id.widget_quote)
                val author = root.findViewById<ImageView>(R.id.widget_author)
                assertTrue(quote.drawable is BitmapDrawable)
                assertTrue(author.drawable is BitmapDrawable)
                assertEquals("A quote", quote.contentDescription)
                assertEquals("An author", author.contentDescription)
            }
        } finally {
            parcel.recycle()
            bitmap.recycle()
        }
    }
}
