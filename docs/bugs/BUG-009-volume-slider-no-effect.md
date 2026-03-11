# BUG-009 — Slider de volume visible mais sans effet sur le son

**Statut :** FIXED
**Priorité :** P1 — contrôle de volume inutilisable

## Symptôme

Le swipe vertical sur la zone droite de l'écran affiche bien la barre de volume,
et la valeur dans la barre change, mais le volume audio réel de la vidéo ne
change pas.

## Étapes de reproduction

1. Ouvrir une vidéo en lecture
2. Swiper verticalement vers le bas sur la zone droite de l'écran
3. La barre de volume apparaît et descend visuellement
4. → Le son reste au même niveau, aucun changement audio

## Analyse de la cause racine

`PlayerViewModel.onVolumeDelta()` ne fait que mettre à jour l'état UI :

```kotlin
fun onVolumeDelta(delta: Float) {
    val cur = _uiState.value.volume
    _uiState.update { it.copy(volume = (cur + delta).coerceIn(0f, 1f), ...) }
    // <- aucun appel a player.volume ou AudioManager
}
```

La valeur `_uiState.volume` contrôle uniquement l'affichage de la barre
(VolumeControl composable). Elle n'est jamais appliquée au player ExoPlayer
ni à AudioManager.

## Section spec impactée

- **Gesture Zones** : "Right 15%: vertical swipe = volume control (AudioManager.STREAM_MUSIC)"
- La spec mentionne AudioManager, mais contrôler le volume via ExoPlayer
  (`player.volume = 0f..1f`) est plus approprié pour un player vidéo.

## Correction

Dans `onVolumeDelta()` et `onVolumeChange()`, appliquer aussi le volume au
player courant : `playerManager.currentPlayer?.volume = newVolume`

Fichier : PlayerViewModel.kt
