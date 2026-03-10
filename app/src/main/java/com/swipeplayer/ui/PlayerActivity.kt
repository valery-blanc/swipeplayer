package com.swipeplayer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swipeplayer.player.AudioFocusManager
import com.swipeplayer.ui.screen.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    @Inject
    lateinit var audioFocusManager: AudioFocusManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullscreen()
        setContent {
            PlayerScreen(viewModel = viewModel)
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        audioFocusManager.registerNoisyReceiver(this)
        viewModel.onActivityStart()
    }

    override fun onStop() {
        super.onStop()
        audioFocusManager.unregisterNoisyReceiver(this)
        viewModel.onActivityStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onActivityDestroy()
    }

    // -------------------------------------------------------------------------

    private fun enableFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        viewModel.onIntentReceived(uri)
    }
}
