# FEAT-003 — Passage automatique à la vidéo suivante en fin de lecture

## Statut : DONE

## Contexte

Quand une vidéo se termine, l'app s'arrête sur la dernière frame. L'utilisateur
doit swiper manuellement pour passer à la suivante. Le comportement attendu est
un avancement automatique.

## Comportement attendu

| Situation | Action |
|-----------|--------|
| Vidéo terminée, playlist > 1 vidéo | Passer à une vidéo aléatoire (équivalent swipe up) |
| Vidéo terminée, mode fichier unique (1 vidéo) | Remettre à zéro et relire depuis le début |

## Spécification technique

### Détection de fin
`Player.Listener.onPlaybackStateChanged(state)` avec `state == Player.STATE_ENDED`.

### VideoPlayerManager
Ajouter callback `var onPlaybackEnded: (() -> Unit)? = null`.
Dans `buildListener()`, override `onPlaybackStateChanged` :
```kotlin
override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_ENDED && player === currentPlayer) {
        onPlaybackEnded?.invoke()
    }
}
```

### PlayerViewModel
Dans `init` : `playerManager.onPlaybackEnded = { onVideoEnded() }`.
```kotlin
private fun onVideoEnded() {
    if (_uiState.value.isSwipeEnabled) {
        onSwipeUp()
    } else {
        // Single file: seek to beginning and replay
        playerManager.currentPlayer?.let { player ->
            player.seekTo(0)
            player.play()
        }
    }
}
```

## Impact sur l'existant

- `VideoPlayerManager.kt` : ajout callback + listener override
- `PlayerViewModel.kt` : `onVideoEnded()` + wire dans `init` + `onCleared`
