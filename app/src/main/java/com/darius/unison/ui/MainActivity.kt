package com.darius.unison.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.darius.unison.ui.theme.UnisonTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            UnisonTheme { UnisonApp(viewModel) }
        }
        consumeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(value: Intent) {
        viewModel.handleIntent(value)
        // A configuration change recreates the Activity with the same launch Intent. Clear the
        // one-shot payload after dispatch so shared tracks are not imported or queued twice.
        value.action = null
        value.data = null
        value.removeExtra(Intent.EXTRA_STREAM)
        value.clipData = null
    }
}
