package com.johnan.terminal.demomobile

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Main iOS view controller factory exporting Compose Multiplatform terminal UI for iOS / SwiftUI host applications.
 */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    MobileTerminalScreen()
}
