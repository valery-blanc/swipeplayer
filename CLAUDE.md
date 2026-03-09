# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew test --tests "com.example.swipeplayer.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

## Project Overview

SwipePlayer is an Android video player (API 26+, target 36) with TikTok-style vertical swipe navigation between videos in the same directory. There is **no built-in file browser** — the app is launched via "Open with" from an external file manager.

The project is currently at the **initial template stage** (just a Hello World `MainActivity`). The full architecture described below is the **spec to implement**, not existing code.

## Target Architecture (MVVM + Hilt)

The app package should be `com.swipeplayer` (not `com.example.swipeplayer` as in the template).

```
app/src/main/java/com/swipeplayer/
├── SwipePlayerApp.kt          # @HiltAndroidApp Application class
├── di/AppModule.kt            # Hilt modules
├── data/
│   ├── VideoFile.kt           # Data class: uri, name, path, duration
│   ├── VideoRepository.kt     # Lists video files from a directory
│   └── PlaybackHistory.kt     # Navigation history stack
├── player/
│   ├── VideoPlayerManager.kt  # Creates/manages ExoPlayer instances (max 2)
│   ├── PlayerConfig.kt        # HW+ decoder config, buffer settings
│   └── AudioFocusManager.kt   # AudioFocus + headphone unplug handling
└── ui/
    ├── PlayerActivity.kt      # Single activity, handles the VIEW intent
    ├── PlayerViewModel.kt     # Main ViewModel with StateFlow
    ├── screen/PlayerScreen.kt # Root composable with VerticalPager
    ├── components/            # UI composables (see below)
    └── gesture/               # Gesture detection and routing
```

## Key Implementation Decisions

### ExoPlayer — HW+ Mode
Hardware-only decoding, no software fallback. Use `setEnableDecoderFallback(false)` and `EXTENSION_RENDERER_MODE_OFF`. On codec failure, show a toast and skip to the next video — do NOT fall back to software decoding.

### Navigation Algorithm
- State: `playlistVideos` (all files in dir), `history` (viewed videos), `currentIndex`
- Swipe up: advance in history or pick a random unseen video; reset pool when all seen
- Swipe down: go back in history (bounce visually if at start)

### Gesture Zones (screen width)
- Left 15%: vertical swipe = brightness control (`WindowManager.LayoutParams.screenBrightness`)
- Right 15%: vertical swipe = volume control (`AudioManager.STREAM_MUSIC`)
- Center 70%: vertical swipe = video navigation; tap = toggle controls; double-tap = ±10s seek; pinch = zoom

All gestures must be handled in **a single `pointerInput` modifier** routed by the X position of the first pointer to avoid gesture conflicts. Swipe-to-navigate requires ≥80dp movement + minimum velocity.

### Video Surface + Compose
Use `SurfaceView` wrapped in `AndroidView` for the video surface only. All overlay UI (controls, gestures) must be pure Compose on top.

### Memory Management
Maximum 2 simultaneous ExoPlayer instances: current (playing) + next (first frame decoded, paused). Release the previous immediately after a swipe. Call `player.setVideoSurfaceView(null)` before `player.release()`.

### VerticalPager Setup
Use `pageCount = { Int.MAX_VALUE }` with a large initial offset to allow bi-directional swiping. Map virtual page indices to the history/navigation algorithm in the ViewModel. Use `beyondBoundsPageCount = 1` to preload one adjacent page.

### Pinch-to-Zoom
Apply scale via `graphicsLayer { scaleX = ...; scaleY = ... }` on the video surface only — not on the controls overlay.

### Controls Auto-hide
Use `AnimatedVisibility` with `fadeIn`/`fadeOut` (200ms). Auto-hide after 4 seconds via `LaunchedEffect` with a cancellable coroutine timer.

## Intent Handling

The `PlayerActivity` must declare an intent filter for `android.intent.action.VIEW` with `video/*` MIME type (both `file://` and `content://` schemes). Use `configChanges="orientation|screenSize|screenLayout|keyboardHidden"` to avoid activity recreation on rotation.

Steps on intent receipt:
1. Extract the video URI
2. List all video files in the same parent directory (`.mp4`, `.mkv`, `.avi`, `.mov`, `.wmv`, `.flv`, `.webm`, `.m4v`, `.3gp`, `.ts`, `.mpg`, `.mpeg`)
3. Sort by filename (case-insensitive natural order)
4. For `content://` URIs without directory access: play only the single file and disable swipe

## Required Permissions
- `READ_MEDIA_VIDEO` (API 33+) / `READ_EXTERNAL_STORAGE` (API < 33, `maxSdkVersion="32"`)
- `WAKE_LOCK`

## Dependencies to Add

The template `libs.versions.toml` only has basic Compose dependencies. Add to `app/build.gradle.kts`:
- `androidx.media3:media3-exoplayer`, `media3-ui`, `media3-common`
- `androidx.compose.foundation:foundation` (for `VerticalPager`)
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `com.google.dagger:hilt-android` + `hilt-android-compiler` (kapt/ksp)

## UI Style
- Always fullscreen immersive sticky mode (hide system bars)
- Progress bar color: Netflix red `#E50914`
- All controls on semi-transparent black overlay `#80000000`
- Timecodes in monospace, format `MM:SS` or `HH:MM:SS`
- Speed options: 0.25x, 0.5x, 0.75x, 1x, 1.25x, 1.5x, 1.75x, 2x, 3x, 4x
- Display modes cycle: Adapt (fit) → Fill (crop) → Stretch → 100% (native)
- Orientation cycle: Auto → Landscape → Portrait


## Lifecycle & Interruptions

Audio focus must be requested at playback start via `AudioFocusManager`:
- `AUDIOFOCUS_LOSS` → pause
- `AUDIOFOCUS_LOSS_TRANSIENT` → pause, resume on `AUDIOFOCUS_GAIN`
- `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` → reduce volume to 30%
- Headphone unplug (`ACTION_AUDIO_BECOMING_NOISY`) → pause immediately
- App backgrounded → pause; foregrounded → resume if was playing
- Screen off → pause and partially release resources
- Incoming phone call → pause automatically

## Edge Cases

| Situation | Behavior |
|-----------|----------|
| Single video in directory | Disable swipe (elastic bounce, no change) |
| Codec not supported (HW+ failure) | Toast "Codec non supporté", auto-skip after 2s |
| File deleted between listing and play | Toast error, remove from playlist, skip to next |
| `content://` URI without directory access | Play single file only, disable swipe, show toast |
| Video > 4 hours | No limitation, use HH:MM:SS format |
| All videos seen (random pool empty) | Reset pool (exclude current video), continue random |

## Animation Timings

- Controls fade in/out: 200ms (FastOutSlowIn)
- Swipe transition between videos: 300ms (DecelerateInterpolator)
- Double-tap feedback (±10s): 500ms (100ms fade in + 400ms fade out)
- Brightness/volume bar update: 100ms (Linear)
- Controls auto-hide delay: 4 seconds of inactivity

## Subtitle Support

Detect and support embedded subtitle tracks (SRT, ASS/SSA) and external
`.srt`/`.ass`/`.ssa` files in the same directory as the video. Subtitle
track selection is available in the settings bottom sheet.

## Performance Targets

- Intent → first video frame: < 500ms
- Swipe transition (last frame → first frame of next): < 300ms
- Seek latency (tap on progress bar): < 200ms
- Zero frame drops during swipe animation
- Memory: < 150MB normal playback, < 250MB during swipe (2 players)
