package com.antigravity.agy.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.antigravity.agy.android.ui.theme.AgyTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Main Activity for the Antigravity Android UI.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: RealAgyViewModel = koinViewModel()
            AgyTheme {
                AgyApp(viewModel = viewModel)
            }
        }
    }
}
