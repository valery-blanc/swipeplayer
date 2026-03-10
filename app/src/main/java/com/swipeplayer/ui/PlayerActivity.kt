package com.swipeplayer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swipeplayer.player.AudioFocusManager
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
            val uiState by viewModel.uiState.collectAsState()
            // Placeholder until PlayerScreen (TASK-017) is implemented.
            // Shows the video filename on a black background once the intent
            // is processed; proves the full pipeline works end-to-end.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val label = when {
                    uiState.isLoading -> "Chargement..."
                    uiState.currentVideo != null -> uiState.currentVideo!!.name
                    uiState.error != null -> "Erreur : ${uiState.error}"
                    else -> "SwipePlayer"
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
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
