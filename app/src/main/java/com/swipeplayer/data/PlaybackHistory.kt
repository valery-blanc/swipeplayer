package com.swipeplayer.data

import com.swipeplayer.ui.PlaybackOrder
import java.util.Collections
import java.util.IdentityHashMap



/**
 * Manages the navigation history and random-pick logic for the video playlist.
 *
 * State:
 *   history      - ordered list of videos the user has visited
 *   currentIndex - position inside history that is currently playing
 *   peekNextVideo - pre-selected next video (computed eagerly when a video
 *                   starts playing, so ExoPlayer can begin buffering before
 *                   the user actually swipes)
 *
 * Thread-safety: not thread-safe; all calls must come from the same thread
 * (the ViewModel's coroutine scope on the main dispatcher).
 */
class PlaybackHistory {

    private val history = mutableListOf<VideoFile>()
    private var currentIndex = 0
    private var peekNextVideo: VideoFile? = null
    private var playlist: List<VideoFile> = emptyList()

    /**
     * CR-019: persistent unseen-video set maintained incrementally.
     * Avoids rebuilding from history on every pickRandom() call.
     * Uses identity equality (like history) for safe JVM-only unit testing.
     */
    private val unseenSet: MutableSet<VideoFile> =
        Collections.newSetFromMap(IdentityHashMap())

    /** Order used to pick the next video. Update from ViewModel when user changes the setting. */
    var playbackOrder: PlaybackOrder = PlaybackOrder.RANDOM

    // -------------------------------------------------------------------------
    // Observable state
    // -------------------------------------------------------------------------

    val current: VideoFile? get() = history.getOrNull(currentIndex)
    val canGoBack: Boolean get() = currentIndex > 0
    val canGoForward: Boolean get() = currentIndex < history.lastIndex

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Initialises history with [startVideo] at index 0 and stores [fullPlaylist]
     * for future random picks. Must be called before any navigation.
     */
    fun init(startVideo: VideoFile, fullPlaylist: List<VideoFile>) {
        history.clear()
        history.add(startVideo)
        currentIndex = 0
        peekNextVideo = null
        playlist = fullPlaylist
        // CR-019: rebuild unseenSet from the full playlist, excluding startVideo
        unseenSet.clear()
        unseenSet.addAll(fullPlaylist)
        unseenSet.remove(startVideo)
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    /**
     * Moves forward:
     *   - If there is a future entry in history (user went back then forward),
     *     advance currentIndex to that entry.
     *   - Otherwise append a new random unseen video (using peekNextVideo if
     *     already computed, or picking a new one).
     *
     * Returns the video that should now play.
     * Throws [IllegalStateException] if called before [init].
     */
    fun navigateForward(): VideoFile {
        check(history.isNotEmpty()) { "PlaybackHistory not initialised" }

        return if (canGoForward) {
            // Re-visiting a previously seen entry
            currentIndex++
            history[currentIndex]
        } else {
            // Append a new video using the pre-selected peek (or pick now)
            val next = peekNextVideo ?: pickNext()
            history.add(next)
            currentIndex = history.lastIndex
            peekNextVideo = null
            // CR-019: mark the newly added video as seen
            unseenSet.remove(next)
            next
        }
    }

    /**
     * Moves back one step in history.
     * Returns the video to play, or null if already at the beginning
     * (caller should show a visual bounce instead of navigating).
     */
    fun navigateBack(): VideoFile? {
        if (!canGoBack) return null
        currentIndex--
        return history[currentIndex]
    }

    /**
     * CRO-028: Returns the video before the current one in history WITHOUT
     * modifying [currentIndex]. Returns null if already at the beginning.
     *
     * Used by [PlayerViewModel.onSwipeDown] to peek at the "previous-of-previous"
     * video for page 0 of the VerticalPager, without the fragile
     * navigateBack()+navigateForward() pattern.
     */
    fun peekBack(): VideoFile? {
        if (!canGoBack) return null
        return history.getOrNull(currentIndex - 1)
    }

    // -------------------------------------------------------------------------
    // Peek / pre-selection
    // -------------------------------------------------------------------------

    /**
     * Pre-selects the next random video WITHOUT advancing currentIndex.
     * Stores it in [peekNextVideo] so [navigateForward] can use it directly,
     * enabling ExoPlayer to start buffering before the swipe happens.
     *
     * Safe to call multiple times; re-uses the same peek until consumed.
     * No-op if we are not at the end of history (canGoForward is true).
     *
     * Returns the pre-selected video.
     */
    fun peekNext(): VideoFile {
        // If there is already a forward entry, peek at it without committing
        if (canGoForward) return history[currentIndex + 1]

        val existing = peekNextVideo
        if (existing != null) return existing

        val next = pickNext()
        peekNextVideo = next
        return next
    }

    /**
     * Commits the current peek as the next history entry and advances
     * currentIndex. Equivalent to [navigateForward] but guaranteed to use
     * the already-computed peek (avoids a second random pick).
     *
     * Returns the committed video.
     */
    fun commitPeek(): VideoFile {
        val next = peekNextVideo ?: pickNext()
        history.add(next)
        currentIndex = history.lastIndex
        peekNextVideo = null
        return next
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Picks a random video from [unseenSet] (maintained incrementally).
     * When all videos have been seen, resets the pool to the full playlist
     * minus the video currently playing, then picks again.
     *
     * Uses reference identity (IdentityHashMap) rather than VideoFile.equals so
     * that this method never calls into Android's Uri.hashCode/equals — which
     * makes it safely testable in pure JVM unit tests.
     *
     * CR-019: no longer rebuilds the unseen set from scratch on every call.
     */
    private fun pickNext(): VideoFile =
        if (playbackOrder == PlaybackOrder.ALPHABETICAL) pickSequential() else pickRandom()

    private fun pickSequential(): VideoFile {
        if (playlist.isEmpty()) return history[currentIndex]
        val currentVideo = history.getOrNull(currentIndex) ?: return playlist.first()
        // Find the index of the current video by identity
        val idx = playlist.indexOfFirst { it === currentVideo }
            .takeIf { it >= 0 }
            ?: playlist.indexOfFirst { it.uri == currentVideo.uri }
                .takeIf { it >= 0 }
            ?: 0
        return playlist[(idx + 1) % playlist.size]
    }

    private fun pickRandom(): VideoFile {
        if (unseenSet.isEmpty()) {
            // Full cycle complete: reset pool, exclude only the current video
            val currentVideo = history.getOrNull(currentIndex)
            unseenSet.addAll(playlist)
            if (currentVideo != null) unseenSet.remove(currentVideo)
        }

        // If the playlist has exactly 1 video, return it (swipe is disabled
        // upstream, but we must not crash here)
        return unseenSet.randomOrNull() ?: playlist.first()
    }
}
