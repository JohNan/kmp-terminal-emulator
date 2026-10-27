package com.johnan.terminal.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.johnan.terminal.core.TerminalImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

internal actual fun createTerminalImageBitmap(image: TerminalImage): ImageBitmap? {
    if (image.widthPx <= 0 || image.heightPx <= 0) return null
    if (image.rgbaBytes.size < image.widthPx * image.heightPx * 4) return null

    val info = ImageInfo(image.widthPx, image.heightPx, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val bitmap = Bitmap()
    bitmap.allocPixels(info)
    bitmap.installPixels(info, image.rgbaBytes, image.widthPx * 4)
    return bitmap.asComposeImageBitmap()
}
