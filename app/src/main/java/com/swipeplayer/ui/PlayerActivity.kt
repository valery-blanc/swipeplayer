package com.swipeplayer.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.swipeplayer.player.AudioFocusManager
import com.swipeplayer.ui.screen.PlayerScreen
import com.swipeplayer.util.applyOrientation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    @Inject
    lateinit var audioFocusManager: AudioFocusManager

    private var mediaSession: MediaSession? = null

    // Pauses playback when the screen turns off
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                viewModel.onActivityStop()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullscreen()

        // MediaSession: create/update whenever the active ExoPlayer changes
        lifecycleScope.launch {
            viewModel.currentPlayerState.collect { player ->
                if (player == null) return@collect
                val session = mediaSession
                if (session == null) {
                    mediaSession = MediaSession.Builder(this@PlayerActivity, player).build()
                } else {
                    session.player = player
                }
            }
        }

        // Toast events from ViewModel
        lifecycleScope.launch {
            viewModel.toastEvents.collect { message ->
                Toast.makeText(this@PlayerActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            // Apply screen orientation whenever the mode changes
            LaunchedEffect(uiState.orientationMode) {
                applyOrientation(this@PlayerActivity, uiState.orientationMode)
            }

            // Apply in-app brightness via WindowManager
            LaunchedEffect(uiState.brightness) {
                if (uiState.brightness >= 0f) {
                    val params = window.attributes
                    params.screenBrightness = uiState.brightness
                    window.attributes = params
                }
            }

            PlayerScreen(viewModel = viewModel, onBack = { finish() })
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
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        viewModel.onActivityStart()
    }

    override fun onStop() {
        super.onStop()
        audioFocusManager.unregisterNoisyReceiver(this)
        try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
        viewModel.onActivityStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
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
