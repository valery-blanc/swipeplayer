package com.swipeplayer.ui.components

import android.view.SurfaceView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.swipeplayer.ui.DisplayMode

/**
 * Renders video from [player] onto a [SurfaceView] wrapped in AndroidView.
 *
 * [zoomScale] is applied via graphicsLayer on the surface only — controls
 * overlaid on top are unaffected by the zoom.
 *
 * [displayMode] controls how the video is scaled relative to the container:
 *   ADAPT      — fit inside with letterbox/pillarbox (black bars, no cropping)
 *   FILL       — fill the container, preserving ratio, cropping excess
 *   STRETCH    — fill the container ignoring the video's aspect ratio
 *   NATIVE_100 — render at native pixel size (1 video pixel = 1 screen pixel)
 *
 * The video size is tracked locally via [Player.Listener.onVideoSizeChanged],
 * so no extra state needs to be stored in the ViewModel.
 *
 * Safe when [player] is null: the SurfaceView is still created but nothing
 * is attached to it (black surface, no crash).
 */
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    zoomScale: Float,
    displayMode: DisplayMode,
    modifier: Modifier = Modifier,
) {
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // Listen to video size changes from the player.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
        player?.addListener(listener)
        // Capture immediately if the player already has a video size.
        player?.videoSize?.let { vs ->
            videoWidth = vs.width
            videoHeight = vs.height
        }
        onDispose { player?.removeListener(listener) }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { scaleX = zoomScale; scaleY = zoomScale }
            .clipToBounds(),
    ) {
        val containerW = maxWidth.value   // dp
        val containerH = maxHeight.value  // dp
        val containerRatio = if (containerH > 0f) containerW / containerH else 1f
        val videoRatio = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight
        } else {
            containerRatio  // fallback: treat as same ratio until size is known
        }

        val surfaceModifier: Modifier = when (displayMode) {
            DisplayMode.ADAPT -> {
                // Fit entirely inside the container, preserving ratio (letterbox / pillarbox).
                val (w, h) = if (videoRatio >= containerRatio) {
                    containerW to (containerW / videoRatio)
                } else {
                    (containerH * videoRatio) to containerH
                }
                Modifier.requiredSize(w.dp, h.dp)
            }

            DisplayMode.FILL -> {
                // Fill the container, preserving ratio, cropping the excess edges.
                val (w, h) = if (videoRatio >= containerRatio) {
                    (containerH * videoRatio) to containerH
                } else {
                    containerW to (containerW / videoRatio)
                }
                Modifier.requiredSize(w.dp, h.dp)
            }

            DisplayMode.STRETCH -> {
                // Fill the container ignoring the video's aspect ratio.
                Modifier.requiredSize(containerW.dp, containerH.dp)
            }

            DisplayMode.NATIVE_100 -> {
                // 1 video pixel = 1 screen pixel. May be clipped on smaller screens.
                if (videoWidth > 0 && videoHeight > 0) {
                    with(LocalDensity.current) {
                        Modifier.requiredSize(videoWidth.toDp(), videoHeight.toDp())
                    }
                } else {
                    Modifier.requiredSize(containerW.dp, containerH.dp)
                }
            }
        }

        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    player?.setVideoSurfaceView(sv)
                }
            },
            update = { sv ->
                player?.setVideoSurfaceView(sv)
            },
            modifier = surfaceModifier,
        )
    }
}
