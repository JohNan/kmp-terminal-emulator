package com.antigravity.agy.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.antigravity.agy.android.ui.theme.AgyTheme

/**
 * Main Activity for the Antigravity Android UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Uses MockAgyViewModel until the full ViewModel implementation is wired up
        val viewModel: IAgyViewModel = MockAgyViewModel()

        setContent {
            AgyTheme {
                AgyApp(viewModel = viewModel)
            }
        }
    }
}
