package com.swipeplayer.data

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
            val next = peekNextVideo ?: pickRandom()
            history.add(next)
            currentIndex = history.lastIndex
            peekNextVideo = null
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

        val next = pickRandom()
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
        val next = peekNextVideo ?: pickRandom()
        history.add(next)
        currentIndex = history.lastIndex
        peekNextVideo = null
        return next
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Picks a random video from the unseen pool (playlist minus history).
     * When all videos have been seen, resets the pool to the full playlist
     * minus the video currently playing, then picks again.
     *
     * Uses reference identity (IdentityHashMap) rather than VideoFile.equals so
     * that this method never calls into Android's Uri.hashCode/equals — which
     * makes it safely testable in pure JVM unit tests. This is semantically
     * correct because every VideoFile stored in [history] is a reference taken
     * directly from [playlist]; no copies are ever created.
     */
    private fun pickRandom(): VideoFile {
        val seen: MutableSet<VideoFile> = Collections.newSetFromMap(IdentityHashMap())
        seen.addAll(history)
        var candidates = playlist.filter { it !in seen }

        if (candidates.isEmpty()) {
            // Full cycle complete: reset pool, exclude only the current video
            val currentVideo = history.getOrNull(currentIndex)
            candidates = playlist.filter { it !== currentVideo }
        }

        // If the playlist has exactly 1 video, return it (swipe is disabled
        // upstream, but we must not crash here)
        return candidates.randomOrNull() ?: playlist.first()
    }
}
