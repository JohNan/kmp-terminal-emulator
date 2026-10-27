package com.johnan.terminal.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.johnan.terminal.core.TerminalImage

internal actual fun createTerminalImageBitmap(image: TerminalImage): ImageBitmap? {
    if (image.widthPx <= 0 || image.heightPx <= 0) return null
    val pixelCount = image.widthPx * image.heightPx
    if (image.rgbaBytes.size < pixelCount * 4) return null

    val colors = IntArray(pixelCount)
    var byteIdx = 0
    for (i in 0 until pixelCount) {
        val r = image.rgbaBytes[byteIdx++].toInt() and 0xFF
        val g = image.rgbaBytes[byteIdx++].toInt() and 0xFF
        val b = image.rgbaBytes[byteIdx++].toInt() and 0xFF
        val a = image.rgbaBytes[byteIdx++].toInt() and 0xFF
        colors[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return Bitmap.createBitmap(colors, image.widthPx, image.heightPx, Bitmap.Config.ARGB_8888).asImageBitmap()
}
