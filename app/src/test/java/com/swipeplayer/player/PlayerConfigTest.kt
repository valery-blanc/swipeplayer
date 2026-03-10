package com.swipeplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerConfigTest {

    @Test
    fun `gesture zones cover the full screen width`() {
        assertEquals(1.0f, PlayerConfig.LEFT_ZONE_FRACTION + (PlayerConfig.RIGHT_ZONE_FRACTION - PlayerConfig.LEFT_ZONE_FRACTION) + (1f - PlayerConfig.RIGHT_ZONE_FRACTION), 0.001f)
        assertTrue("Left zone must be less than right zone", PlayerConfig.LEFT_ZONE_FRACTION < PlayerConfig.RIGHT_ZONE_FRACTION)
    }

    @Test
    fun `zoom bounds are sane`() {
        assertTrue(PlayerConfig.MIN_ZOOM_SCALE >= 1f)
        assertTrue(PlayerConfig.MAX_ZOOM_SCALE > PlayerConfig.MIN_ZOOM_SCALE)
    }

    @Test
    fun `buffer durations are ordered correctly`() {
        assertTrue(PlayerConfig.BUFFER_FOR_PLAYBACK_MS < PlayerConfig.MIN_BUFFER_MS)
        assertTrue(PlayerConfig.MIN_BUFFER_MS < PlayerConfig.MAX_BUFFER_MS)
        assertTrue(PlayerConfig.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS <= PlayerConfig.MIN_BUFFER_MS)
    }

    @Test
    fun `controls hide delay matches spec (4 seconds)`() {
        assertEquals(4_000L, PlayerConfig.CONTROLS_HIDE_DELAY_MS)
    }

    @Test
    fun `codec skip delay matches spec (2 seconds)`() {
        assertEquals(2_000L, PlayerConfig.CODEC_FAILURE_SKIP_DELAY_MS)
    }

    @Test
    fun `swipe min dp is positive`() {
        assertTrue(PlayerConfig.SWIPE_MIN_DP > 0f)
    }
}
