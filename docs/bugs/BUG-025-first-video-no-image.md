# BUG-025 — Première vidéo ouverte via "Ouvrir avec" : son sans image

## Statut
`FIXED`

## Symptôme
Quand l'app est lancée via "Ouvrir avec", la première vidéo jouait le son
correctement mais l'écran restait noir. Tout rentrait dans l'ordre dès le premier
swipe.

## Cause racine
Race condition dans `onIntentReceived` :

1. `_uiState.update { currentVideo = startVideo }` est appelé en premier (StateFlow).
2. Compose schedule une recomposition → `PlayerScreen` se compose avec
   `playerA = viewModel.currentPlayer`.
3. `startPlayback(startVideo)` s'exécute juste après et appelle `preparePlayer()`
   qui affecte `playerManager.currentPlayer = player`.

**Mais** : la recomposition Compose peut se produire AVANT que `preparePlayer`
ne s'exécute (selon les suspensions coroutines). Dans ce cas,
`viewModel.currentPlayer` est null au moment de la composition initiale →
`playerA = null` → `VideoSurface` crée le `SurfaceView` sans player attaché →
ExoPlayer joue l'audio mais ne rend rien à l'écran.

Le `SurfaceView` ne se met pas à jour automatiquement ensuite (pas de mécanisme
d'observation du player initial dans PlayerScreen), jusqu'au premier swipe qui
force une réaffectation.

## Fix appliqué
`PlayerScreen.kt` : ajout d'un `LaunchedEffect(currentPlayerState)` qui affecte
le player courant à la surface frontale si celle-ci est null :

```kotlin
val currentPlayerForInit by viewModel.currentPlayerState.collectAsState()
LaunchedEffect(currentPlayerForInit) {
    val p = currentPlayerForInit ?: return@LaunchedEffect
    if (aIsFront && playerA == null) playerA = p
    else if (!aIsFront && playerB == null) playerB = p
}
```

La condition `== null` garantit que ce LaunchedEffect ne perturbe pas la logique
ping-pong des swipes (la surface frontale n'est jamais null pendant un swipe).

## Fichiers modifiés
- `app/src/main/java/com/swipeplayer/ui/screen/PlayerScreen.kt`
