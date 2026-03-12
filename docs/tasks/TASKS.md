# SwipePlayer — Task Tracking

> Source de vérité pour la progression de l'implémentation.
> En début de session : lire ce fichier et reprendre au premier item `[ ]`.
> Mettre à jour chaque item APRÈS completion.

Statuts : `[ ]` TODO · `[~]` EN COURS · `[x]` DONE · `[!]` BLOQUÉ

---

## M0 — Project Bootstrap

Fondations du projet : structure du package, dépendances, DI, manifest.
Rien d'autre ne peut commencer sans M0.

---

### TASK-001 — Migration du package `com.example.swipeplayer` → `com.swipeplayer`

- **Statut** : `[x]`
- **Dépendances** : aucune
- **Fichiers à modifier** :
  - `app/build.gradle.kts` → champ `applicationId`
  - `app/src/main/AndroidManifest.xml` → attribut `package`
  - `app/src/main/java/com/example/swipeplayer/MainActivity.kt` → déplacer vers `com/swipeplayer/MainActivity.kt`, corriger le package
  - `app/src/test/.../ExampleUnitTest.kt` → corriger le package
  - `app/src/androidTest/.../ExampleInstrumentedTest.kt` → corriger le package
- **Critère de validation** : `./gradlew assembleDebug` réussit, l'app se lance sur un device/émulateur

---

### TASK-002 — Ajout des dépendances et configuration KSP

- **Statut** : `[x]`
- **Dépendances** : TASK-001
- **Fichiers à modifier** :
  - `gradle/libs.versions.toml` → ajouter versions et aliases pour : `media3`, `hilt`, `ksp`, `documentfile`, `lifecycle-viewmodel-compose`, `compose-foundation`
  - `build.gradle.kts` (racine) → ajouter plugin KSP dans `plugins {}`
  - `app/build.gradle.kts` → ajouter plugin KSP, ajouter toutes les dépendances
- **Contenu exact des dépendances** :
  ```
  media3-exoplayer, media3-common, media3-session
  compose-foundation (VerticalPager)
  lifecycle-viewmodel-compose
  hilt-android + ksp(hilt-android-compiler)   ← KSP, pas kapt
  documentfile
  ```
- **Critère de validation** : `./gradlew assembleDebug` réussit avec Hilt + KSP compilant correctement (pas d'erreur `annotation processor`)

---

### TASK-003 — Configuration AndroidManifest.xml

- **Statut** : `[x]`
- **Dépendances** : TASK-001
- **Fichiers à modifier** :
  - `app/src/main/AndroidManifest.xml`
- **Changements** :
  - Permissions : `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE` (maxSdkVersion=32), `WAKE_LOCK`
  - `PlayerActivity` : `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`, `launchMode="singleTask"`, thème fullscreen
  - Intent-filter ACTION_VIEW avec `video/*`, schémas `file://` et `content://`
  - Attribut `android:name=".SwipePlayerApp"` sur `<application>`
- **Critère de validation** : `./gradlew lint` ne remonte aucune erreur sur le manifest

---

### TASK-004 — SwipePlayerApp + AppModule (squelette Hilt)

- **Statut** : `[x]`
- **Dépendances** : TASK-002, TASK-003
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/SwipePlayerApp.kt` → `@HiltAndroidApp class SwipePlayerApp : Application()`
  - `app/src/main/java/com/swipeplayer/di/AppModule.kt` → module `@Singleton` avec providers : `Context`, `AudioManager`, `ContentResolver`
    _(Les providers pour VideoRepository et AudioFocusManager seront ajoutés dans leurs tâches respectives)_
- **Critère de validation** : `./gradlew assembleDebug` réussit, Hilt génère les composants sans erreur

---

## M1 — Data Layer

Modèles de données, accès fichiers, historique de navigation.

---

### TASK-005 — `VideoFile` data class

- **Statut** : `[x]`
- **Dépendances** : TASK-001
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/data/VideoFile.kt`
- **Contenu** :
  ```kotlin
  data class VideoFile(val uri: Uri, val name: String, val path: String, val duration: Long, val size: Long = 0L)
  val VIDEO_EXTENSIONS = setOf("mp4","mkv","avi","mov","wmv","flv","webm","m4v","3gp","ts","mpg","mpeg")
  ```
- **Critère de validation** : la classe compile, `VIDEO_EXTENSIONS` couvre les 12 formats listés dans les specs

---

### TASK-006 — Utilitaire `naturalCompare` (tri naturel)

- **Statut** : `[x]`
- **Dépendances** : TASK-005
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/util/FileUtils.kt`
- **Contenu** : fonction `naturalCompare(a: String, b: String): Int` via tokenisation regex `(\d+)|(\D+)` + extension `List<VideoFile>.sortedNaturally()`
- **Critère de validation** : test unitaire confirmant `"video2.mp4" < "video10.mp4"`, `"Episode 9" < "Episode 10"`, insensible à la casse

---

### TASK-007 — `VideoRepository` (listing + résolution URI)

- **Statut** : `[x]`
- **Dépendances** : TASK-004, TASK-005, TASK-006
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/data/VideoRepository.kt`
- **Implémente** :
  - `listVideosInDirectory(uri: Uri): List<VideoFile>` — dispatche sur le type d'URI
  - `listViaFile(file: File)` — `File.parentFile.listFiles()` filtré sur `VIDEO_EXTENSIONS`
  - `queryMediaStore(uri: Uri)` — requête MediaStore avec `BUCKET_ID` pour trouver le répertoire
  - `resolveViaDocumentFile(uri: Uri)` — SAF via `DocumentFile`, catch `SecurityException` → liste vide
  - `isMediaStoreUri()`, `isSafUri()`
  - Tri final via `naturalCompare`
- **Provider Hilt** : ajouter dans `AppModule.kt`
- **Critère de validation** : pour une URI `file://`, retourne la liste triée du répertoire ; pour un `content://` inaccessible, retourne une liste d'un seul élément sans crash

---

### TASK-008 — `PlaybackHistory` (pile de navigation + peekNext)

- **Statut** : `[x]`
- **Dépendances** : TASK-005
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/data/PlaybackHistory.kt`
- **Implémente** :
  - `init(startVideo, playlist)`
  - `navigateForward(): VideoFile` — avance dans l'historique ou choisit une vidéo aléatoire parmi les non-vues (reset pool si tout vu sauf courante)
  - `navigateBack(): VideoFile?` — retourne `null` si `currentIndex == 0`
  - `peekNext(playlist): VideoFile` — pré-sélectionne sans avancer `currentIndex`, mémorise dans `peekNextVideo`
  - `commitPeek()` — confirme le peek lors d'un swipe réel
  - Propriétés : `current`, `canGoBack`, `canGoForward`
- **Critère de validation** : test unitaire couvrant : navigation avant/arrière, pool vidéos vues, reset du pool, peekNext préservé lors d'un swipe-down

---

## M2 — Player Core

Configuration ExoPlayer, gestion audio focus, cycle de vie des instances.

---

### TASK-009 — `PlayerConfig` (constantes et factory HW+)

- **Statut** : `[x]`
- **Dépendances** : TASK-002
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/player/PlayerConfig.kt`
- **Contenu** : `object PlayerConfig` avec toutes les constantes (buffers, zones de geste, timings, zoom min/max, swipe min dp, controls hide delay, codec skip delay) + `renderersFactory` + `loadControl`
- **Critère de validation** : compile, `renderersFactory` produit un `DefaultRenderersFactory` avec `setEnableDecoderFallback(false)` et `EXTENSION_RENDERER_MODE_OFF`

---

### TASK-010 — `AudioFocusManager`

- **Statut** : `[x]`
- **Dépendances** : TASK-004, TASK-009
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/player/AudioFocusManager.kt`
- **Implémente** :
  - Interface `Listener` : `onPause()`, `onResume()`, `onDuck()`, `onUnduck()`
  - `requestFocus(): Boolean`
  - `abandonFocus()`
  - `registerNoisyReceiver(context)` / `unregisterNoisyReceiver(context)` — `ACTION_AUDIO_BECOMING_NOISY`
  - Réactions : `LOSS` → pause ; `LOSS_TRANSIENT` → pause+flag ; `GAIN` → resume+unduck ; `CAN_DUCK` → duck 30%
- **Provider Hilt** : ajouter dans `AppModule.kt`
- **Critère de validation** : compile ; en test manuel, le débranchement des écouteurs met la lecture en pause

---

### TASK-011 — `VideoPlayerManager` (max 2 instances, swap, release)

- **Statut** : `[x]`
- **Dépendances** : TASK-004, TASK-005, TASK-009
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/player/VideoPlayerManager.kt`
- **Implémente** :
  - `preparePlayer(video, preloadOnly): ExoPlayer` — crée avec config HW+, `setMediaItem`, `prepare()`
  - `preloadNext(video)` — prépare `nextPlayer` en `preloadOnly = true`
  - `swapToNext(surfaceView)` — next→current, libère l'ancien current
  - `releasePlayer(player, surfaceView?)` — séquence ordonnée sur `Dispatchers.IO` : `clearVideoSurfaceView` → `stop` → `clearMediaItems` → `release`
  - `releaseAll()`
  - `attachSurface(player, sv)` / `detachSurface(player, sv)`
  - Gestion codec failure : `onPlayerError` → si `DecoderInitializationException`, appeler `onCodecFailure()`
- **Critère de validation** : deux vidéos lisibles en séquence sans fuite mémoire ; `releaseAll()` ne crash pas si un player est déjà null

---

## M3 — ViewModel & État UI

---

### TASK-012 — `PlayerUiState` + types associés

- **Statut** : `[x]`
- **Dépendances** : TASK-005
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/PlayerUiState.kt`
- **Contenu** :
  - `data class PlayerUiState(...)` avec tous les champs (voir design.md Section 2)
  - `enum class DisplayMode { ADAPT, FILL, STRETCH, NATIVE_100 }`
  - `enum class OrientationMode { AUTO, LANDSCAPE, PORTRAIT }`
  - `sealed class PlayerError { CodecNotSupported, FileNotFound, ContentUriNoAccess, Generic }`
  - `enum class TapSide { LEFT, RIGHT }`
- **Critère de validation** : compile ; les valeurs par défaut correspondent aux specs (DisplayMode.ADAPT, OrientationMode.AUTO, brightness=-1f)

---

### TASK-013 — `TimeFormat` utilitaire

- **Statut** : `[x]`
- **Dépendances** : TASK-001
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/util/TimeFormat.kt`
- **Implémente** : `fun Long.toTimecode(): String` — `MM:SS` si durée < 1h, `HH:MM:SS` sinon ; gère 0ms et durées > 4h
- **Critère de validation** : test unitaire : `183000L → "03:03"`, `3723000L → "01:02:03"`, `0L → "00:00"`, `14400000L → "04:00:00"`

---

### TASK-014 — `PlayerViewModel`

- **Statut** : `[x]`
- **Dépendances** : TASK-007, TASK-008, TASK-010, TASK-011, TASK-012, TASK-013
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/PlayerViewModel.kt`
- **Implémente** :
  - `@HiltViewModel`, injection de `VideoRepository`, `VideoPlayerManager`, `AudioFocusManager`
  - `_uiState: MutableStateFlow<PlayerUiState>` + `uiState: StateFlow`
  - `onIntentReceived(uri)` → `loadPlaylist` + lecture initiale
  - `onSwipeUp()`, `onSwipeDown()` → `PlaybackHistory` + `VideoPlayerManager.swapToNext`
  - `onPlayPause()`, `onSeek(ms)`, `onSeekRelative(delta)`, `onSpeedChange(speed)`
  - `onToggleControls()`, `onZoomChange(scale)`, `onDisplayModeChange()`, `onOrientationChange()`
  - `triggerPeekNext()` — appelé dès que `isPlaying` passe à true
  - `AudioFocusManager.Listener` : `onPause`, `onResume`, `onDuck`, `onUnduck`
  - Hooks lifecycle : `onActivityStart()`, `onActivityStop()`, `onActivityDestroy()`
  - Edge cases : codec failure (toast + skip après 2s), fichier supprimé (toast + skip), content:// sans accès (toast + mode single)
- **Critère de validation** : `uiState` émet correctement lors des appels ; `onSwipeUp` deux fois depuis une playlist de 3 vidéos produit `currentIndex == 2`

---

## M4 — Activity & Écran Shell

---

### TASK-015 — `PlayerActivity` (intent handling + fullscreen)

- **Statut** : `[x]`
- **Dépendances** : TASK-004, TASK-014
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/PlayerActivity.kt`
- **Remplace** : `MainActivity.kt` (qui peut être supprimée)
- **Implémente** :
  - `@AndroidEntryPoint`, `viewModels<PlayerViewModel>()`
  - `onCreate` : `enableFullscreen()` + `setContent { PlayerScreen(...) }` + `handleIntent(intent)`
  - `onNewIntent` : `handleIntent(intent)` (singleTask)
  - `onStart` / `onStop` : `audioFocusManager.registerNoisyReceiver` / `unregister` + `viewModel.onActivityStart/Stop`
  - `onDestroy` : `viewModel.onActivityDestroy()`
  - `enableFullscreen()` : `WindowInsetsControllerCompat` en mode immersif sticky
  - `handleIntent(intent)` : extrait l'URI + appelle `viewModel.onIntentReceived(uri)`
- **Critère de validation** : l'app se lance depuis un explorateur de fichiers, joue la vidéo, les barres système sont masquées

---

### TASK-016 — `VideoSurface` composable

- **Statut** : `[x]`
- **Dépendances** : TASK-002, TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/VideoSurface.kt`
- **Implémente** :
  - `AndroidView { SurfaceView }` + `player.setVideoSurfaceView(sv)` dans `factory` et `update`
  - `graphicsLayer { scaleX = zoomScale; scaleY = zoomScale }` sur la surface uniquement
  - Gestion du cas `player == null` (surface vide, pas de crash)
- **Critère de validation** : la vidéo s'affiche, le zoom à 2x double la taille sans affecter les overlays

---

### TASK-017 — `PlayerScreen` (VerticalPager, reset silencieux)

- **Statut** : `[x]`
- **Dépendances** : TASK-014, TASK-016
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/screen/PlayerScreen.kt`
- **Implémente** :
  - `rememberPagerState(initialPage = 1, pageCount = { 3 })` — pages 0=prev, 1=current, 2=next
  - `VerticalPager(userScrollEnabled = false, beyondBoundsPageCount = 1)`
  - `LaunchedEffect(pagerState.currentPage)` : détecte changement → appelle `onSwipeUp/Down` → `pagerState.scrollToPage(1)` sans animation
  - `AnimatedVisibility` pour `ControlsOverlay` (placeholder à ce stade)
  - Blocage du swipe-down si `!uiState.canGoBack` (elastic bounce uniquement)
- **Critère de validation** : swiper vers le haut change la vidéo et le pager revient à la page 1 ; swiper vers le bas en `currentIndex == 0` produit un rebond sans changement de vidéo

---

## M5 — Système de Gestes

---

### TASK-018 — `SwipeDetector`

- **Statut** : `[x]`
- **Dépendances** : TASK-009
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/gesture/SwipeDetector.kt`
- **Implémente** :
  - `detect(startY, currentY, velocityY): SwipeResult?` — retourne UP/DOWN si `|delta| ≥ minDistancePx` ET velocity suffisante
  - `isHorizontalIntent(deltaX, deltaY): Boolean` — true si `|deltaX| > |deltaY| * 1.5`
  - `enum class SwipeResult { UP, DOWN }`
- **Critère de validation** : test unitaire : delta=100dp UP → `UP`, delta=30dp → `null`, mouvement horizontal → `isHorizontalIntent = true`

---

### TASK-019 — `PinchZoomHandler`

- **Statut** : `[x]`
- **Dépendances** : TASK-009
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/gesture/PinchZoomHandler.kt`
- **Implémente** : `calculateNewScale(previousSpan, currentSpan, currentScale): Float` clamped dans `[1f, 4f]`
- **Critère de validation** : test unitaire : span 100→200 depuis 1x → 2x ; span 100→50 depuis 1x → 1x (pas en dessous de 1x) ; depuis 3x, span 100→200 → 4x (clampage)

---

### TASK-020 — `GestureHandler` (pointerInput unique)

- **Statut** : `[x]`
- **Dépendances** : TASK-017, TASK-018, TASK-019
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/gesture/GestureHandler.kt`
- **Implémente** :
  - Modifier extension `fun Modifier.gestureHandler(...)` avec un seul `pointerInput`
  - `awaitEachGesture` : détecte 2 pointeurs → pinch ; 1 pointeur → routage par zone X
  - `classifyZone(x, screenWidth): GestureZone` — LEFT / CENTER / RIGHT
  - Zone LEFT/RIGHT : swipe vertical → `onBrightnessChange` / `onVolumeChange` en temps réel
  - Zone CENTER : pinch prioritaire, puis si `zoomScale > 1f` → swipe désactivé (pan only) ; sinon SwipeDetector, double-tap (200ms window), tap simple
  - Mouvement horizontal initial → annule swipe, passe en seekbar mode
- **Intégration** : brancher dans `PlayerScreen.kt` sur le `Box` principal
- **Critère de validation** : en test manuel, swipe centre change vidéo, swipe gauche règle la luminosité, pinch zoome sans déclencher de swipe

---

## M6 — Composants UI

---

### TASK-021 — `TopBar` composable

- **Statut** : `[x]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/TopBar.kt`
- **Contenu** : bouton retour (ArrowBack) + titre fichier (16sp, blanc, ellipsis, shadow), fond `#80000000`
- **Critère de validation** : affiche le nom du fichier ; le bouton retour appelle `onBack()`

---

### TASK-022 — `CenterControls` composable

- **Statut** : `[x]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/CenterControls.kt`
- **Contenu** : Row centré avec Replay10 (48dp) + Play/Pause (64dp) + Forward10 (48dp) ; icônes blanches Material
- **Critère de validation** : tap Play/Pause appelle `onPlayPause()` ; l'icône bascule entre ▶ et ❚❚

---

### TASK-023 — `ProgressBar` composable (seekbar + timecodes)

- **Statut** : `[x]`
- **Dépendances** : TASK-012, TASK-013
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/ProgressBar.kt`
- **Contenu** :
  - `Slider` Compose (ou Canvas custom) avec couleur filled `#E50914`, buffer `#80FFFFFF`, fond `#40FFFFFF`
  - Timecodes monospace 14sp à gauche (position) et droite (durée)
  - Format `MM:SS` / `HH:MM:SS` via `TimeFormat`
  - `onValueChangeFinished` → `viewModel.onSeek(ms)`
- **Critère de validation** : le slider se déplace en temps réel avec la lecture ; tap/drag appelle `onSeek` avec la bonne position

---

### TASK-024 — `DoubleTapFeedback` composable

- **Statut** : `[x]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/DoubleTapFeedback.kt`
- **Contenu** : cercles concentriques animés + texte "-10s" / "+10s" ; animation : fade-in 100ms + fade-out 400ms
- **Critère de validation** : apparaît et disparaît en 500ms après un double-tap ; côté gauche → "-10s", droit → "+10s"

---

### TASK-025 — `BrightnessControl` + `VolumeControl` composables

- **Statut** : `[x]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/BrightnessControl.kt`
  - `app/src/main/java/com/swipeplayer/ui/components/VolumeControl.kt`
- **Contenu** : barre verticale semi-transparente + icône (soleil `#FFC107` / haut-parleur `#2196F3`) ; visible uniquement pendant le geste (`brightness >= 0` / `isDragging`) ; animation update 100ms Linear
- **Critère de validation** : apparaît pendant le swipe latéral, disparaît à la fin du geste

---

### TASK-026 — `SpeedSelector` composable

- **Statut** : `[x]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/SpeedSelector.kt`
- **Contenu** : `DropdownMenu` avec les 9 vitesses (0.25x … 4x) ; la vitesse active est mise en évidence ; `onSpeedSelected` appelle `viewModel.onSpeedChange(speed)` → `player.setPlaybackParameters(PlaybackParameters(speed))`
- **Critère de validation** : chaque vitesse change effectivement la vitesse de lecture ; l'icône de la `ToolBar` affiche la vitesse courante

---

### TASK-027 — `SettingsSheet` composable (audio + sous-titres)

- **Statut** : `[x]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/SettingsSheet.kt`
- **Contenu** : `ModalBottomSheet` avec deux sections : sélection piste audio (liste des tracks), sélection sous-titres (pistes intégrées + fichiers externes .srt/.ass/.ssa) ; indicateur décodeur "HW+" (read-only)
- **Note** : la logique sous-titres complète est dans TASK-033 ; ici on implémente le shell UI
- **Critère de validation** : le sheet s'ouvre et se ferme ; les sections sont visibles (même vides)

---

### TASK-028 — `ToolBar` composable

- **Statut** : `[x]`
- **Dépendances** : TASK-026, TASK-027, TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/ToolBar.kt`
- **Contenu** : Row avec 4 boutons espacés 32dp : SpeedSelector trigger + SettingsSheet trigger + DisplayMode cycle + OrientationMode cycle ; icônes blanches
- **Critère de validation** : chaque bouton déclenche l'action correcte ; DisplayMode et OrientationMode cyclent dans l'ordre défini dans les specs

---

### TASK-029 — `ControlsOverlay` composable (assemblage + auto-hide)

- **Statut** : `[x]`
- **Dépendances** : TASK-021, TASK-022, TASK-023, TASK-024, TASK-025, TASK-028
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/ControlsOverlay.kt`
- **Implémente** :
  - `Box` fullscreen avec fond `#80000000`
  - Layout : `TopBar` en haut, `CenterControls` au centre, `Column(ProgressBar + ToolBar)` en bas, `BrightnessControl` à gauche, `VolumeControl` à droite
  - Auto-hide : `LaunchedEffect(controlsVisible)` → `delay(4000L)` → `viewModel.onToggleControls()`
  - `DoubleTapFeedback` overlayé, visible selon événements double-tap
- **Intégration** : brancher dans `PlayerScreen.kt` dans l'`AnimatedVisibility`
- **Critère de validation** : les contrôles disparaissent automatiquement après 4s ; réapparaissent au tap ; un double-tap déclenche l'animation DoubleTapFeedback

---

## M7 — Audio & MediaSession

---

### TASK-030 — `OrientationManager` utilitaire

- **Statut** : `[x]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/util/OrientationManager.kt`
- **Implémente** : `fun applyOrientation(activity: Activity, mode: OrientationMode)` — `ActivityInfo.SCREEN_ORIENTATION_SENSOR` / `LANDSCAPE` / `PORTRAIT` ; appelé par `PlayerViewModel.onOrientationChange()`
- **Critère de validation** : chaque mode verrouille/déverrouille correctement l'orientation de l'écran

---

### TASK-031 — MediaSession (media3-session)

- **Statut** : `[x]`
- **Dépendances** : TASK-014, TASK-015
- **Fichiers à modifier** :
  - `app/src/main/java/com/swipeplayer/ui/PlayerActivity.kt` → créer et lier `MediaSession`
  - `app/src/main/java/com/swipeplayer/ui/PlayerViewModel.kt` → exposer les callbacks MediaSession
- **Implémente** :
  - `MediaSession.Builder(context, player).build()` dans `PlayerActivity`
  - `MediaSession.Callback` : `onPlay`, `onPause`, `onSeekTo`
  - Libération dans `onDestroy`
  - Notification de lecture visible dans le shade Android
- **Critère de validation** : les contrôles de lecture apparaissent dans la notification système et dans l'écran de verrouillage ; play/pause depuis la notification fonctionne

---

## M8 — Sous-titres & Cas Limites

---

### TASK-032 — Support sous-titres (détection + sélection)

- **Statut** : `[x]`
- **Dépendances** : TASK-027, TASK-014
- **Fichiers à modifier** :
  - `app/src/main/java/com/swipeplayer/ui/PlayerViewModel.kt` → détecter pistes intégrées et fichiers `.srt`/`.ass`/`.ssa` dans le même répertoire ; exposer dans `PlayerUiState`
  - `app/src/main/java/com/swipeplayer/ui/components/SettingsSheet.kt` → afficher et permettre la sélection de la piste
- **Implémente** :
  - Détection des tracks intégrés via `player.currentTracks`
  - Détection des fichiers externes via `VideoRepository` (même répertoire, même nom de base)
  - Sélection via `player.trackSelectionParameters`
- **Critère de validation** : un fichier MKV avec piste SRT intégrée affiche la piste dans SettingsSheet ; activer les sous-titres les affiche à l'écran

---

### TASK-033 — Cas limites et gestion d'erreurs

- **Statut** : `[x]`
- **Dépendances** : TASK-014, TASK-020, TASK-029
- **Fichiers à modifier** :
  - `PlayerViewModel.kt` — compléter/vérifier tous les cas déjà esquissés
  - `PlayerScreen.kt` — affichage des erreurs/toasts
- **Cas à implémenter et tester** :
  - Répertoire avec 1 seule vidéo → `isSwipeEnabled = false`, rebond élastique uniquement
  - Codec non supporté (HW+ failure) → Toast "Codec non supporté", skip automatique après 2s
  - Fichier supprimé entre listing et lecture → Toast, retirer de la playlist, skip
  - `content://` sans accès répertoire → mode fichier unique, `isSwipeEnabled = false`, Toast explicatif
  - Vidéo > 4h → format `HH:MM:SS`, aucune limitation
  - Pool de vidéos vues entièrement → reset (excluant la vidéo courante), continuer aléatoire
  - App en arrière-plan → pause ; retour → reprise si était en lecture
  - Écran éteint → pause + libération partielle
  - Appel entrant → pause automatique via AudioFocus
- **Critère de validation** : chacun des 9 cas ci-dessus se comporte comme décrit sans crash

---

## M9 — Thème & Ressources

---

### TASK-034 — Thème fullscreen + ressources

- **Statut** : `[x]`
- **Dépendances** : TASK-001
- **Fichiers à créer/modifier** :
  - `app/src/main/res/values/themes.xml` → thème `Theme.SwipePlayer.Fullscreen` : `windowFullscreen`, `windowTranslucentStatus`, fond noir
  - `app/src/main/res/values/colors.xml` → toutes les couleurs du design (`#E50914`, `#80000000`, `#80FFFFFF`, `#40FFFFFF`, `#FFC107`, `#2196F3`)
  - `app/src/main/res/values/strings.xml` → chaînes localisables (messages d'erreur, labels)
- **Critère de validation** : l'app démarre avec fond noir, barres système masquées dès le splash ; toutes les couleurs référencées dans le code Compose compilent

---

## M10 — Intégration Finale & Smoke Test

---

### TASK-035 — Intégration finale et smoke test complet

- **Statut** : `[x]`
- **Dépendances** : toutes les tâches précédentes
- **Actions** :
  - Supprimer `MainActivity.kt` si elle n'a pas encore été supprimée
  - Vérifier que tous les providers Hilt sont bien déclarés dans `AppModule.kt`
  - `./gradlew lint` → 0 erreur
  - `./gradlew test` → 0 échec
  - Test manuel : ouvrir un `.mp4` depuis Files by Google → lecture démarre < 500ms ; swipe up × 3 → 3 vidéos différentes ; swipe down × 2 → retour dans l'historique ; pinch 2x → navigation désactivée ; double-tap droit → +10s ; swipe gauche → luminosité change ; MediaSession visible dans notifications
  - Test avec vidéo 4K HEVC + MKV multi-pistes + vidéo verticale
- **Critère de validation** : les 3 tests de performance sont respectés (intent<500ms, swipe<300ms, seek<200ms) ; 0 crash sur les parcours nominaux et les cas limites

---

## Bug Fixes

### BUG-001 — isSwipeEnabled default = true (crash au swipe)

- [x] Documenter : docs/bugs/BUG-001-swipe-crash-history-not-init.md
- [x] Fix : PlayerUiState.kt — isSwipeEnabled default = false
- [x] Tests : PlayerUiStateTest + PlayerViewModelTest (regression)
- [x] Mettre bug à FIXED
- [x] Commit : FIX BUG-001

---

### BUG-002 — Vidéo étirée (STRETCH) au lieu de ADAPT par défaut

- [x] Documenter : docs/bugs/BUG-002-default-display-mode-stretch.md
- [x] Fix : VideoSurface.kt — ajouter displayMode + tracking vidéo size + BoxWithConstraints
- [x] Fix : PlayerScreen.kt — passer displayMode à VideoSurface
- [x] Mettre bug à FIXED
- [x] Commit : FIX BUG-002

---

### BUG-003 — Menu réglages se ferme immédiatement

- [x] Documenter : docs/bugs/BUG-003-settings-menu-closes-immediately.md
- [x] Fix : ToolBar.kt — supprimer état local showSettings, ajouter paramètres
- [x] Fix : ControlsOverlay.kt — ajouter showSettingsSheet param, suspendre timer
- [x] Fix : PlayerScreen.kt — gérer showSettingsSheet, SettingsSheet hors AnimatedVisibility
- [x] Mettre bug à FIXED
- [x] Commit : FIX BUG-003

---

### BUG-004 — Pas de swipe en mode paysage

- [x] Documenter : docs/bugs/BUG-004-no-swipe-landscape.md
- [x] Fix : GestureHandler.kt — ajouter screenWidthPx/screenHeightPx aux clés pointerInput
- [x] Commit : FIX BUG-004

---

### BUG-005 — Codec non supporté : vidéo ignorée au lieu de fallback logiciel

- [x] Documenter : docs/bugs/BUG-005-codec-not-supported-skip.md
- [x] Fix : PlayerConfig.kt — setEnableDecoderFallback(true)
- [x] Mettre bug à FIXED
- [x] Commit : FIX BUG-005

---

### BUG-006 — Zoom saccadé (zoomScale comme clé pointerInput)

- [x] Documenter : docs/bugs/BUG-006-zoom-not-fluid.md
- [x] Fix : GestureHandler.kt — zoomScale en lambda, retirer des clés pointerInput
- [x] Fix : PlayerScreen.kt — passer { uiState.zoomScale }
- [x] Commit : FIX BUG-006 (a6a8f95)

---

### BUG-007 — Swipe bloqué quand zoomé

- [x] Documenter : docs/bugs/BUG-007-swipe-disabled-when-zoomed.md
- [x] Fix : GestureHandler.kt — supprimer zoomScale <= 1f
- [x] Commit : FIX BUG-007 (a6a8f95, avec BUG-006)

---

### FEAT-001 — Persistance position/zoom/format par vidéo

- [x] Spec : docs/specs/FEAT-001-video-resume.md
- [x] PlayerConfig.kt : MAX_ZOOM_SCALE → 50f
- [x] Créer data/VideoStateStore.kt
- [x] PlayerViewModel.kt : injection + load au démarrage + save au changement
- [x] Commit : FEAT-001 (83bfb77)

---

### FEAT-002 — Bouton format → menu déroulant avec ratios forcés

- [x] Spec : docs/specs/FEAT-002-format-menu.md
- [x] PlayerUiState.kt : ajouter RATIO_1_1, RATIO_3_4, RATIO_16_9 à DisplayMode
- [x] VideoSurface.kt : gérer les nouveaux modes
- [x] Créer ui/components/FormatSelector.kt
- [x] ToolBar.kt : remplacer bouton cycle par FormatSelector
- [x] ControlsOverlay.kt : adapter la signature
- [x] PlayerViewModel.kt : ajouter onDisplayModeSet()
- [x] Commit : FEAT-002 (740fa38)

---

### FEAT-003 — Passage automatique à la vidéo suivante en fin de lecture

- [x] Spec : docs/specs/FEAT-003-auto-next.md
- [x] VideoPlayerManager.kt : onPlaybackEnded callback
- [x] PlayerViewModel.kt : onVideoEnded()
- [x] Commit : FEAT-003 (e11cb70)

---

### BUG-008 — Menu format/vitesse se referme immédiatement

- [x] Documenter : docs/bugs/BUG-008-format-menu-closes-immediately.md
- [x] Fix : FormatSelector.kt + SpeedSelector.kt — onMenuStateChange callback
- [x] Fix : ToolBar.kt — propager le callback
- [x] Fix : ControlsOverlay.kt — suspendre timer si menu ouvert
- [x] Commit : FIX BUG-008 (d97b97f, 4e7d563)

---

### BUG-009 — Volume slider sans effet sur le son

- [x] Documenter : docs/bugs/BUG-009-volume-slider-no-effect.md
- [x] Fix : PlayerViewModel.kt — appliquer volume au player dans onVolumeDelta/onVolumeChange
- [x] Commit : FIX BUG-009 (752a3e4)

---

### FEAT-004 — Icône originale

- [x] Spec : docs/specs/FEAT-004-app-icon.md
- [x] Icône PNG fournie par l'utilisateur — legacy + round + adaptive générés par script Pillow
- [x] Commit : FEAT-004 (23d7f1e) + icône PNG utilisateur (ce commit)

---

### FEAT-005 — Lancement depuis icône : sélection de vidéo

- [x] Spec : docs/specs/FEAT-005-launcher-file-picker.md
- [x] PlayerScreen.kt — écran "Choisir une vidéo" si currentVideo == null
- [x] Commit : FEAT-005 (c2de903)

---

### BUG-010 — SwipePlayer absent du chooser "Ouvrir avec"

- [x] Documenter : docs/bugs/BUG-010-app-absent-open-with-chooser.md
- [x] Fix : AndroidManifest.xml — BROWSABLE + pathPattern pour 12 extensions

---

### FEAT-006 — Maintien de l'écran allumé pendant la lecture

- [x] Spec : docs/specs/FEAT-006-keep-screen-on.md
- [x] Impl : PlayerActivity.kt — FLAG_KEEP_SCREEN_ON via LaunchedEffect(isPlaying)

---

### BUG-011 — Sliders luminosité/volume invisibles quand les contrôles sont cachés

- [x] Documenter : docs/bugs/BUG-011-brightness-volume-hidden-when-controls-hidden.md
- [x] Fix : ControlsOverlay.kt — sortir BrightnessControl et VolumeControl du bloc AnimatedVisibility des contrôles principaux
- [x] Fix : PlayerScreen.kt — BrightnessControl et VolumeControl déplacés dans le Box principal hors AnimatedVisibility
- [x] Mettre bug à FIXED
- [x] Commit : FIX BUG-011

---

### FEAT-007 — Menu vitesses : 0.33x ajouté, 1.25x et 1.75x supprimés

- [x] Spec : docs/specs/FEAT-007-speed-menu-update.md
- [x] SpeedSelector.kt — SPEED_OPTIONS mis à jour (0.25, 0.33, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0)
- [x] Commit : FEAT-007

---

### FEAT-008 — Seek par swipe horizontal (style MX Player)

- [x] Spec : docs/specs/FEAT-008-horizontal-swipe-seek.md
- [x] PlayerUiState.kt — isSeekingHorizontally, horizontalSeekTargetMs, horizontalSeekDeltaMs
- [x] PlayerViewModel.kt — onHorizontalSeekStart/Update/End/Cancel
- [x] Créer ui/components/HorizontalSeekIndicator.kt
- [x] GestureHandler.kt — détection swipe horizontal + callbacks seek
- [x] PlayerScreen.kt — HorizontalSeekIndicator + wiring des callbacks
- [x] Commit : FEAT-008

---

## Résumé des milestones

| Milestone | Tâches | Prérequis |
|---|---|---|
| M0 — Bootstrap | TASK-001 → TASK-004 | — |
| M1 — Data Layer | TASK-005 → TASK-008 | M0 |
| M2 — Player Core | TASK-009 → TASK-011 | M0, M1 partiel |
| M3 — ViewModel | TASK-012 → TASK-014 | M1, M2 |
| M4 — Activity Shell | TASK-015 → TASK-017 | M3 |
| M5 — Gestes | TASK-018 → TASK-020 | M4 |
| M6 — UI Controls | TASK-021 → TASK-029 | M3, M5 |
| M7 — Audio/Session | TASK-030 → TASK-031 | M4, M6 |
| M8 — Edge Cases | TASK-032 → TASK-033 | M6, M7 |
| M9 — Ressources | TASK-034 | M0 (indépendant) |
| M10 — Final | TASK-035 | Tout |

**Total : 35 tâches**

---

## Code Review — Corrections (docs/CODE_REVIEW.md)

Session 2026-03-12. Toutes issues issues de la revue complète du projet.

### Phase 1 — CRITIQUE

- [x] **CR-001** `VideoPlayerManager.kt` + `PlayerViewModel.kt` — swapToNext() jamais appelé, preloading non-fonctionnel (BUG-012)
- [x] **CR-002** `VideoPlayerManager.kt` — surface non détachée de l'ancien player avant swap (BUG-013)
- [x] **CR-003** `PlayerViewModel.kt:556` — onSourceErrorDetected() logique défectueuse, crash potentiel (BUG-014)

### Phase 2 — MAJEUR

- [x] **CR-004** `VideoPlayerManager.kt:216` — erreurs nextPlayer déclenchent actions sur currentPlayer (BUG-015)
- [x] **CR-005** `PlayerViewModel.kt:116` — ContentUriNoAccess émis pour toutes playlists à 1 vidéo (BUG-016)
- [x] **CR-006** `PlayerConfig.kt:84` — KDoc incohérent : dit setEnableDecoderFallback(false) mais code fait true
- [x] **CR-007** `PinchZoomHandler.kt:9` — commentaire dit "1x..4x" au lieu de 50x
- [x] **CR-008** `SwipeDetector.kt:28` — vélocité minimum swipe non implémentée (BUG-017)
- [x] **CR-009** `PlayerViewModel.kt` — sous-titres externes .srt/.ass/.ssa non implémentés (BUG-018)
- [x] **CR-010** `PlayerViewModel.kt:450` — race condition lors de swipes rapides dans startPlayback() (BUG-019)
- [x] **CR-011** `PlayerActivity.kt:206` — UX agressive : renvoi systématique vers Paramètres pour MANAGE_EXTERNAL_STORAGE (BUG-020)

### Phase 3 — Documentation + Performance

- [x] **CR-014** `VideoSurface.kt:148` — setVideoSurfaceView() appelé inutilement à chaque recomposition
- [x] **CR-015** `ProgressBar.kt` — buffer progress non visualisé

### Phase 4 — Mineur

- [x] **CR-012** `ProgressBar.kt:39` — isDragging type ambiguë
- [x] **CR-016** `BrightnessControl.kt` + `VolumeControl.kt` — duplication code → VerticalIndicatorBar
- [x] **CR-017** `PlayerViewModel.kt` — logique resumeAfterSeek() dupliquée
- [x] **CR-019** `PlaybackHistory.kt:148` — allocation IdentityHashMap à chaque pickRandom()
- [x] **CR-020** `VideoRepository.kt` — création VideoFile dupliquée → File.toVideoFile()
- [x] **CR-021** `PlayerViewModel.kt:545` — toast sans accents
- [x] **CR-022** `CLAUDE.md` — liste vitesses obsolète
- [x] **CR-023** `TopBar.kt` — shadow titre manquant

---

## Code Review Opus — Corrections (docs/CODE_REVIEW_OPUS.md)

Session 2026-03-12. Revue independante par Opus 4.6 — 21 corrections a appliquer.

### P0

- [x] **CRO-005** `VideoPlayerManager.kt` + `PlayerViewModel.kt` + `PlayerActivity.kt` — Singleton.close() tue le manager definitivement

### P1

- [x] **CRO-001** `PlayerConfig.kt` — LoadControl n'utilise pas les valeurs de buffer
- [x] **CRO-002** `GestureHandler.kt` — VelocityTracker recoit System.currentTimeMillis
- [x] **CRO-006** — corrige par CRO-005
- [x] **CRO-007** `VideoPlayerManager.kt` — SurfaceView dans Singleton = fuite Activity
- [x] **CRO-009** `PlayerViewModel.kt` + `PlaybackHistory.kt` — race condition navigation
- [x] **CRO-010** `PlayerViewModel.kt` — triggerPeekNext() preloads concurrents
- [x] **CRO-012** — corrige par CRO-005
- [x] **CRO-014** `PlayerViewModel.kt` — onUnduck() restaure volume a 1.0
- [x] **CRO-017** `AudioFocusManager.kt` — registerReceiver sans RECEIVER_NOT_EXPORTED
- [x] **CRO-018** `VideoPlayerManager.kt` — callbacks ExoPlayer sur playbackThread
- [x] **CRO-020** `GestureHandler.kt` + `PlayerScreen.kt` — pointerInput recree a chaque swipe
- [x] **CRO-023** `VideoRepository.kt` — queryMediaStoreByPath() uri.path invalide
- [x] **CRO-027** `PlayerScreen.kt` — double navigation possible

### P2

- [x] **CRO-003** `PlayerViewModel.kt` — polling 500ms -> 250ms
- [x] **CRO-004** `VideoSurface.kt` + `PlayerScreen.kt` — zoomScale en lambda
- [x] **CRO-013** `VideoPlayerManager.kt` — HandlerThread lazy start
- [x] **CRO-021** `GestureHandler.kt` — dead zone 8px -> 20dp
- [x] **CRO-024** `VideoRepository.kt` — resolveViaUriPath logging
- [x] **CRO-025** `VideoRepository.kt` — findExternalSubtitles fallback SAF
- [x] **CRO-030** `PlayerActivity.kt` — screenOffReceiver RECEIVER_NOT_EXPORTED

### P3

- [x] **CRO-026** `VideoRepository.kt` — supprimer isSafUri() code mort
- [x] **CRO-031** `VideoStateStore.kt` — cles SharedPreferences separateur ::
