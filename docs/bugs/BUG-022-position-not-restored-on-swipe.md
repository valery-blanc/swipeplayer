# BUG-022 — Position de lecture non restaurée au swipe vers le haut

## Statut
`FIXED`

## Symptôme
Lors du swipe vers le haut, la nouvelle vidéo commence toujours depuis le début
même si l'utilisateur en avait déjà regardé une partie précédemment.

## Cause racine (double)

### 1. onSwipeDown() ne sauvegarde pas la position
`onSwipeDown()` navigait vers la vidéo précédente sans appeler `saveCurrentState()`
sur la vidéo courante. La position de cette vidéo était donc perdue. Lors d'un
swipe vers le haut ultérieur revenant sur cette vidéo, `videoStateStore.load()`
ne trouvait aucune position sauvegardée (ou une valeur ancienne) et relançait
depuis 0.

### 2. Seek appliqué après play() sur le player préchargé
Pour le chemin `noSurfaceSwap` (swipe haut ping-pong), `swapPlayersNoSurface()`
appelait `player.play()` depuis la position 0, puis `startPlayback()` appelait
`player.seekTo(savedPosition)` après coup. Le seek arrivait après le début de
la lecture, risquant un bref affichage de la position 0.

## Fix appliqué

### Fix 1 — PlayerViewModel.kt : save dans onSwipeDown()
```kotlin
fun onSwipeDown() {
    ...
    navigationMutex.withLock {
        history.current?.let { saveCurrentState(it) }  // ← ajouté
        val prev = history.navigateBack() ?: return@withLock
        ...
    }
}
```

### Fix 2 — PlayerViewModel.kt : pre-seek avant swapPlayersNoSurface()
```kotlin
// Pre-seek AVANT le swap pour que play() démarre à la bonne position
if (saved != null && saved.positionMs > 0L &&
        playerManager.nextPlayerVideo?.uri == video.uri) {
    playerManager.nextPlayer?.seekTo(saved.positionMs)
}
val swapped = ...swapPlayersNoSurface()...
```

Pour le chemin fresh-prepare, le seek est appliqué sur `newPlayer` directement
(pas sur `playerManager.currentPlayer` qui n'est pas encore mis à jour).

## Fichiers modifiés
- `app/src/main/java/com/swipeplayer/ui/PlayerViewModel.kt`
