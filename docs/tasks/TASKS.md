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

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
- **Dépendances** : TASK-009
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/gesture/PinchZoomHandler.kt`
- **Implémente** : `calculateNewScale(previousSpan, currentSpan, currentScale): Float` clamped dans `[1f, 4f]`
- **Critère de validation** : test unitaire : span 100→200 depuis 1x → 2x ; span 100→50 depuis 1x → 1x (pas en dessous de 1x) ; depuis 3x, span 100→200 → 4x (clampage)

---

### TASK-020 — `GestureHandler` (pointerInput unique)

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/TopBar.kt`
- **Contenu** : bouton retour (ArrowBack) + titre fichier (16sp, blanc, ellipsis, shadow), fond `#80000000`
- **Critère de validation** : affiche le nom du fichier ; le bouton retour appelle `onBack()`

---

### TASK-022 — `CenterControls` composable

- **Statut** : `[ ]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/CenterControls.kt`
- **Contenu** : Row centré avec Replay10 (48dp) + Play/Pause (64dp) + Forward10 (48dp) ; icônes blanches Material
- **Critère de validation** : tap Play/Pause appelle `onPlayPause()` ; l'icône bascule entre ▶ et ❚❚

---

### TASK-023 — `ProgressBar` composable (seekbar + timecodes)

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/DoubleTapFeedback.kt`
- **Contenu** : cercles concentriques animés + texte "-10s" / "+10s" ; animation : fade-in 100ms + fade-out 400ms
- **Critère de validation** : apparaît et disparaît en 500ms après un double-tap ; côté gauche → "-10s", droit → "+10s"

---

### TASK-025 — `BrightnessControl` + `VolumeControl` composables

- **Statut** : `[ ]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/BrightnessControl.kt`
  - `app/src/main/java/com/swipeplayer/ui/components/VolumeControl.kt`
- **Contenu** : barre verticale semi-transparente + icône (soleil `#FFC107` / haut-parleur `#2196F3`) ; visible uniquement pendant le geste (`brightness >= 0` / `isDragging`) ; animation update 100ms Linear
- **Critère de validation** : apparaît pendant le swipe latéral, disparaît à la fin du geste

---

### TASK-026 — `SpeedSelector` composable

- **Statut** : `[ ]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/SpeedSelector.kt`
- **Contenu** : `DropdownMenu` avec les 9 vitesses (0.25x … 4x) ; la vitesse active est mise en évidence ; `onSpeedSelected` appelle `viewModel.onSpeedChange(speed)` → `player.setPlaybackParameters(PlaybackParameters(speed))`
- **Critère de validation** : chaque vitesse change effectivement la vitesse de lecture ; l'icône de la `ToolBar` affiche la vitesse courante

---

### TASK-027 — `SettingsSheet` composable (audio + sous-titres)

- **Statut** : `[ ]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/SettingsSheet.kt`
- **Contenu** : `ModalBottomSheet` avec deux sections : sélection piste audio (liste des tracks), sélection sous-titres (pistes intégrées + fichiers externes .srt/.ass/.ssa) ; indicateur décodeur "HW+" (read-only)
- **Note** : la logique sous-titres complète est dans TASK-033 ; ici on implémente le shell UI
- **Critère de validation** : le sheet s'ouvre et se ferme ; les sections sont visibles (même vides)

---

### TASK-028 — `ToolBar` composable

- **Statut** : `[ ]`
- **Dépendances** : TASK-026, TASK-027, TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/ui/components/ToolBar.kt`
- **Contenu** : Row avec 4 boutons espacés 32dp : SpeedSelector trigger + SettingsSheet trigger + DisplayMode cycle + OrientationMode cycle ; icônes blanches
- **Critère de validation** : chaque bouton déclenche l'action correcte ; DisplayMode et OrientationMode cyclent dans l'ordre défini dans les specs

---

### TASK-029 — `ControlsOverlay` composable (assemblage + auto-hide)

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
- **Dépendances** : TASK-012
- **Fichiers à créer** :
  - `app/src/main/java/com/swipeplayer/util/OrientationManager.kt`
- **Implémente** : `fun applyOrientation(activity: Activity, mode: OrientationMode)` — `ActivityInfo.SCREEN_ORIENTATION_SENSOR` / `LANDSCAPE` / `PORTRAIT` ; appelé par `PlayerViewModel.onOrientationChange()`
- **Critère de validation** : chaque mode verrouille/déverrouille correctement l'orientation de l'écran

---

### TASK-031 — MediaSession (media3-session)

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
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

- **Statut** : `[ ]`
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
