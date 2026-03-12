package com.swipeplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android audio focus and headphone-unplug (NOISY) events.
 *
 * Audio focus reactions:
 *   AUDIOFOCUS_LOSS             -> onPause()
 *   AUDIOFOCUS_LOSS_TRANSIENT   -> onPause(), flag to resume on GAIN
 *   AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck() (reduce to 30 %)
 *   AUDIOFOCUS_GAIN             -> onResume() (if was paused) or onUnduck()
 *   ACTION_AUDIO_BECOMING_NOISY -> onPause() (headphones unplugged)
 */
@Singleton
class AudioFocusManager @Inject constructor(
    private val audioManager: AudioManager,
) {

    interface Listener {
        fun onPause()
        fun onResume()
        fun onDuck()
        fun onUnduck()
    }

    var listener: Listener? = null

    private var shouldResumeOnGain = false
    private var isDucking = false

    // -------------------------------------------------------------------------
    // Audio focus
    // -------------------------------------------------------------------------

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                shouldResumeOnGain = false
                isDucking = false
                listener?.onPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                shouldResumeOnGain = true
                listener?.onPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDucking = true
                listener?.onDuck()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                when {
                    isDucking -> {
                        isDucking = false
                        listener?.onUnduck()
                    }
                    shouldResumeOnGain -> {
                        shouldResumeOnGain = false
                        listener?.onResume()
                    }
                }
            }
        }
    }

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        .setOnAudioFocusChangeListener(focusChangeListener)
        .build()

    /**
     * Requests audio focus. Returns true if focus was granted immediately.
     * Call at the start of playback.
     */
    fun requestFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    /**
     * Releases audio focus. Call on pause, stop, or destroy.
     */
    fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        shouldResumeOnGain = false
        isDucking = false
    }

    // -------------------------------------------------------------------------
    // Headphone unplug (ACTION_AUDIO_BECOMING_NOISY)
    // -------------------------------------------------------------------------

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                listener?.onPause()
            }
        }
    }

    private var noisyRegistered = false

    /**
     * Registers the NOISY broadcast receiver. Call in Activity.onStart().
     * CRO-017: use RECEIVER_NOT_EXPORTED on Android 14+ to avoid SecurityException.
     */
    fun registerNoisyReceiver(context: Context) {
        if (!noisyRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(
                    context, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            } else {
                context.registerReceiver(noisyReceiver, filter)
            }
            noisyRegistered = true
        }
    }

    /**
     * Unregisters the NOISY broadcast receiver. Call in Activity.onStop().
     */
    fun unregisterNoisyReceiver(context: Context) {
        if (noisyRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (_: IllegalArgumentException) {
                // Receiver was not registered; safe to ignore
            }
            noisyRegistered = false
        }
    }
}
