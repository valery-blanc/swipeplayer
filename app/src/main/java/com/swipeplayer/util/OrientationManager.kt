package com.swipeplayer.util

import android.app.Activity
import android.content.pm.ActivityInfo
import com.swipeplayer.ui.OrientationMode

/**
 * Applies [mode] to [activity]'s requested orientation.
 *
 * AUTO      -> SCREEN_ORIENTATION_SENSOR (follows device rotation)
 * LANDSCAPE -> SCREEN_ORIENTATION_SENSOR_LANDSCAPE (locks landscape,
 *              but flips between reverse-landscape if sensor allows)
 * PORTRAIT  -> SCREEN_ORIENTATION_SENSOR_PORTRAIT (locks portrait,
 *              flips between reverse-portrait if sensor allows)
 */
fun applyOrientation(activity: Activity, mode: OrientationMode) {
    activity.requestedOrientation = when (mode) {
        OrientationMode.AUTO      -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        OrientationMode.PORTRAIT  -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
}
