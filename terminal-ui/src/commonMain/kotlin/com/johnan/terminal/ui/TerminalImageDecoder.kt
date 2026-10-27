package com.johnan.terminal.ui

import androidx.compose.ui.graphics.ImageBitmap
import com.johnan.terminal.core.TerminalImage

internal expect fun createTerminalImageBitmap(image: TerminalImage): ImageBitmap?
