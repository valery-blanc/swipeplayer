# Code Review — SwipePlayer

> **Date** : 2026-03-12
> **Périmètre** : Tous les fichiers Kotlin de `app/src/main/java/com/swipeplayer/` + `AndroidManifest.xml`
> **Base** : specs `docs/specs/swipeplayer-specs.md`, `CLAUDE.md`, `TASKS.md`
> **Règle** : NE PAS corriger lors de cette revue — rapport uniquement.

---

## Légende des sévérités

| Niveau    | Critère |
|-----------|---------|
| CRITIQUE  | Fonctionnalité cassée, crash probable, fuite mémoire grave |
| MAJEUR    | Régression fonctionnelle, non-conformité aux specs, comportement incorrect visible |
| MINEUR    | Qualité de code, micro-optimisation, commentaire trompeur, UX dégradée |

---

## Résumé exécutif

| Sévérité | Nb |
|----------|----|
| CRITIQUE | 3  |
| MAJEUR   | 8  |
| MINEUR   | 12 |
| **Total**| **23** |

---

## CRITIQUE

---

### CR-001 — `swapToNext()` n'est jamais appelé : le preloading est non-fonctionnel

**Fichiers** : `VideoPlayerManager.kt`, `PlayerViewModel.kt:450-478`
**Sévérité** : CRITIQUE

`VideoPlayerManager.swapToNext()` existe et est correctement implémentée, mais elle n'est appelée **nulle part** dans le ViewModel. À chaque swipe, la chaîne d'appels est :

```
onSwipeUp() → switchToVideo() → startPlayback() → playerManager.preparePlayer(video, preloadOnly=false)
```

`preparePlayer()` crée un **nouveau ExoPlayer from scratch**, ignorant totalement `nextPlayer` préchargé par `triggerPeekNext()`. Résultat :

- Le `peekNext()` / `preloadNext()` ne sert à rien : la vidéo est re-bufferisée depuis zéro à chaque swipe.
- La cible de performance `< 300ms` pour la transition swipe est probablement non atteinte.
- `nextPlayer` est créé, consomme de la mémoire et du réseau, puis est libéré sans avoir joué.

**Fix attendu** : Dans `onSwipeUp()` (et `onSwipeDown()` le cas échéant), détecter si `nextPlayer` correspond à la vidéo cible et appeler `playerManager.swapToNext(surfaceView)` au lieu de `preparePlayer()`.

---

### CR-002 — Surface non détachée de l'ancien player avant le swap

**Fichiers** : `VideoPlayerManager.kt:139-153`
**Sévérité** : CRITIQUE

Dans `swapToNext()` :

```kotlin
attachSurface(newCurrent, surfaceView)   // SurfaceView attaché au nouveau player
newCurrent.play()
currentPlayer = newCurrent
...
releasePlayer(oldCurrent, surfaceView = null)  // Release SANS clearVideoSurfaceView !
```

`attachSurface(newCurrent, sv)` appelle `newCurrent.setVideoSurfaceView(sv)`, mais ExoPlayer ne détache pas automatiquement le SurfaceView de l'ancien player. L'ancien player conserve sa référence à `sv` et peut continuer à rendre des frames dessus jusqu'à ce que `release()` soit appelé (de façon asynchrone). Conséquences :

- Artifacts visuels : frames résiduels de l'ancienne vidéo flashent sur la surface.
- Fuite surface GL : si `release()` est appelé avec la surface encore attachée, le contexte GL peut ne pas être correctement nettoyé.

Les specs indiquent explicitement : `player.clearVideoSurfaceView(sv)` avant `player.release()`.

**Fix attendu** : Appeler `detachSurface(oldCurrent, surfaceView)` AVANT `attachSurface(newCurrent, surfaceView)`, puis `releasePlayer(oldCurrent, surfaceView = null)`.

---

### CR-003 — `onSourceErrorDetected()` : logique d'historique défectueuse + crash potentiel

**Fichier** : `PlayerViewModel.kt:556-573`
**Sévérité** : CRITIQUE

```kotlin
history.init(
    history.navigateForward().let { history.current ?: return@launch },
    newPlaylist,
)
```

Problèmes multiples sur cette ligne :

1. **`history.navigateForward()` est appelé avec l'ancien playlist** encore chargé dans `history`. Si l'on est à la fin de l'historique, `navigateForward()` appelle `pickRandom()` sur la **vieille playlist** (qui contient encore le fichier supprimé).

2. La valeur de retour de `navigateForward()` est ignorée : `.let { history.current ... }` récupère `history.current` APRÈS l'avancement, ce qui est toujours la même valeur que la valeur retournée. La construction `.let { ... }` est inutile et trompeuse.

3. Si `newPlaylist` est vide (tous les fichiers supprimés), `pickRandom()` appelle `playlist.first()` sur l'ancien playlist (vide si un seul fichier) et crashe avec `NoSuchElementException`.

4. Après `history.init(nextVideo, newPlaylist)`, `history.current` pointe sur `nextVideo` qui peut ne pas être dans `newPlaylist` (cas edge de la race condition ci-dessus).

---

## MAJEUR

---

### CR-004 — Erreurs du `nextPlayer` déclenchent des actions sur la vidéo courante

**Fichier** : `VideoPlayerManager.kt:216-219`
**Sévérité** : MAJEUR

```kotlin
override fun onPlayerError(error: PlaybackException) {
    when {
        isCodecInitFailure(error) -> onCodecFailure?.invoke()
        isSourceError(error)      -> onSourceError?.invoke()
    }
}
```

`onPlayerError` n'a **pas** la vérification `player === currentPlayer` (contrairement à `onTracksChanged` et `onPlaybackStateChanged`). Si `nextPlayer` (la vidéo pré-chargée) rencontre une erreur, `onCodecFailure` ou `onSourceError` est invoqué, ce qui provoque un skip ou un toast intempestif sur la **vidéo courante** — qui jouait correctement.

---

### CR-005 — `ContentUriNoAccess` émis pour TOUTES les playlists à 1 vidéo

**Fichier** : `PlayerViewModel.kt:116-131`
**Sévérité** : MAJEUR

```kotlin
val singleFileMode = playlist.size == 1 ||
    (playlist.size == 1 && playlist.first().uri == uri &&
        !videoRepository.isMediaStoreUri(uri) &&
        !videoRepository.isSafUri(uri))
```

La deuxième condition est entièrement incluse dans la première (`playlist.size == 1`). La variable vaut donc toujours `playlist.size == 1`. La condition suivante :

```kotlin
error = if (singleFileMode && playlist.size == 1) PlayerError.ContentUriNoAccess else null
```

émet `ContentUriNoAccess` pour **toutes** les playlists d'un seul fichier — y compris quand l'utilisateur a légitimement un répertoire avec une seule vidéo. Selon les specs, `ContentUriNoAccess` ne doit s'afficher que pour les URI `content://` sans accès au répertoire.

---

### CR-006 — `PlayerConfig.kt` : KDoc incohérent avec le code

**Fichier** : `PlayerConfig.kt:84-93`
**Sévérité** : MAJEUR

Le KDoc de `renderersFactory()` dit :
> HW+ mode: hardware decoding only, no software fallback.
> `setEnableDecoderFallback(false)` — no software fallback

Mais le code fait :
```kotlin
.setEnableDecoderFallback(true)   // fallback SW activé
```

La spec a été correctement révisée (BUG-005) pour activer le fallback logiciel, mais le commentaire n'a pas été mis à jour. Cela contredit la documentation, induit les futurs développeurs en erreur, et contredit la constante `EXTENSION_RENDERER_MODE_OFF` juste en dessous.

---

### CR-007 — `PinchZoomHandler` : commentaire dit "4x max" au lieu de 50x

**Fichier** : `PinchZoomHandler.kt:9`
**Sévérité** : MAJEUR

```kotlin
 * Scale = ... clamped to [PlayerConfig.MIN_ZOOM_SCALE]..[PlayerConfig.MAX_ZOOM_SCALE] (1x..4x).
```

`PlayerConfig.MAX_ZOOM_SCALE = 50f`. Le commentaire `(1x..4x)` est faux. De plus, le comment dans `PlayerConfig.kt:159` du TASKS.md dit "`MAX_ZOOM_SCALE → 50f`" (FEAT-001), confirmant que 50x est la valeur correcte. Ce commentaire erroné peut conduire à une régression si un développeur aligne la valeur sur le commentaire.

---

### CR-008 — Vélocité minimum du swipe non implémentée

**Fichier** : `SwipeDetector.kt:28`
**Sévérité** : MAJEUR

```kotlin
fun detect(startY: Float, currentY: Float, @Suppress("UNUSED_PARAMETER") velocityY: Float): SwipeResult?
```

La spec (CLAUDE.md et swipeplayer-specs.md §7) indique : *"Swipe vidéo nécessite ≥ 80dp de déplacement + vélocité minimum"*. La vélocité est passée en paramètre mais est explicitement supprimée (`@Suppress("UNUSED_PARAMETER")`). Seul le déplacement est vérifié. Un glissement très lent de 80dp déclenche le changement de vidéo, ce qui n'est pas le comportement attendu.

---

### CR-009 — Sous-titres externes non implémentés malgré TASK-032 `[x]`

**Fichier** : `PlayerViewModel.kt:579-611`
**Sévérité** : MAJEUR

TASK-032 est marqué `[x]` dans TASKS.md. La spec (§4 et §7) indique la détection des fichiers `.srt`/`.ass`/`.ssa` externes dans le même répertoire. Or `updateTracks()` ne lit que `player.currentTracks` (pistes intégrées). Il n'y a **aucun code** qui :
- scanne le répertoire pour les fichiers sous-titres externes,
- construit un `MediaItem` avec sous-titres via `MediaItem.SubtitleConfiguration`,
- ajoute ces pistes à `subtitleTracks`.

La `SettingsSheet` affiche donc uniquement les sous-titres intégrés. Ce point est marqué done à tort.

---

### CR-010 — Race condition lors de swipes rapides dans `startPlayback()`

**Fichier** : `PlayerViewModel.kt:450-467`
**Sévérité** : MAJEUR

```kotlin
val oldPlayer = playerManager.currentPlayer
val newPlayer = playerManager.preparePlayer(video, preloadOnly = false)  // suspend
oldPlayer?.let {
    if (it !== newPlayer) viewModelScope.launch { playerManager.releasePlayer(it, null) }
}
```

`preparePlayer()` est une `suspend` fonction qui switch sur `Dispatchers.Main`. Entre la capture de `oldPlayer` et le retour de `preparePlayer()`, un autre swipe peut avoir appelé `startPlayback()` une seconde fois, modifiant `playerManager.currentPlayer`. Les deux appels libèrent le même player en parallèle, ce qui peut provoquer un appel à `player.release()` sur un player déjà libéré → exception `IllegalStateException`.

---

### CR-011 — UX agressive : renvoi systématique vers Paramètres pour `MANAGE_EXTERNAL_STORAGE`

**Fichier** : `PlayerActivity.kt:206-222`
**Sévérité** : MAJEUR

À chaque ouverture d'une vidéo sur Android 11+, si `MANAGE_EXTERNAL_STORAGE` n'est pas accordé, l'app :
1. Affiche un Toast,
2. Envoie l'utilisateur vers l'écran Paramètres → Autorisations,
3. L'empêche de jouer immédiatement.

Cette permission est optionnelle (dernier fallback de la chaîne de listing). L'app devrait tenter MediaStore et SAF en premier, puis demander cette permission uniquement si aucune stratégie n'a fonctionné. Actuellement, même si MediaStore peut répondre, l'utilisateur est bloqué en attendant une permission dont il n'a peut-être pas besoin.

---

## MINEUR

---

### CR-012 — `ProgressBar.kt` : `isDragging` — sémantique ambiguë

**Fichier** : `ProgressBar.kt:39`
**Sévérité** : MINEUR

```kotlin
var isDragging by remember { mutableFloatStateOf(-1f) }
```

`isDragging` encode deux informations dans une seule variable : booléen (`>= 0` = en cours de drag) et valeur flottante (fraction du seek). C'est une anti-pattern : deux sémantiques dans une variable, lisibilité dégradée. `-1f` comme sentinel est fragile.

---

### CR-013 — `VideoRepository.kt` : `withContext(Dispatchers.IO)` imbriqués inutiles

**Fichiers** : `VideoRepository.kt:82, 110, 192, 222, 276, 304`
**Sévérité** : MINEUR

`listViaFile()`, `queryMediaStoreByBucketId()`, `resolveViaDocumentFile()`, etc. sont des méthodes `suspend` avec leur propre `withContext(Dispatchers.IO)`. Elles sont appelées depuis `listViaContentFallbackChain()` ou `listVideosInDirectory()` qui sont **déjà** dans un `withContext(Dispatchers.IO)`. Ces imbrications produisent des transitions de contexte superflues (allocation d'une `Continuation` Kotlin supplémentaire par niveau). La performance est très légèrement dégradée pour des fichiers volumineux.

---

### CR-014 — `VideoSurface.kt` : `setVideoSurfaceView()` dans `AndroidView.update` provoque des réattachements inutiles

**Fichier** : `VideoSurface.kt:148-150`
**Sévérité** : MINEUR

```kotlin
update = { sv ->
    player?.setVideoSurfaceView(sv)   // appelé à CHAQUE recomposition
},
```

`AndroidView.update` est invoqué à chaque recomposition (ex: changement de `displayMode`, `zoomScale`, ou `positionMs` — qui se met à jour 2× par seconde). `setVideoSurfaceView()` sur ExoPlayer provoque une réattachement interne qui peut générer un bref flash vidéo ou un freeze d'un frame. Ce n'est nécessaire que si le `player` a changé.

---

### CR-015 — Buffer progress non affiché dans la `ProgressBar`

**Fichier** : `ProgressBar.kt`
**Sévérité** : MINEUR

`PlayerUiState.bufferedPositionMs` est correctement mis à jour dans `startPositionPolling()`, mais il n'est **pas passé** à `ProgressBar` et n'est pas visualisé. La spec §6.5 demande : *"Buffer : gris #80FFFFFF"*. Le Material3 `Slider` ne supporte pas nativement deux pistes (progression + buffer) ; une implémentation Canvas personnalisée serait nécessaire.

---

### CR-016 — `BrightnessControl` et `VolumeControl` : duplication de code

**Fichiers** : `BrightnessControl.kt`, `VolumeControl.kt`
**Sévérité** : MINEUR

Ces deux composables sont quasi-identiques (même structure, même dimensions `6.dp` / `120.dp`, même `AnimatedVisibility(tween(100))`). La seule différence est la couleur et l'icône. Un composable générique `VerticalIndicatorBar(value, color, icon, ...)` éviterait cette duplication.

---

### CR-017 — `onHorizontalSeekEnd()` et `onHorizontalSeekCancel()` : logique dupliquée

**Fichier** : `PlayerViewModel.kt:340-368`
**Sévérité** : MINEUR

Les deux méthodes partagent exactement le même bloc de reprise :
```kotlin
player?.setSeekParameters(SeekParameters.EXACT)
if (wasPlayingBeforeHorizontalSeek) {
    player?.play()
    startPositionPolling()
    _uiState.update { it.copy(isPlaying = true) }
}
```
Ce bloc apparaît deux fois identique. Une fonction privée `resumeAfterSeek()` éliminerait la duplication.

---

### CR-018 — `VideoStateStore` : clés SharedPreferences sans séparateur garanti unique

**Fichier** : `VideoStateStore.kt:38-41`
**Sévérité** : MINEUR

Les clés sont construites comme `"${filename}_pos"`, `"${filename}_zoom"`, `"${filename}_fmt"`. Un fichier nommé `video_pos` génère la clé `video_pos_pos`, qui est différente de `video_pos.mp4_pos`. Pas de collision réelle dans ce cas, mais si deux fichiers ont pour noms `a_pos` et `a_pos.mp4`, le fichier `a_pos.mp4` utilisera la clé `a_pos.mp4_pos` tandis que `a_pos` utilise `a_pos_pos` — pas de conflit. En revanche, si le nom contient les chaînes `_pos`, `_zoom`, ou `_fmt` en position finale (ex: un fichier nommé `video_zoom`), les clés `video_zoom_pos`, `video_zoom_zoom` etc. sont inhabituelles mais correctes. Le risque de collision réelle est quasi-nul en pratique.

---

### CR-019 — `PlaybackHistory.pickRandom()` : allocation à chaque appel

**Fichier** : `PlaybackHistory.kt:148-161`
**Sévérité** : MINEUR

`pickRandom()` crée un `IdentityHashMap` et parcourt toute la playlist (`filter`) à chaque appel. Pour de grandes playlists (>500 fichiers) et un historique long, cela devient non-négligeable. Un `seenSet` persistant mis à jour incrémentalement serait plus efficace.

---

### CR-020 — `VideoRepository.kt` : création de `VideoFile` dupliquée

**Fichiers** : `VideoRepository.kt:88-96` et `311-319`
**Sévérité** : MINEUR

Le bloc de création de `VideoFile` à partir d'un objet `File` est dupliqué entre `listViaFile()` et `resolveViaUriPath()`. Une extension privée `File.toVideoFile(): VideoFile` éliminerait la duplication.

---

### CR-021 — Toast "Codec non supporté" sans accent

**Fichier** : `PlayerViewModel.kt:545`
**Sévérité** : MINEUR

```kotlin
_toastEvents.emit("Codec non supporte - passage a la video suivante")
```

La spec indique : *"Toast `Codec non supporté`"*. Le message émis n'a pas d'accents (`supporte` au lieu de `supporté`, `video` au lieu de `vidéo`). Visible pour l'utilisateur.

---

### CR-022 — `CLAUDE.md` : liste des vitesses non à jour

**Fichier** : `CLAUDE.md`
**Sévérité** : MINEUR

`CLAUDE.md` indique encore :
> Speed options: 0.25x, 0.5x, 0.75x, 1x, 1.25x, 1.5x, 1.75x, 2x, 3x, 4x

Mais `swipeplayer-specs.md §6.6` et `SpeedSelector.kt` ont été mis à jour (FEAT-007) avec la liste :
`0.25x, 0.33x, 0.5x, 0.75x, 1x, 1.5x, 2x, 3x, 4x`
`CLAUDE.md` n'a pas été synchronisé après FEAT-007.

---

### CR-023 — `TopBar` : shadow du titre non implémentée

**Fichier** : `TopBar.kt`
**Sévérité** : MINEUR

La spec §6.1 mentionne un shadow sur le titre. `TopBar.kt` n'applique pas de `shadow` sur le `Text` du titre. Très mineur visuellement sur fond semi-transparent.

---

## Tableau récapitulatif

| ID      | Fichier(s)                                   | Catégorie    | Sévérité |
|---------|----------------------------------------------|--------------|----------|
| CR-001  | `VideoPlayerManager.kt`, `PlayerViewModel.kt:450` | Performance / Fonctionnel | CRITIQUE |
| CR-002  | `VideoPlayerManager.kt:139`                  | Mémoire / Visuel | CRITIQUE |
| CR-003  | `PlayerViewModel.kt:556`                     | Crash / Logique | CRITIQUE |
| CR-004  | `VideoPlayerManager.kt:216`                  | Comportement incorrect | MAJEUR |
| CR-005  | `PlayerViewModel.kt:116`                     | Conformité specs | MAJEUR |
| CR-006  | `PlayerConfig.kt:84`                         | Documentation trompeuse | MAJEUR |
| CR-007  | `PinchZoomHandler.kt:9`                      | Documentation trompeuse | MAJEUR |
| CR-008  | `SwipeDetector.kt:28`                        | Conformité specs | MAJEUR |
| CR-009  | `PlayerViewModel.kt:579`                     | Fonctionnalité manquante | MAJEUR |
| CR-010  | `PlayerViewModel.kt:450`                     | Race condition | MAJEUR |
| CR-011  | `PlayerActivity.kt:206`                      | UX / Permissions | MAJEUR |
| CR-012  | `ProgressBar.kt:39`                          | Qualité code | MINEUR |
| CR-013  | `VideoRepository.kt` (multiple)              | Performance | MINEUR |
| CR-014  | `VideoSurface.kt:148`                        | Performance | MINEUR |
| CR-015  | `ProgressBar.kt`                             | Conformité specs | MINEUR |
| CR-016  | `BrightnessControl.kt`, `VolumeControl.kt`   | Duplication | MINEUR |
| CR-017  | `PlayerViewModel.kt:340-368`                 | Duplication | MINEUR |
| CR-018  | `VideoStateStore.kt:38`                      | Robustesse | MINEUR |
| CR-019  | `PlaybackHistory.kt:148`                     | Performance | MINEUR |
| CR-020  | `VideoRepository.kt:88,311`                  | Duplication | MINEUR |
| CR-021  | `PlayerViewModel.kt:545`                     | Qualité UX | MINEUR |
| CR-022  | `CLAUDE.md`                                  | Documentation | MINEUR |
| CR-023  | `TopBar.kt`                                  | Conformité specs | MINEUR |

---

## Priorités de correction recommandées

### Phase 1 — Correctifs bloquants (avant prochaine session de test)

1. **CR-001** : Implémenter l'utilisation de `swapToNext()` dans le ViewModel pour honorer le preloading.
2. **CR-002** : Appeler `detachSurface(oldCurrent, sv)` avant `attachSurface(newCurrent, sv)` dans `swapToNext()`.
3. **CR-003** : Réécrire la logique `onSourceErrorDetected()` en initialisant l'historique avec `newPlaylist` avant de naviguer.
4. **CR-004** : Ajouter `if (player !== currentPlayer) return` dans `onPlayerError` de `buildListener()`.

### Phase 2 — Correctifs de conformité aux specs

5. **CR-005** : Distinguer "1 vidéo dans le répertoire" de "content:// sans accès" pour l'erreur `ContentUriNoAccess`.
6. **CR-008** : Implémenter la vérification de la vélocité minimum dans `SwipeDetector.detect()`.
7. **CR-009** : Implémenter la détection des sous-titres externes (`.srt`/`.ass`/`.ssa`) dans `updateTracks()`.

### Phase 3 — Qualité et documentation

8. **CR-006** + **CR-007** : Corriger les KDoc incohérents dans `PlayerConfig.kt` et `PinchZoomHandler.kt`.
9. **CR-010** : Ajouter un mutex ou vérification de concurrence dans `startPlayback()`.
10. **CR-011** : Reporter la demande de `MANAGE_EXTERNAL_STORAGE` après échec des autres stratégies.
11. **CR-014** : Mémoriser le player précédent dans `AndroidView` et n'appeler `setVideoSurfaceView` que si le player change.
12. **CR-015** : Implémenter un Canvas custom pour visualiser le buffer dans `ProgressBar`.

### Phase 4 — Code quality (mineurs restants)

13. **CR-012** : Remplacer `isDragging: Float` par deux variables distinctes dans `ProgressBar`.
14. **CR-016** : Extraire `VerticalIndicatorBar` commun à `BrightnessControl` et `VolumeControl`.
15. **CR-017** : Extraire `resumeAfterSeek()` dans `PlayerViewModel`.
16. **CR-019** : Optimiser `pickRandom()` dans `PlaybackHistory` (éviter IdentityHashMap + filter à chaque appel).
17. **CR-020** : Extraire `File.toVideoFile()` dans `VideoRepository`.
18. **CR-021** : Corriger les accents manquants dans le toast "Codec non supporté".
19. **CR-022** : Mettre à jour la liste des vitesses dans `CLAUDE.md`.
20. **CR-023** : Ajouter le shadow sur le titre dans `TopBar`.
