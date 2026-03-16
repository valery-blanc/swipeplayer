package com.swipeplayer.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

private const val TAG = "SwipePlayer"

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    @Inject
    lateinit var audioFocusManager: AudioFocusManager

    private var mediaSession: MediaSession? = null

    // URI waiting to be processed once permission is granted.
    private var pendingUri: Uri? = null

    // Runtime permission launcher (must be registered before onCreate returns).
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val uri = pendingUri ?: return@registerForActivityResult
            pendingUri = null
            Log.d(TAG, "Media permission ${if (granted) "granted" else "denied"}")
            viewModel.onIntentReceived(uri)  // proceed regardless (single file if denied)
        }

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

            // Keep screen on while a video is playing; allow sleep when paused.
            LaunchedEffect(uiState.isPlaying) {
                if (uiState.isPlaying) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
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

    override fun onResume() {
        super.onResume()
    }

    override fun onStart() {
        super.onStart()
        audioFocusManager.registerNoisyReceiver(this)
        // CRO-030: RECEIVER_NOT_EXPORTED required on Android 14+ to avoid SecurityException
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.registerReceiver(
                this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
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
        // CRO-005: onActivityDestroy() removed — cleanup handled in ViewModel.onCleared()
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
        val uri = intent?.data ?: run { finish(); return }
        Log.d(TAG, "handleIntent: uri=$uri scheme=${uri.scheme} authority=${uri.authority}")

        // Step 1: ensure basic media read permission is granted.
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (checkSelfPermission(mediaPermission) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "handleIntent: requesting $mediaPermission")
            pendingUri = uri
            permissionLauncher.launch(mediaPermission)
            return
        }

        // CR-011: MANAGE_EXTERNAL_STORAGE is NOT requested proactively.
        // VideoRepository uses a chain of fallback strategies (SAF → MediaStore →
        // File.listFiles()). If File.listFiles() fails due to missing permission,
        // the app falls back to single-file mode gracefully. The user can grant
        // MANAGE_EXTERNAL_STORAGE manually via app settings if they want SD card
        // directory listing to work.
        Log.d(TAG, "handleIntent: permissions OK, proceeding")
        viewModel.onIntentReceived(uri)
    }
}
