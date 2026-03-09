package com.swipeplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFileTest {

    @Test
    fun `VIDEO_EXTENSIONS contains all 12 required formats`() {
        val required = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv",
            "webm", "m4v", "3gp", "ts", "mpg", "mpeg",
        )
        assertEquals("Expected exactly 12 extensions", 12, VIDEO_EXTENSIONS.size)
        assertTrue(
            "Missing: ${required - VIDEO_EXTENSIONS}",
            VIDEO_EXTENSIONS.containsAll(required),
        )
    }

    @Test
    fun `VIDEO_EXTENSIONS are all lowercase`() {
        VIDEO_EXTENSIONS.forEach { ext ->
            assertEquals("Extension '$ext' must be lowercase", ext, ext.lowercase())
        }
    }
}
