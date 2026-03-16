# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.


## Workflow Rules

### Task Tracking
For any task that involves more than 3 files or more than 3 steps:
1. BEFORE starting, create/update a checklist in `docs/tasks/TASKS.md`
2. Mark each sub-step with `[ ]` (todo), `[x]` (done), or `[!]` (blocked)
3. Update the checklist AFTER completing each sub-step
4. If the session is interrupted, the checklist is the source of truth 
   for resuming work

### Resuming Work
When starting a new session or after /clear, ALWAYS:
1. Read `docs/tasks/TASKS.md` to check current progress
2. Identify the first unchecked item
3. Resume from there — do NOT restart completed work


### Documentation Synchronization (OBLIGATOIRE)

**À chaque demande de modification, bug fix ou nouvelle feature — quelle que soit
la façon dont elle est formulée (message direct, fichier temp_*.txt, description
orale) — TOUJOURS :**

1. **Créer ou mettre à jour le fichier de bug** (`docs/bugs/BUG-XXX-*.md`)
   ou de feature (`docs/specs/FEAT-XXX-*.md`) correspondant.

2. **Mettre à jour `docs/specs/swipeplayer-specs.md`** pour refléter tout
   changement de comportement, toute règle révisée ou toute nouvelle fonctionnalité.
   Ne pas attendre qu'on le demande explicitement — c'est systématique.

3. **Mettre à jour `docs/tasks/TASKS.md`** — toujours, sans condition :
   ajouter l'entrée si elle n'existe pas, cocher `[x]` les étapes terminées.

Cette règle s'applique MÊME pour les petites modifications demandées directement
dans le chat (ex : "désactive la mise en veille", "change la couleur", etc.).
Si c'est trop petit pour un fichier BUG/FEAT dédié, au minimum mettre à jour
`swipeplayer-specs.md` si le comportement change.

### Règle de déploiement et confirmation (OBLIGATOIRE)

**Aucun commit ne doit être créé avant que l'utilisateur ait testé et confirmé.**

Ordre impératif pour tout bug fix ou feature :

```
[code] → [docs] → [./gradlew installDebug] → [demander test] → [attendre OK] → [commit]
```

- Le commit regroupe TOUJOURS : code source + fichiers de doc + TASKS.md
- Si l'utilisateur signale un problème après test → corriger, re-déployer,
  re-demander confirmation AVANT de committer
- **Si un crash est découvert lors du test** → créer `docs/bugs/BUG-XXX-*.md`
  (même si le crash a déjà été corrigé), mettre à jour `swipeplayer-specs.md`
  avec la règle à retenir, et référencer dans TASKS.md
- Aucune exception : même pour une modification d'une seule ligne

---

### Bug Fix Workflow
1. Documenter le bug dans `docs/bugs/BUG-XXX-short-name.md` (symptôme,
   reproduction, logcat, section spec impactée)
2. Analyser la cause racine AVANT d'écrire le fix (Plan Mode)
3. Implémenter le fix
4. Mettre à jour toute la documentation :
   - `docs/bugs/BUG-XXX-*.md` → statut `FIXED`, fix appliqué décrit
   - `docs/specs/swipeplayer-specs.md` (obligatoire)
   - `docs/tasks/TASKS.md` → cocher `[x]` toutes les étapes terminées (obligatoire)
5. **Déployer sur le téléphone** : `./gradlew installDebug`
6. **Demander à l'utilisateur de tester et attendre sa confirmation explicite**
   — NE PAS committer avant que l'utilisateur confirme que c'est OK
7. Une fois confirmé : committer TOUS les fichiers modifiés en un seul commit
   (code + docs + TASKS.md) : `"FIX BUG-XXX: description courte"`

### Feature Evolution Workflow
1. Écrire la spec dans `docs/specs/FEAT-XXX-short-name.md` (contexte,
   comportement, spec technique, impact sur l'existant)
2. Analyser l'impact sur le code existant (Plan Mode) : risques, conflits,
   lacunes de la spec
3. Décomposer en tâches dans `docs/tasks/TASKS.md`
4. Implémenter
5. Mettre à jour toute la documentation :
   - `docs/specs/FEAT-XXX-*.md` → statut `DONE`, implémentation décrite
   - `docs/specs/swipeplayer-specs.md` (obligatoire)
   - `docs/tasks/TASKS.md` → cocher `[x]` toutes les étapes terminées (obligatoire)
6. **Déployer sur le téléphone** : `./gradlew installDebug`
7. **Demander à l'utilisateur de tester et attendre sa confirmation explicite**
   — NE PAS committer avant que l'utilisateur confirme que c'est OK
8. Une fois confirmé : committer TOUS les fichiers modifiés en un seul commit
   (code + docs + TASKS.md) : `"FEAT-XXX: description courte"`
9. Mettre à jour CLAUDE.md si des règles d'architecture ont changé






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
- `peekNext()`: as soon as the current video starts playing, pre-select the next random video **without** advancing `currentIndex`. This enables ExoPlayer pre-loading before the user swipes. If user swipes down instead, `peekNext` is preserved for the next swipe-up.

File listing sort must use **natural sort** (case-insensitive, numeric-aware): `video2.mp4` < `video10.mp4`. Standard `String.compareTo()` gives wrong order for numbered files.

### Gesture Zones (screen width)
- Left 15%: vertical swipe = brightness control (`WindowManager.LayoutParams.screenBrightness`)
- Right 15%: vertical swipe = volume control (`AudioManager.STREAM_MUSIC`)
- Center 70%: vertical swipe = video navigation; tap = toggle controls; double-tap = ±10s seek; pinch = zoom

All gestures must be handled in **a single `pointerInput` modifier** routed by the X position of the first pointer to avoid gesture conflicts. Swipe-to-navigate requires ≥80dp movement + minimum velocity. An initially horizontal movement means seekbar interaction — cancel video-swipe detection. Swipe-to-navigate is disabled when zoom scale > 1x.

### Video Surface + Compose
Use `SurfaceView` wrapped in `AndroidView` for the video surface only. All overlay UI (controls, gestures) must be pure Compose on top.

### Memory Management
Maximum 2 simultaneous ExoPlayer instances: current (playing) + next (`peekNext`, first frame decoded, paused). Release the previous immediately after a swipe. Call `player.clearVideoSurfaceView(surfaceView)` (or `player.setVideoSurface(null)`) before `player.release()` to prevent leaks.

### VerticalPager Setup
Use **2 logical pages with silent reset** — not `Int.MAX_VALUE`. Pages: 0 = previous video, 1 = current video. After each completed swipe, call `pagerState.scrollToPage(1)` without animation and update the ViewModel. To block swipe-down at the start of history, intercept the scroll gesture and prevent navigation to page 0 when `currentIndex == 0`.

### Pinch-to-Zoom
Apply scale via `graphicsLayer { scaleX = ...; scaleY = ... }` on the video surface only — not on the controls overlay. **When scale > 1x, disable the vertical swipe-to-navigate gesture.** The user must first pinch back to 1x (or double-tap to reset zoom) before video navigation is re-enabled.

### MediaSession (mandatory)
Implement `MediaSession` (`media3-session`) to expose playback controls in the Android system notification. Required since Android 12+ for foreground media apps; without it the app may be killed in the background by the OS.

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
- `androidx.media3:media3-exoplayer`, `media3-common`, `media3-session` — **not** `media3-ui` (UI is pure Compose)
- `androidx.compose.foundation:foundation` (for `VerticalPager`)
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `com.google.dagger:hilt-android` + `ksp("hilt-android-compiler")` — use **KSP**, not kapt (kapt is deprecated for Kotlin 2.x)
- `androidx.documentfile:documentfile` (for SAF / `content://` parent directory access)

Add the KSP plugin to `build.gradle.kts` (root) and `app/build.gradle.kts`.

## UI Style
- Always fullscreen immersive sticky mode (hide system bars)
- Progress bar color: Netflix red `#E50914`
- All controls on semi-transparent black overlay `#80000000`
- Timecodes in monospace, format `MM:SS` or `HH:MM:SS`
- Speed options: 0.25x, 0.33x, 0.5x, 0.75x, 1x, 1.5x, 2x, 3x, 4x (FEAT-007)
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


