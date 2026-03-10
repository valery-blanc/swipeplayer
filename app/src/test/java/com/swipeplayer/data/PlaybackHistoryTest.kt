package com.swipeplayer.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Pure-JVM tests for PlaybackHistory.
 *
 * Uri is an Android stub whose constructor cannot be called in non-Robolectric
 * JVM tests. We use Mockito to create a distinct mock Uri per VideoFile.
 * PlaybackHistory uses IdentityHashMap internally so Uri.hashCode/equals
 * are never called; the mocks are only needed to satisfy the non-null field.
 */
class PlaybackHistoryTest {

    // Each call to mock() returns a unique Uri instance (object identity).
    private fun video(name: String) = VideoFile(
        uri = mock<Uri>(),
        name = name,
        path = "",
        duration = -1L,
    )

    private val v1 = video("v1.mp4")
    private val v2 = video("v2.mp4")
    private val v3 = video("v3.mp4")
    private val v4 = video("v4.mp4")
    private val v5 = video("v5.mp4")

    private val playlist = listOf(v1, v2, v3, v4, v5)
    private lateinit var history: PlaybackHistory

    @Before
    fun setUp() {
        history = PlaybackHistory()
        history.init(v1, playlist)
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial current is the start video`() {
        assertEquals("v1.mp4", history.current?.name)
    }

    @Test
    fun `initial canGoBack is false`() {
        assertFalse(history.canGoBack)
    }

    @Test
    fun `initial canGoForward is false`() {
        assertFalse(history.canGoForward)
    }

    // -------------------------------------------------------------------------
    // navigateForward
    // -------------------------------------------------------------------------

    @Test
    fun `navigateForward appends a new unseen video`() {
        val next = history.navigateForward()
        assertNotEquals("v1.mp4", next.name)
        assertTrue(playlist.map { it.name }.contains(next.name))
    }

    @Test
    fun `two consecutive forward navigations return different videos`() {
        val a = history.navigateForward()
        val b = history.navigateForward()
        assertNotEquals(a.name, b.name)
    }

    @Test
    fun `navigateForward advances currentIndex so canGoBack becomes true`() {
        history.navigateForward()
        assertTrue(history.canGoBack)
    }

    // -------------------------------------------------------------------------
    // navigateBack
    // -------------------------------------------------------------------------

    @Test
    fun `navigateBack returns null at start of history`() {
        assertNull(history.navigateBack())
    }

    @Test
    fun `navigateBack returns previous video after forward navigation`() {
        history.navigateForward()
        val back = history.navigateBack()
        assertEquals("v1.mp4", back?.name)
    }

    @Test
    fun `back then forward returns the same video`() {
        val forward1 = history.navigateForward()
        history.navigateBack()
        val forward2 = history.navigateForward()
        assertEquals(forward1.name, forward2.name)
    }

    @Test
    fun `canGoForward is true after navigateBack`() {
        history.navigateForward()
        history.navigateBack()
        assertTrue(history.canGoForward)
    }

    // -------------------------------------------------------------------------
    // Unseen pool and full-cycle reset
    // -------------------------------------------------------------------------

    @Test
    fun `all 5 videos visited exactly once before pool reset`() {
        val visited = mutableSetOf(history.current!!.name)
        repeat(4) { visited.add(history.navigateForward().name) }
        assertEquals(5, visited.size)
        assertEquals(playlist.map { it.name }.toSet(), visited)
    }

    @Test
    fun `pool resets after all videos are seen`() {
        repeat(4) { history.navigateForward() }
        // 6th call triggers pool reset; must not throw
        val sixth = history.navigateForward()
        assertTrue(playlist.map { it.name }.contains(sixth.name))
    }

    @Test
    fun `pool reset excludes current video`() {
        repeat(4) { history.navigateForward() }
        val currentName = history.current!!.name
        val afterReset = history.navigateForward()
        assertNotEquals(currentName, afterReset.name)
    }

    // -------------------------------------------------------------------------
    // peekNext
    // -------------------------------------------------------------------------

    @Test
    fun `peekNext returns a valid playlist video`() {
        val peek = history.peekNext()
        assertTrue(playlist.map { it.name }.contains(peek.name))
    }

    @Test
    fun `peekNext does not advance currentIndex`() {
        history.peekNext()
        assertFalse(history.canGoBack)
        assertEquals("v1.mp4", history.current?.name)
    }

    @Test
    fun `peekNext is stable across repeated calls`() {
        val first = history.peekNext()
        val second = history.peekNext()
        assertEquals(first.name, second.name)
    }

    @Test
    fun `navigateForward uses the peekNext video`() {
        val peek = history.peekNext()
        val next = history.navigateForward()
        assertEquals(peek.name, next.name)
    }

    @Test
    fun `peekNext is preserved across a back-forward cycle`() {
        // v1 -> X (forward), peek at Y, back to v1, forward to X, forward = Y
        history.navigateForward()          // v1 -> X
        val peek = history.peekNext()      // Y pre-selected from X
        history.navigateBack()             // X -> v1
        history.navigateForward()          // v1 -> X  (history entry, not new pick)
        val secondForward = history.navigateForward()  // X -> Y (uses stored peek)
        assertEquals(peek.name, secondForward.name)
    }

    // -------------------------------------------------------------------------
    // commitPeek
    // -------------------------------------------------------------------------

    @Test
    fun `commitPeek uses the same video as peekNext`() {
        val peek = history.peekNext()
        val committed = history.commitPeek()
        assertEquals(peek.name, committed.name)
    }

    @Test
    fun `commitPeek advances currentIndex`() {
        history.commitPeek()
        assertTrue(history.canGoBack)
    }

    @Test
    fun `commitPeek clears stored peek so next peek picks a fresh video`() {
        val peek1 = history.peekNext()
        history.commitPeek()
        if (playlist.size > 2) {
            val peek2 = history.peekNext()
            assertNotEquals(peek1.name, peek2.name)
        }
    }

    // -------------------------------------------------------------------------
    // Edge case: single-video playlist
    // -------------------------------------------------------------------------

    @Test
    fun `single-video playlist does not crash on navigateForward`() {
        history.init(v1, listOf(v1))
        val next = history.navigateForward()
        assertEquals("v1.mp4", next.name)
    }
}
