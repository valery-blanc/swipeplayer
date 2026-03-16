package com.swipeplayer.ui.home

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.swipeplayer.ui.PlayerActivity
import com.swipeplayer.ui.home.screen.HomeScreen
import com.swipeplayer.ui.theme.SwipePlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableFullscreen()
        requestMediaPermission()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            // When a video is selected, launch PlayerActivity
            LaunchedEffect(uiState.videoToPlay) {
                val uri = uiState.videoToPlay ?: return@LaunchedEffect
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setClass(this@HomeActivity, PlayerActivity::class.java)
                }
                startActivity(intent)
                viewModel.onVideoPlayLaunched()
            }

            SwipePlayerTheme {
                HomeScreen(uiState = uiState, viewModel = viewModel)
            }
        }
    }

    private fun requestMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    private fun enableFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
