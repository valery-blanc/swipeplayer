# BUG-024 — Swipe haut : carré noir au lieu de la vidéo suivante

## Statut
`FIXED`

## Symptôme
Au swipe vers le haut, il arrivait qu'un carré noir apparaisse à la place de la
vidéo suivante pendant l'animation, notamment si l'utilisateur swipait rapidement
avant que le préchargement ne soit terminé.

## Cause racine
Quand le preload de `nextPlayer` n'est pas encore terminé au moment du commit du
swipe, la surface arrière (`playerB` ou `playerA`) est null. Le `SurfaceView`
associé est créé mais sans player — il s'affiche noir.

## Fix appliqué
**`onSwipeUp`** : avant de démarrer l'animation, vérifie si la surface arrière a
un player. Si non, attend jusqu'à 2 secondes que `nextPlayerState` émette un
player non-null, puis l'affecte explicitement à la surface arrière :

```kotlin
val backIsReady = if (aIsFront) playerB != null else playerA != null
if (!backIsReady) {
    val loaded = withTimeoutOrNull(2_000L) {
        viewModel.nextPlayerState.first { it != null }
    }
    if (loaded != null) {
        if (aIsFront) playerB = loaded else playerA = loaded
    }
}
```

Le même mécanisme est appliqué à `onSwipeDown` pour `prevPlayer`.

## Fichiers modifiés
- `app/src/main/java/com/swipeplayer/ui/screen/PlayerScreen.kt`
