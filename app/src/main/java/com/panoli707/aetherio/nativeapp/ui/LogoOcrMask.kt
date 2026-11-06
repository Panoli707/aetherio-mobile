package com.panoli707.aetherio.nativeapp.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/** Inspection copy only: transparent black/white/coloured lettering needs an opaque OCR background. */
internal fun logoOcrMask(original: Bitmap): Bitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888).also {
    var light = 0.0
    var samples = 0
    for (y in 0 until 32) for (x in 0 until 32) {
        val pixel = original.getPixel(x * (original.width - 1) / 31, y * (original.height - 1) / 31)
        if (Color.alpha(pixel) >= 128) {
            light += .2126 * Color.red(pixel) + .7152 * Color.green(pixel) + .0722 * Color.blue(pixel)
            samples++
        }
    }
    val canvas = Canvas(it)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(original, 0f, 0f, Paint().apply {
        // Preserve interior letter details. Flattening alpha alone merges opaque shadows and
        // outlines into blocks (especially distressed anime logos).
        if (samples > 0 && light / samples > 127) colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
    })
}
