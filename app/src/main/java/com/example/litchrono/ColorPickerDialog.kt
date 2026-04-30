package com.example.litchrono

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.widget.SeekBar
import android.widget.TextView
import android.widget.LinearLayout
import android.view.View

class ColorPickerDialog(
    context: Context,
    private val initialColor: String,
    private val includeAlpha: Boolean = true,
    private val onColorPicked: (String) -> Unit
) : Dialog(context) {

    init {
        setTitle("Pick a Color")
        val rootView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(20, 20, 20, 20)
        }

        // Convert initial hex to RGB(A)
        val hexClean = initialColor.trim().removePrefix("#")
        val (rInit, gInit, bInit, aInit) = try {
            if (hexClean.length >= 6) {
                val r = Integer.parseInt(hexClean.substring(0, 2), 16)
                val g = Integer.parseInt(hexClean.substring(2, 4), 16)
                val b = Integer.parseInt(hexClean.substring(4, 6), 16)
                val a = if (hexClean.length >= 8) Integer.parseInt(hexClean.substring(6, 8), 16) else 255
                Quadruple(r, g, b, a)
            } else Quadruple(255, 255, 255, 255)
        } catch (e: Exception) { Quadruple(255,255,255,255) }

        // Color preview
        val previewView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
            setBackgroundColor(Color.argb(aInit, rInit, gInit, bInit))
        }
        rootView.addView(previewView)

        // Red SeekBar
        rootView.addView(createLabel(context, "Red: $rInit"))
        val redSeekBar = SeekBar(context).apply {
            max = 255
            progress = rInit
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootView.addView(redSeekBar)

        // Green SeekBar
        rootView.addView(createLabel(context, "Green: $gInit"))
        val greenSeekBar = SeekBar(context).apply {
            max = 255
            progress = gInit
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootView.addView(greenSeekBar)

        // Blue SeekBar
        rootView.addView(createLabel(context, "Blue: $bInit"))
        val blueSeekBar = SeekBar(context).apply {
            max = 255
            progress = bInit
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootView.addView(blueSeekBar)

        // Alpha SeekBar (optional)
        val alphaSeekBar = SeekBar(context)
        if (includeAlpha) {
            rootView.addView(createLabel(context, "Alpha: $aInit"))
            alphaSeekBar.apply {
                max = 255
                progress = aInit
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rootView.addView(alphaSeekBar)
        } else {
            // If alpha not included, default alpha value
            alphaSeekBar.apply { max = 255; progress = 255 }
        }

        // Hex display
        val hexDisplay = TextView(context).apply {
            val initialHex = if (includeAlpha) initialColor else {
                // show RGB only
                String.format("%02X%02X%02X", rInit, gInit, bInit)
            }
            text = "Hex: $initialHex"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 10, 0, 10)
        }
        rootView.addView(hexDisplay)

        // Update listeners
        val updateColor = {
            val newR = redSeekBar.progress
            val newG = greenSeekBar.progress
            val newB = blueSeekBar.progress
            val newA = alphaSeekBar.progress

            previewView.setBackgroundColor(Color.argb(newA, newR, newG, newB))
            val hexColor = if (includeAlpha) rgbaToHex(newR, newG, newB, newA) else String.format("%02X%02X%02X", newR, newG, newB)
            hexDisplay.text = "Hex: $hexColor"

            // Update labels (positions are stable)
            (rootView.getChildAt(1) as TextView).text = "Red: $newR"
            (rootView.getChildAt(3) as TextView).text = "Green: $newG"
            (rootView.getChildAt(5) as TextView).text = "Blue: $newB"
            if (includeAlpha) (rootView.getChildAt(7) as TextView).text = "Alpha: $newA"
        }

        redSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateColor()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        greenSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateColor()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        blueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateColor()
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        if (includeAlpha) {
            alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = updateColor()
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // Buttons
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 10, 0, 0)
        }

        val okButton = android.widget.Button(context).apply {
            text = "OK"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setOnClickListener {
                val finalColor = if (includeAlpha) rgbaToHex(
                    redSeekBar.progress,
                    greenSeekBar.progress,
                    blueSeekBar.progress,
                    alphaSeekBar.progress
                ) else {
                    // Return RGB-only (RRGGBB) when alpha choice is disabled
                    String.format("%02X%02X%02X", redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
                }
                onColorPicked(finalColor)
                dismiss()
            }
        }
        buttonLayout.addView(okButton)

        val cancelButton = android.widget.Button(context).apply {
            text = "Cancel"
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setOnClickListener { dismiss() }
        }
        buttonLayout.addView(cancelButton)

        rootView.addView(buttonLayout)

        setContentView(rootView)
    }

    private fun createLabel(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 10, 0, 5)
        }
    }

    private fun hexToRgba(hex: String): Quadruple<Int, Int, Int, Int> {
        return try {
            val r = hex.substring(0, 2).toInt(16)
            val g = hex.substring(2, 4).toInt(16)
            val b = hex.substring(4, 6).toInt(16)
            val a = hex.substring(6, 8).toInt(16)
            Quadruple(r, g, b, a)
        } catch (e: Exception) {
            Quadruple(255, 255, 255, 255)
        }
    }

    private fun rgbaToHex(r: Int, g: Int, b: Int, a: Int): String {
        return String.format("%02X%02X%02X%02X", r, g, b, a)
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
