package com.swipeplayer.ui.components

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer

/**
 * Renders video from [player] onto a [SurfaceView] wrapped in AndroidView.
 *
 * [zoomScale] is applied via graphicsLayer on the surface only — controls
 * overlaid on top are unaffected by the zoom.
 *
 * Safe when [player] is null: the SurfaceView is still created but nothing
 * is attached to it (black surface, no crash).
 */
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    zoomScale: Float,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            SurfaceView(ctx).also { sv ->
                player?.setVideoSurfaceView(sv)
            }
        },
        update = { sv ->
            // Re-attach whenever the player reference changes (e.g. after a swap).
            player?.setVideoSurfaceView(sv)
        },
        modifier = modifier.graphicsLayer {
            scaleX = zoomScale
            scaleY = zoomScale
        },
    )
}
