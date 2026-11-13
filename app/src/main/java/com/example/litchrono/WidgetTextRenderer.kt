package com.example.litchrono

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.min
import kotlin.math.sqrt

internal object WidgetTextRenderer {
    fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        includePad: Boolean = false,
        maxLines: Int = Int.MAX_VALUE
    ): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(alignment)
        .setLineSpacing(0f, 1f)
        .setIncludePad(includePad)
        .setMaxLines(maxLines)
        .setEllipsize(if (maxLines < Int.MAX_VALUE) TextUtils.TruncateAt.END else null)
        .build()

    // RemoteViews cannot transfer custom Typeface objects to the launcher. Draw locally,
    // keeping bitmap memory bounded even for large widgets and high-density screens.
    fun bitmap(
        layout: StaticLayout,
        height: Int = layout.height,
        centerVertically: Boolean = false,
        maxPixels: Int = 262144
    ): Bitmap {
        val width = layout.width.coerceAtLeast(1)
        val drawHeight = height.coerceAtLeast(1)
        val scale = min(
            1.0,
            min(
                1024.0 / maxOf(width, drawHeight),
                sqrt(maxPixels.coerceAtLeast(1).toDouble() / (width.toDouble() * drawHeight))
            )
        ).toFloat()
        return Bitmap.createBitmap(
            (width * scale).toInt().coerceAtLeast(1),
            (drawHeight * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        ).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.scale(bitmap.width.toFloat() / width, bitmap.height.toFloat() / drawHeight)
            if (centerVertically) {
                canvas.translate(0f, ((drawHeight - layout.height) / 2f).coerceAtLeast(0f))
            }
            layout.draw(canvas)
        }
    }
}
