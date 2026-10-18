package io.github.ninock.pocketcode

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ninock.pocketcode.runtime.AgentAttentionNotifier
import io.github.ninock.pocketcode.ui.MainViewModel
import io.github.ninock.pocketcode.ui.PocketDevApp
import io.github.ninock.pocketcode.ui.theme.PocketTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Back on a screen with nothing left to close leaves the app the way Home does. Finishing
        // the activity would clear MainViewModel, which owns running agent sessions and terminal
        // commands. Registered before setContent, so every Compose BackHandler takes precedence.
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        setContent {
            val vm = mainViewModel
            val state by vm.state.collectAsStateWithLifecycle()
            PocketTheme(themeMode = state.themeMode) {
                PocketDevApp(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AgentAttentionNotifier.onAppVisibilityChanged(this, visible = true)
    }

    override fun onStop() {
        AgentAttentionNotifier.onAppVisibilityChanged(this, visible = false)
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            mainViewModel.releaseMemoryCaches()
        }
    }
}
