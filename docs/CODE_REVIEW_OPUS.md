# Code Review Opus — SwipePlayer

**Auteur** : Claude Opus 4.6 (revue independante)
**Date** : 2026-03-12
**Contexte** : Revue critique du projet complet, perspective developpeur Android senior specialise performance et lecteurs video.

---

## Severite

| Niveau | Signification |
|--------|---------------|
| **P0** | Crash, corruption de donnees, fuite memoire fatale |
| **P1** | Bug fonctionnel visible, race condition exploitable, degradation de perf significative |
| **P2** | Probleme latent, mauvaise pratique, dette technique importante |
| **P3** | Amelioration recommandee, nettoyage |

---

## INSTRUCTIONS POUR LE CORRECTEUR (Claude Sonnet)

**Corrige TOUS les CRO dans l'ordre numerique (CRO-001, CRO-002, ..., CRO-031).**

Pour chaque CRO :
1. Lis le fichier concerne
2. Applique le fix exact decrit dans la section "FIX DETAILLE"
3. Verifie que le code compile (`./gradlew assembleDebug`)
4. Passe au CRO suivant

**REGLES** :
- Les sections "FIX DETAILLE" contiennent le code exact a ecrire. Suis-les a la lettre.
- Si un CRO dit "NE PAS CORRIGER", passe au suivant.
- Ne modifie aucun fichier qui n'est pas explicitement mentionne.
- Mets a jour `docs/tasks/TASKS.md` au fur et a mesure.
- A la fin, lance `./gradlew installDebug` pour deployer et demande a l'utilisateur de tester.

---

## 1. Performance et fluidite

### CRO-001 [P1] — LoadControl: les valeurs de buffer ne sont pas appliquees

**Fichier** : `PlayerConfig.kt`

Les constantes `MIN_BUFFER_MS`, `MAX_BUFFER_MS`, etc. sont declarees mais **jamais utilisees**. Le `DefaultLoadControl()` utilise les valeurs par defaut de Media3 (50s min / 50s max), pas les 5s/30s voulus par la spec.

**FIX DETAILLE** :

Dans `PlayerConfig.kt`, remplacer :
```kotlin
@OptIn(UnstableApi::class)
val loadControl: DefaultLoadControl = DefaultLoadControl()
```
par :
```kotlin
@OptIn(UnstableApi::class)
val loadControl: DefaultLoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        MIN_BUFFER_MS,
        MAX_BUFFER_MS,
        BUFFER_FOR_PLAYBACK_MS,
        BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
    )
    .build()
```

Mettre a jour le KDoc au-dessus pour supprimer le commentaire "DefaultLoadControl.Builder was removed" qui est faux.

**Si `DefaultLoadControl.Builder()` n'existe pas dans Media3 1.5.1** (erreur de compilation), alors utiliser :
```kotlin
val loadControl: DefaultLoadControl = DefaultLoadControl(
    DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    MIN_BUFFER_MS.toLong(),
    MAX_BUFFER_MS.toLong(),
    BUFFER_FOR_PLAYBACK_MS.toLong(),
    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS.toLong(),
)
```
Avec l'import `import androidx.media3.common.C`.

---

### CRO-002 [P1] — VelocityTracker recoit le mauvais type de timestamp

**Fichier** : `GestureHandler.kt`

**FIX DETAILLE** :

1. Ajouter l'import en haut du fichier :
```kotlin
import android.os.SystemClock
```

2. Remplacer la ligne :
```kotlin
val startTimeMs = System.currentTimeMillis()
```
par :
```kotlin
val startTimeMs = SystemClock.uptimeMillis()
```

3. Remplacer la ligne :
```kotlin
velocityTracker.addPosition(System.currentTimeMillis(), pos)
```
par :
```kotlin
velocityTracker.addPosition(SystemClock.uptimeMillis(), pos)
```

ATTENTION : il y a aussi un `System.currentTimeMillis()` pour le double-tap detection (`lastTapTimeMs`, `now`). **Celui-la doit rester tel quel** — ce n'est pas un VelocityTracker, c'est juste un timer humain. OU mieux : le convertir aussi en `SystemClock.uptimeMillis()` pour la coherence (les deux horloges marchent pour mesurer des delais < 200ms).

Decision : convertir TOUTES les occurrences de `System.currentTimeMillis()` en `SystemClock.uptimeMillis()` dans ce fichier.

---

### CRO-003 [P2] — Position polling a 500ms

**Fichier** : `PlayerViewModel.kt`

**FIX DETAILLE** :

Dans `startPositionPolling()`, changer `delay(500L)` en `delay(250L)`.

---

### CRO-004 [P2] — VideoSurface : recomposition a chaque changement de zoomScale

**Fichiers** : `VideoSurface.kt` + `PlayerScreen.kt`

**FIX DETAILLE** :

1. Dans `VideoSurface.kt`, changer la signature du parametre `zoomScale` de `Float` en `() -> Float` :
```kotlin
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    zoomScale: () -> Float,
    displayMode: DisplayMode,
    modifier: Modifier = Modifier,
)
```

2. Dans `VideoSurface.kt`, mettre a jour le `graphicsLayer` pour appeler le lambda :
```kotlin
modifier = modifier
    .graphicsLayer {
        val s = zoomScale()
        scaleX = s
        scaleY = s
    }
    .clipToBounds(),
```

3. Dans `PlayerScreen.kt`, passer un lambda au lieu de la valeur :
```kotlin
VideoSurface(
    player = viewModel.currentPlayer,
    zoomScale = { uiState.zoomScale },
    displayMode = uiState.displayMode,
    modifier = Modifier.fillMaxSize(),
)
```

---

## 2. Stabilite

### CRO-005 [P0] — VideoPlayerManager est @Singleton mais close() est appele par le ViewModel

**Fichiers** : `VideoPlayerManager.kt` + `PlayerViewModel.kt` + `PlayerActivity.kt`

C'est le fix le plus important. Voici exactement quoi faire :

**FIX DETAILLE** :

#### Etape 1 : VideoPlayerManager.kt — supprimer close(), rendre le scope et le thread immortels

Supprimer entierement la methode `close()` :
```kotlin
// SUPPRIMER CES LIGNES :
/** Cancels internal scope and stops the shared playback thread. */
fun close() {
    managerScope.cancel()
    playbackThread.quitSafely()
}
```

Dans `releaseAll()`, ajouter la mise a null du `_currentPlayerState` :
```kotlin
suspend fun releaseAll() {
    val c = currentPlayer
    val n = nextPlayer
    currentPlayer = null
    nextPlayer = null
    nextPlayerVideo = null
    currentSurfaceView = null
    _currentPlayerState.value = null  // AJOUTER CETTE LIGNE

    c?.let { releasePlayer(it, surfaceView = null) }
    n?.let { releasePlayer(it, surfaceView = null) }
}
```

Aussi, nullifier les callbacks dans `releaseAll()` pour eviter de retenir le ViewModel :
```kotlin
suspend fun releaseAll() {
    val c = currentPlayer
    val n = nextPlayer
    currentPlayer = null
    nextPlayer = null
    nextPlayerVideo = null
    currentSurfaceView = null
    _currentPlayerState.value = null
    onCodecFailure = null       // AJOUTER
    onSourceError = null        // AJOUTER
    onTracksChanged = null      // AJOUTER
    onPlaybackEnded = null      // AJOUTER

    c?.let { releasePlayer(it, surfaceView = null) }
    n?.let { releasePlayer(it, surfaceView = null) }
}
```

#### Etape 2 : PlayerViewModel.kt — supprimer onActivityDestroy() et simplifier onCleared()

Supprimer entierement la methode `onActivityDestroy()`.

Modifier `onCleared()` pour utiliser `runBlocking` au lieu de `viewModelScope.launch` (car viewModelScope est deja annule dans onCleared) :
```kotlin
override fun onCleared() {
    super.onCleared()
    positionPollingJob?.cancel()
    codecSkipJob?.cancel()
    hideBrightnessBarJob?.cancel()
    hideVolumeBarJob?.cancel()
    clearDoubleTapJob?.cancel()
    audioFocusManager.listener = null
    // Release players synchronously (onCleared runs on main thread,
    // and releasePlayer uses withContext(Main) which resolves immediately).
    kotlinx.coroutines.runBlocking(Dispatchers.Main.immediate) {
        playerManager.releaseAll()
    }
}
```

NOTE IMPORTANTE : `runBlocking(Dispatchers.Main.immediate)` fonctionne ici car `onCleared()` est TOUJOURS appele sur le main thread. `Dispatchers.Main.immediate` execute immediatement si on est deja sur le main thread, donc pas de deadlock.

#### Etape 3 : PlayerActivity.kt — supprimer l'appel a onActivityDestroy()

Dans `PlayerActivity.onDestroy()`, supprimer la ligne `viewModel.onActivityDestroy()` :
```kotlin
override fun onDestroy() {
    super.onDestroy()
    mediaSession?.release()
    mediaSession = null
    // NE PLUS APPELER viewModel.onActivityDestroy()
}
```

---

### CRO-006 [P1] — onActivityDestroy() lance dans un scope potentiellement annule

**Fichier** : corrige par CRO-005 (suppression de onActivityDestroy). Rien de plus a faire.

---

### CRO-007 [P1] — currentSurfaceView dans le Singleton retient une reference a un View

**Fichier** : `VideoPlayerManager.kt`

**FIX DETAILLE** :

1. Ajouter l'import :
```kotlin
import java.lang.ref.WeakReference
```

2. Remplacer la declaration de `currentSurfaceView` :
```kotlin
// AVANT :
var currentSurfaceView: SurfaceView? = null
    private set

// APRES :
private var currentSurfaceRef: WeakReference<SurfaceView>? = null
val currentSurfaceView: SurfaceView? get() = currentSurfaceRef?.get()
```

3. Dans `attachSurface()`, remplacer :
```kotlin
currentSurfaceView = surfaceView
```
par :
```kotlin
currentSurfaceRef = WeakReference(surfaceView)
```

4. Dans `releaseAll()`, remplacer :
```kotlin
currentSurfaceView = null
```
par :
```kotlin
currentSurfaceRef = null
```

5. Dans `swapToNext()`, le code `val sv = currentSurfaceView ?: return false` fonctionne toujours grace au getter.

---

### CRO-008 [P2] — Double appel a close() / releaseAll()

Corrige par CRO-005 (close() supprime, releaseAll() appele uniquement dans onCleared()). Rien de plus a faire.

---

## 3. Race conditions swipe/ExoPlayer

### CRO-009 [P1] — Swipes rapides : PlaybackHistory accede sans protection entre suspend points

**Fichiers** : `PlayerViewModel.kt` + `PlaybackHistory.kt`

**FIX DETAILLE** :

#### Etape 1 : Ajouter un Mutex dans PlayerViewModel

Ajouter l'import :
```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
```

Ajouter le champ :
```kotlin
/** Serializes navigation operations to prevent PlaybackHistory corruption. */
private val navigationMutex = Mutex()
```

#### Etape 2 : Wrapper toutes les operations de navigation avec le Mutex

Modifier `onSwipeUp()` :
```kotlin
fun onSwipeUp() {
    if (!_uiState.value.isSwipeEnabled) return
    viewModelScope.launch {
        navigationMutex.withLock {
            history.current?.let { saveCurrentState(it) }
            val previous = history.current
            val next = history.navigateForward()
            switchToVideo(next, previousVideo = previous)
        }
    }
}
```

Modifier `onSwipeDown()` :
```kotlin
fun onSwipeDown() {
    if (!_uiState.value.isSwipeEnabled) return
    viewModelScope.launch {
        navigationMutex.withLock {
            val prev = history.navigateBack() ?: return@withLock
            val beforePrev = history.peekBack()  // CRO-028: use peekBack instead of navigate back+forward
            _uiState.update {
                it.copy(currentVideo = prev, previousVideo = beforePrev)
            }
            startPlayback(prev)
        }
    }
}
```

Modifier `onSourceErrorDetected()` — le bloc `viewModelScope.launch { ... }` interne doit aussi utiliser le mutex :
```kotlin
private fun onSourceErrorDetected() {
    val current = history.current ?: return
    viewModelScope.launch {
        navigationMutex.withLock {
            _toastEvents.emit("Fichier introuvable : ${current.name}")
            // ... reste du code existant identique ...
        }
    }
}
```

#### Etape 3 : Ajouter peekBack() a PlaybackHistory (pour CRO-028)

Dans `PlaybackHistory.kt`, ajouter cette methode dans la section "Navigation" :
```kotlin
/**
 * Returns the video before the current one in history without modifying
 * the currentIndex. Returns null if already at the beginning.
 */
fun peekBack(): VideoFile? {
    if (!canGoBack) return null
    return history.getOrNull(currentIndex - 1)
}
```

---

### CRO-010 [P1] — triggerPeekNext() peut lancer des preloads concurrents

**Fichier** : `PlayerViewModel.kt`

**FIX DETAILLE** :

1. Ajouter un champ Job :
```kotlin
/** Pending peek/preload job; cancelled before each new preload to avoid races. */
private var peekJob: Job? = null
```

2. Modifier `triggerPeekNext()` :
```kotlin
private fun triggerPeekNext() {
    val playlist = _uiState.value.playlist
    if (playlist.size <= 1) return
    peekJob?.cancel()
    peekJob = viewModelScope.launch {
        val next = history.peekNext()
        val subtitleFiles = videoRepository.findExternalSubtitles(next)
        playerManager.preloadNext(next, subtitleFiles)
    }
}
```

3. Ajouter l'annulation dans `onCleared()` :
```kotlin
peekJob?.cancel()
```
(a cote des autres `?.cancel()`)

---

### CRO-011 [P2] — swapToNext() thread safety

**NE PAS CORRIGER directement.** Ce probleme est attenue par CRO-018 (callbacks dispatches sur main) et CRO-009 (mutex de navigation). Apres ces deux fixes, tous les acces a swapToNext() passent par le main thread, ce qui est suffisant.

---

## 4. Fuites memoire

### CRO-012 [P1] — Callbacks lambda du VideoPlayerManager retiennent le ViewModel

**Fichier** : corrige par CRO-005 (les callbacks sont nullifies dans releaseAll()). Rien de plus a faire.

---

### CRO-013 [P2] — HandlerThread demarre dans le constructeur du Singleton

**Fichier** : `VideoPlayerManager.kt`

**FIX DETAILLE** :

Remplacer :
```kotlin
private val playbackThread = HandlerThread("SwipePlayer-Playback").also { it.start() }
```
par un demarrage paresseux :
```kotlin
private val playbackThread by lazy {
    HandlerThread("SwipePlayer-Playback").also { it.start() }
}
```

Le `lazy` est thread-safe par defaut (mode `SYNCHRONIZED`). Le thread ne demarrera qu'au premier appel a `createExoPlayer()` qui accede `playbackThread.looper`.

---

## 5. AudioFocus

### CRO-014 [P1] — onUnduck() restaure le volume a 1.0 au lieu de la valeur utilisateur

**Fichier** : `PlayerViewModel.kt`

**FIX DETAILLE** :

1. Ajouter un champ :
```kotlin
/** Volume level before audio ducking, restored on unduck. */
private var preDuckVolume = 1.0f
```

2. Remplacer `onDuck()` :
```kotlin
override fun onDuck() {
    preDuckVolume = _uiState.value.volume
    playerManager.currentPlayer?.volume = 0.3f
}
```

3. Remplacer `onUnduck()` :
```kotlin
override fun onUnduck() {
    playerManager.currentPlayer?.volume = preDuckVolume
    _uiState.update { it.copy(volume = preDuckVolume) }
}
```

---

### CRO-015 [P2] — onDuck() ne met pas a jour uiState.volume

**Fichier** : Corrige par CRO-014 — `onDuck()` ne doit PAS mettre a jour `uiState.volume` car le duck est temporaire. Le `preDuckVolume` stocke la vraie valeur. L'UI n'a pas besoin de montrer le volume ducke. Rien de plus a faire.

---

### CRO-016 [P2] — Audio focus GAIN apres LOSS permanent

**NE PAS CORRIGER.** C'est le comportement correct selon la spec Android.

---

### CRO-017 [P2] — registerReceiver sans flag RECEIVER_NOT_EXPORTED

**Fichier** : `AudioFocusManager.kt`

**FIX DETAILLE** :

1. Ajouter les imports :
```kotlin
import android.os.Build
import androidx.core.content.ContextCompat
```

2. Remplacer le contenu de `registerNoisyReceiver()` :
```kotlin
fun registerNoisyReceiver(context: Context) {
    if (!noisyRegistered) {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            context.registerReceiver(noisyReceiver, filter)
        }
        noisyRegistered = true
    }
}
```

NOTE : On utilise `ContextCompat.registerReceiver` qui gere le flag correctement. L'import `androidx.core.content.ContextCompat` est deja disponible via les dependances AndroidX.

---

## 6. Thread safety

### CRO-018 [P1] — Callbacks ExoPlayer executes sur le playbackThread, pas le main thread

**Fichier** : `VideoPlayerManager.kt`

**FIX DETAILLE** :

1. Ajouter l'import :
```kotlin
import android.os.Handler
import android.os.Looper
```

2. Ajouter un champ main handler :
```kotlin
private val mainHandler = Handler(Looper.getMainLooper())
```

3. Modifier `buildListener()` pour dispatcher les callbacks sur le main thread :
```kotlin
private fun buildListener(player: ExoPlayer): Player.Listener =
    object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            mainHandler.post {
                if (player !== currentPlayer) return@post
                when {
                    isCodecInitFailure(error) -> onCodecFailure?.invoke()
                    isSourceError(error)      -> onSourceError?.invoke()
                }
            }
        }
        override fun onTracksChanged(tracks: Tracks) {
            mainHandler.post {
                if (player === currentPlayer) onTracksChanged?.invoke(tracks)
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            mainHandler.post {
                if (playbackState == Player.STATE_ENDED && player === currentPlayer) {
                    onPlaybackEnded?.invoke()
                }
            }
        }
    }
```

---

### CRO-019 [P2] — Variables publiques mutables sans synchronisation

**NE PAS CORRIGER.** Apres CRO-018, tous les acces aux variables du manager se font depuis le main thread (les callbacks sont dispatchees sur main, et le ViewModel est sur main). Les references objet sont atomiques sur la JVM. Le risque est theorique.

---

## 7. Gestes conflictuels

### CRO-020 [P1] — Le pointerInput est recree a chaque changement de canSwipeDown

**Fichiers** : `GestureHandler.kt` + `PlayerScreen.kt`

**FIX DETAILLE** :

#### Etape 1 : GestureHandler.kt

Changer la signature de `gestureHandler` — `canSwipeDown` devient un lambda :
```kotlin
fun Modifier.gestureHandler(
    screenWidthPx: Float,
    screenHeightPx: Float,
    zoomScale: () -> Float,
    isSwipeEnabled: Boolean,
    canSwipeDown: () -> Boolean,   // CHANGEMENT : etait Boolean
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    // ... reste identique ...
```

Retirer `canSwipeDown` des cles du `pointerInput` :
```kotlin
): Modifier = this.pointerInput(isSwipeEnabled, screenWidthPx, screenHeightPx) {
```

Dans le bloc de dispatch du swipe, changer l'appel :
```kotlin
SwipeResult.DOWN -> if (canSwipeDown()) onSwipeDown()  // CHANGEMENT: appel de lambda
```

#### Etape 2 : PlayerScreen.kt

Changer l'appel a `gestureHandler` :
```kotlin
canSwipeDown = { uiState.previousVideo != null },  // CHANGEMENT: lambda
```

---

### CRO-021 [P2] — Pas de dead zone sur les gestes lateraux brightness/volume

**Fichier** : `GestureHandler.kt`

**FIX DETAILLE** :

Remplacer le seuil hardcode `8f` par un seuil en dp. Dans le bloc `awaitEachGesture`, avant la boucle `while`, ajouter :
```kotlin
val sideDeadZonePx = 20f * density  // 20dp dead zone for side gestures
```

Puis remplacer les deux occurrences de `totalDelta.y.absoluteValue > 8f` :

Dans `GestureZone.LEFT` :
```kotlin
if (totalDelta.y.absoluteValue > sideDeadZonePx) {
```

Dans `GestureZone.RIGHT` :
```kotlin
if (totalDelta.y.absoluteValue > sideDeadZonePx) {
```

---

### CRO-022 [P2] — Le double-tap utilise le centre de l'ecran comme pivot

**NE PAS CORRIGER.** C'est le comportement standard YouTube/TikTok et c'est intentionnel. Le double-tap ne se declenche que dans la zone CENTER (15%-85%), et le pivot a 50% de l'ecran est correct UX.

---

## 8. URIs content:// — Robustesse

### CRO-023 [P1] — queryMediaStoreByPath() utilise uri.path sur des content:// URIs

**Fichier** : `VideoRepository.kt`

**FIX DETAILLE** :

Le fix est d'ameliorer `queryMediaStoreByPath` pour extraire le vrai chemin depuis le `DATA` column de MediaStore quand `uri.path` n'est pas un chemin filesystem valide.

Remplacer le debut de `queryMediaStoreByPath` :
```kotlin
private suspend fun queryMediaStoreByPath(uri: Uri): List<VideoFile> =
    withContext(Dispatchers.IO) {
        // Try to get a real filesystem path: first from DATA column, then from uri.path
        val parentPath = resolveParentPath(uri)
        if (parentPath == null) {
            Log.d(TAG, "queryMediaStoreByPath: cannot resolve parent path")
            return@withContext emptyList()
        }
        Log.d(TAG, "queryMediaStoreByPath: parentPath=$parentPath")
        // ... reste du code identique a partir de "val result = mutableListOf<VideoFile>()" ...
```

Ajouter cette nouvelle methode privee dans VideoRepository :
```kotlin
/** Resolves the parent directory path from a URI, trying DATA column first, then uri.path. */
private fun resolveParentPath(uri: Uri): String? {
    // Strategy 1: query DATA column from MediaStore (works for media:// URIs)
    runCatching {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.Video.Media.DATA),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (dataCol >= 0) {
                    val data = cursor.getString(dataCol)
                    if (!data.isNullOrBlank()) {
                        val parent = File(data).parent
                        if (parent != null && File(parent).exists()) return parent
                    }
                }
            }
        }
    }
    // Strategy 2: try uri.path directly (works for file-path-like content:// URIs)
    val path = uri.path ?: return null
    val parent = File(path).parent ?: return null
    return if (File(parent).exists()) parent else null
}
```

---

### CRO-024 [P2] — resolveViaUriPath() : meme probleme

**Fichier** : `VideoRepository.kt`

**FIX DETAILLE** :

`resolveViaUriPath` est deja un fallback de dernier recours dans la chaine. Son comportement actuel est correct : si `uri.path` n'est pas un vrai chemin, `File(path).parentFile` n'existera pas et la methode retourne une liste vide. Pas de crash.

Ameliorer le logging pour clarifier :
```kotlin
private suspend fun resolveViaUriPath(uri: Uri): List<VideoFile> =
    withContext(Dispatchers.IO) {
        try {
            val path = uri.path ?: return@withContext emptyList()
            val parent = File(path).parentFile ?: return@withContext emptyList()
            if (!parent.exists()) {
                Log.d(TAG, "resolveViaUriPath: parent=${parent.path} does not exist, skipping")
                return@withContext emptyList()
            }
            Log.d(TAG, "resolveViaUriPath: parent=${parent.path} exists=${parent.exists()}")
            parent.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
                ?.map { it.toVideoFile() }
                ?.sortedNaturally()
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
```

---

### CRO-025 [P2] — findExternalSubtitles() ne fonctionne pas pour les content:// URIs

**Fichier** : `VideoRepository.kt`

**FIX DETAILLE** :

Ajouter un fallback SAF quand `video.path` est vide. Remplacer `findExternalSubtitles` :

```kotlin
suspend fun findExternalSubtitles(video: VideoFile): List<SubtitleFile> =
    withContext(Dispatchers.IO) {
        // Strategy 1: filesystem path (works for file:// URIs and resolved content:// URIs)
        if (video.path.isNotBlank()) {
            val result = findSubtitlesViaFilesystem(video.path)
            if (result.isNotEmpty()) return@withContext result
        }
        // Strategy 2: SAF (works for content:// URIs with tree access)
        findSubtitlesViaSaf(video)
    }

private fun findSubtitlesViaFilesystem(videoPath: String): List<SubtitleFile> {
    val videoFile = File(videoPath)
    val baseName = videoFile.nameWithoutExtension
    val parent = videoFile.parentFile ?: return emptyList()
    return parent.listFiles()
        ?.filter { f ->
            f.isFile &&
                f.extension.lowercase() in SUBTITLE_EXTENSIONS &&
                f.nameWithoutExtension.equals(baseName, ignoreCase = true)
        }
        ?.mapNotNull { f ->
            val mimeType = when (f.extension.lowercase()) {
                "srt"        -> MimeTypes.APPLICATION_SUBRIP
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                else         -> return@mapNotNull null
            }
            SubtitleFile(uri = Uri.fromFile(f), mimeType = mimeType, name = f.name)
        }
        ?: emptyList()
}

private fun findSubtitlesViaSaf(video: VideoFile): List<SubtitleFile> {
    return try {
        val doc = DocumentFile.fromSingleUri(context, video.uri) ?: return emptyList()
        val parent = doc.parentFile ?: return emptyList()
        val baseName = video.name.substringBeforeLast('.')
        parent.listFiles()
            .filter { f ->
                f.isFile &&
                    f.name?.substringAfterLast('.', "")?.lowercase() in SUBTITLE_EXTENSIONS &&
                    f.name?.substringBeforeLast('.', "")
                        .equals(baseName, ignoreCase = true)
            }
            .mapNotNull { f ->
                val ext = f.name?.substringAfterLast('.', "")?.lowercase() ?: return@mapNotNull null
                val mimeType = when (ext) {
                    "srt"        -> MimeTypes.APPLICATION_SUBRIP
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else         -> return@mapNotNull null
                }
                SubtitleFile(uri = f.uri, mimeType = mimeType, name = f.name ?: "")
            }
    } catch (_: Exception) {
        emptyList()
    }
}
```

---

### CRO-026 [P3] — isSafUri() code mort

**Fichier** : `VideoRepository.kt`

**FIX DETAILLE** :

Supprimer la methode `isSafUri()` et sa ligne :
```kotlin
fun isSafUri(uri: Uri): Boolean =
    uri.authority?.contains("documents", ignoreCase = true) == true ||
        uri.authority?.contains("com.android.externalstorage", ignoreCase = true) == true
```

---

## 9. Problemes supplementaires

### CRO-027 [P1] — VerticalPager + LaunchedEffect : double navigation possible

**Fichier** : `PlayerScreen.kt`

**FIX DETAILLE** :

Ajouter un flag `isNavigating` pour ignorer les evenements pendant la transition :

```kotlin
var isNavigating by remember { mutableStateOf(false) }

LaunchedEffect(pagerState.currentPage) {
    if (isNavigating) return@LaunchedEffect
    when (pagerState.currentPage) {
        0 -> {
            isNavigating = true
            viewModel.onSwipeDown()
            pagerState.scrollToPage(1)
            isNavigating = false
        }
        2 -> {
            isNavigating = true
            viewModel.onSwipeUp()
            pagerState.scrollToPage(1)
            isNavigating = false
        }
    }
}
```

Aussi, ajouter l'import `import androidx.compose.runtime.setValue` si pas deja present (il devrait l'etre).

---

### CRO-028 [P2] — onSwipeDown() : navigation back-forward non atomique

**Fichier** : Corrige par CRO-009 (ajout de `peekBack()` a PlaybackHistory et utilisation dans onSwipeDown avec le mutex). Rien de plus a faire.

---

### CRO-029 [P2] — MediaSession lifecycle

**NE PAS CORRIGER.** Le comportement actuel est correct pour l'usage normal (configChanges evite la recreation). Le nettoyage a la mort du processus est gere par Android.

---

### CRO-030 [P2] — screenOffReceiver enregistre sans flag RECEIVER_NOT_EXPORTED

**Fichier** : `PlayerActivity.kt`

**FIX DETAILLE** :

Remplacer :
```kotlin
registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
```
par :
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    androidx.core.content.ContextCompat.registerReceiver(
        this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
    )
} else {
    registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
}
```

---

### CRO-031 [P3] — VideoStateStore : les cles SharedPreferences ne sont pas echappees

**Fichier** : `VideoStateStore.kt`

**FIX DETAILLE** :

Utiliser un separateur `::` au lieu de `_` pour eviter les collisions :

Remplacer dans `save()` :
```kotlin
.putLong("${filename}_pos", safePosition)
.putFloat("${filename}_zoom", zoom)
.putString("${filename}_fmt", displayMode.name)
```
par :
```kotlin
.putLong("${filename}::pos", safePosition)
.putFloat("${filename}::zoom", zoom)
.putString("${filename}::fmt", displayMode.name)
```

Remplacer dans `load()` :
```kotlin
if (!prefs.contains("${filename}_pos")) return null
val pos  = prefs.getLong("${filename}_pos", 0L)
val zoom = prefs.getFloat("${filename}_zoom", 1f)
val fmt  = prefs.getString("${filename}_fmt", DisplayMode.ADAPT.name)
```
par :
```kotlin
if (!prefs.contains("${filename}::pos")) return null
val pos  = prefs.getLong("${filename}::pos", 0L)
val zoom = prefs.getFloat("${filename}::zoom", 1f)
val fmt  = prefs.getString("${filename}::fmt", DisplayMode.ADAPT.name)
```

NOTE : Ceci invalide les preferences sauvegardees existantes. C'est acceptable car l'app est en pre-release.

---

## Resume des priorites

### P0 — Corriger immediatement
| ID | Description | Statut |
|----|-------------|--------|
| CRO-005 | Singleton.close() appele par ViewModel tue le manager definitivement | [ ] |

### P1 — Corriger avant release
| ID | Description | Statut |
|----|-------------|--------|
| CRO-001 | LoadControl n'utilise pas les valeurs de buffer configurees | [ ] |
| CRO-002 | VelocityTracker recoit System.currentTimeMillis au lieu de uptimeMillis | [ ] |
| CRO-006 | onActivityDestroy() lance dans un scope potentiellement annule | [ ] |
| CRO-007 | SurfaceView reference dans le Singleton = fuite d'Activity | [ ] |
| CRO-009 | PlaybackHistory accede sans protection entre suspend points | [ ] |
| CRO-010 | triggerPeekNext() peut lancer des preloads concurrents | [ ] |
| CRO-012 | Callbacks lambda retiennent le ViewModel dans le Singleton | [ ] |
| CRO-014 | onUnduck() restaure volume a 1.0 au lieu de la valeur utilisateur | [ ] |
| CRO-017 | registerReceiver sans RECEIVER_NOT_EXPORTED (Android 14+) | [ ] |
| CRO-018 | Callbacks ExoPlayer sur playbackThread, pas main thread | [ ] |
| CRO-020 | pointerInput recree a chaque changement de canSwipeDown | [ ] |
| CRO-023 | queryMediaStoreByPath() utilise uri.path invalide sur content:// | [ ] |
| CRO-027 | VerticalPager double navigation possible | [ ] |

### P2 — Corriger dans une prochaine iteration
| ID | Description | Statut |
|----|-------------|--------|
| CRO-003 | Polling 250ms (etait 500ms) | [ ] |
| CRO-004 | VideoSurface zoomScale en lambda | [ ] |
| CRO-008 | Double appel close()/releaseAll() (corrige par CRO-005) | [x] |
| CRO-011 | swapToNext() thread safety (attenue par CRO-018 + CRO-009) | [x] |
| CRO-013 | HandlerThread lazy start | [ ] |
| CRO-015 | onDuck() uiState.volume (corrige par CRO-014) | [x] |
| CRO-016 | AUDIOFOCUS_GAIN apres LOSS permanent — NE PAS CORRIGER | [x] |
| CRO-019 | Variables publiques sans synchronisation — NE PAS CORRIGER | [x] |
| CRO-021 | Dead zone brightness/volume augmentee a 20dp | [ ] |
| CRO-022 | Double-tap pivot — NE PAS CORRIGER | [x] |
| CRO-024 | resolveViaUriPath() logging ameliore | [ ] |
| CRO-025 | findExternalSubtitles() fallback SAF pour content:// | [ ] |
| CRO-028 | onSwipeDown() non atomique (corrige par CRO-009) | [x] |
| CRO-029 | MediaSession lifecycle — NE PAS CORRIGER | [x] |
| CRO-030 | screenOffReceiver RECEIVER_NOT_EXPORTED | [ ] |

### P3 — Nice to have
| ID | Description | Statut |
|----|-------------|--------|
| CRO-026 | isSafUri() code mort — supprimer | [ ] |
| CRO-031 | SharedPreferences cles avec separateur :: | [ ] |

---

## Conclusion

Le projet est fonctionnel et l'architecture MVVM est globalement bien structuree. Les points les plus critiques sont :

1. **CRO-005** : Le pattern Singleton + close() est fondamentalement casse. C'est le seul P0 car il peut provoquer des crashes en production.

2. **La frontiere thread safety entre le playbackThread et le main thread** (CRO-018, CRO-011) : Les callbacks ExoPlayer arrivent sur un thread different de celui ou le ViewModel les traite. Ca fonctionne aujourd'hui grace aux `viewModelScope.launch` et aux CAS de `StateFlow`, mais c'est fragile.

3. **Les race conditions de navigation** (CRO-009, CRO-010, CRO-027) : Des swipes rapides peuvent corrompre l'etat de PlaybackHistory. Un Mutex ou une serialisation des operations de navigation est necessaire.

4. **Le volume duck/unduck** (CRO-014) : Bug UX visible — le volume utilisateur est perdu apres un duck.
