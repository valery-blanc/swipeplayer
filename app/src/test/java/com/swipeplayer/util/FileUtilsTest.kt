package com.swipeplayer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUtilsTest {

    private fun cmp(a: String, b: String) = naturalCompare(a, b)

    // ── Numeric ordering ──────────────────────────────────────────────────────

    @Test
    fun `video2 sorts before video10`() {
        assertTrue(cmp("video2.mp4", "video10.mp4") < 0)
    }

    @Test
    fun `Episode 9 sorts before Episode 10`() {
        assertTrue(cmp("Episode 9.mkv", "Episode 10.mkv") < 0)
    }

    @Test
    fun `numeric range 1 to 20 sorted correctly`() {
        val names = (1..20).map { "clip$it.mp4" }.shuffled()
        val sorted = names.sortedWith { a, b -> naturalCompare(a, b) }
        val expected = (1..20).map { "clip$it.mp4" }
        assertEquals(expected, sorted)
    }

    @Test
    fun `large numeric gap handled correctly`() {
        assertTrue(cmp("file9.mp4", "file100.mp4") < 0)
        assertTrue(cmp("file100.mp4", "file1000.mp4") < 0)
    }

    // ── Case insensitivity ────────────────────────────────────────────────────

    @Test
    fun `comparison is case-insensitive`() {
        assertEquals(0, cmp("Movie.mp4", "movie.mp4"))
        assertEquals(0, cmp("VIDEO.MP4", "video.mp4"))
    }

    @Test
    fun `uppercase and lowercase numbers treated equally`() {
        assertTrue(cmp("Episode2.mkv", "EPISODE10.mkv") < 0)
    }

    // ── Lexicographic fallback ────────────────────────────────────────────────

    @Test
    fun `pure text compared lexicographically`() {
        assertTrue(cmp("alpha.mp4", "beta.mp4") < 0)
        assertTrue(cmp("beta.mp4", "alpha.mp4") > 0)
    }

    @Test
    fun `equal names return zero`() {
        assertEquals(0, cmp("same.mp4", "same.mp4"))
    }

    // ── Mixed tokens ──────────────────────────────────────────────────────────

    @Test
    fun `season and episode ordering`() {
        val files = listOf("S01E10", "S01E2", "S02E1", "S01E1").shuffled()
        val sorted = files.sortedWith { a, b -> naturalCompare(a, b) }
        assertEquals(listOf("S01E1", "S01E2", "S01E10", "S02E1"), sorted)
    }

    @Test
    fun `leading zeros treated numerically`() {
        // "007" parsed as 7L, so same as "7"
        assertEquals(0, cmp("007.mp4", "7.mp4"))
        assertTrue(cmp("007.mp4", "8.mp4") < 0)
    }
}
