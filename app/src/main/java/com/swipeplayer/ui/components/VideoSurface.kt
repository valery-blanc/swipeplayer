package com.swipeplayer.ui.components

import android.view.TextureView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
 * Renders video from [player] onto a [TextureView] wrapped in AndroidView.
 *
 * TextureView is used instead of SurfaceView because it renders into the app's
 * OpenGL texture pipeline, which means graphicsLayer transforms (zoom) and
 * View transforms (mirror via scaleX) both apply correctly.
 * SurfaceView renders to a separate SurfaceFlinger buffer that ignores all
 * View/RenderNode transforms — making mirror impossible without OpenGL shaders.
 *
 * [zoomScale] is applied via graphicsLayer on the container — controls
 * overlaid on top are unaffected by the zoom.
 *
 * [isMirrored] flips the video horizontally via TextureView.scaleX = -1f.
 *
 * [displayMode] controls how the video is scaled relative to the container.
 *
 * The video size is tracked locally via [Player.Listener.onVideoSizeChanged],
 * so no extra state needs to be stored in the ViewModel.
 *
 * Safe when [player] is null: the TextureView is still created but nothing
 * is attached to it (black surface, no crash).
 */
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    // CRO-004: lambda avoids recomposition on every pinch frame
    zoomScale: () -> Float,
    displayMode: DisplayMode,
    isMirrored: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // We keep the last known non-zero dimensions across player changes so there
    // is no flash to full-screen (which looks like STRETCH) while the new
    // player reports its video size for the first time.
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // Listen to video size changes from the player.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }
        }
        player?.addListener(listener)
        // Capture immediately if the player already has a video size.
        player?.videoSize?.let { vs ->
            if (vs.width > 0 && vs.height > 0) {
                videoWidth = vs.width
                videoHeight = vs.height
            }
        }
        onDispose { player?.removeListener(listener) }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // CRO-004: call lambda in graphicsLayer scope to skip recomposition
            .graphicsLayer {
                val s = zoomScale()
                scaleX = s
                scaleY = s
            }
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

        // Helper: compute size that FITS the given ratio inside the container (ADAPT logic).
        fun fitSize(ratio: Float): Pair<Float, Float> =
            if (ratio >= containerRatio) containerW to (containerW / ratio)
            else (containerH * ratio) to containerH

        val surfaceModifier: Modifier = when (displayMode) {
            DisplayMode.ADAPT -> {
                val (w, h) = fitSize(videoRatio)
                Modifier.requiredSize(w.dp, h.dp)
            }

            DisplayMode.FILL -> {
                // Fill the container, preserving ratio, cropping excess edges.
                val (w, h) = if (videoRatio >= containerRatio) {
                    (containerH * videoRatio) to containerH
                } else {
                    containerW to (containerW / videoRatio)
                }
                Modifier.requiredSize(w.dp, h.dp)
            }

            DisplayMode.STRETCH -> {
                Modifier.requiredSize(containerW.dp, containerH.dp)
            }

            DisplayMode.NATIVE_100 -> {
                if (videoWidth > 0 && videoHeight > 0) {
                    with(LocalDensity.current) {
                        Modifier.requiredSize(videoWidth.toDp(), videoHeight.toDp())
                    }
                } else {
                    Modifier.requiredSize(containerW.dp, containerH.dp)
                }
            }

            // Forced aspect ratios: deform the video to fill the given ratio inside the container.
            DisplayMode.RATIO_1_1 -> {
                val side = minOf(containerW, containerH)
                Modifier.requiredSize(side.dp, side.dp)
            }

            DisplayMode.RATIO_3_4 -> {
                val (w, h) = fitSize(3f / 4f)
                Modifier.requiredSize(w.dp, h.dp)
            }

            DisplayMode.RATIO_16_9 -> {
                val (w, h) = fitSize(16f / 9f)
                Modifier.requiredSize(w.dp, h.dp)
            }
        }

        // CR-014: track the previously attached player to avoid calling
        // setVideoTextureView on every recomposition (would cause a flash).
        var attachedPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    // FEAT-017: TextureView.scaleX = -1f mirrors the video horizontally.
                    // Unlike SurfaceView, TextureView renders through the app's RenderNode
                    // so View transforms apply correctly to the actual video content.
                    tv.scaleX = if (isMirrored) -1f else 1f
                    player?.setVideoTextureView(tv)
                    attachedPlayer = player
                }
            },
            update = { tv ->
                if (player !== attachedPlayer) {
                    // Detach from old player before attaching to new one
                    attachedPlayer?.clearVideoTextureView(tv)
                    player?.setVideoTextureView(tv)
                    attachedPlayer = player
                }
                tv.scaleX = if (isMirrored) -1f else 1f
            },
            modifier = surfaceModifier,
        )
    }
}
