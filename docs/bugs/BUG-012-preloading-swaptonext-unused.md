# BUG-012 — swapToNext() jamais appelé : preloading non-fonctionnel

**Statut** : FIXED
**Sévérité** : CRITIQUE
**Découvert via** : Code review 2026-03-12 (CR-001)

## Symptôme

Chaque swipe crée un nouveau ExoPlayer from scratch. Les transitions vidéo sont lentes.
La cible performance < 300ms n'est pas atteinte.

## Cause racine

`VideoPlayerManager.swapToNext()` existe mais n'est jamais appelée.
`PlayerViewModel.startPlayback()` appelle toujours `playerManager.preparePlayer()`,
ignorant le `nextPlayer` préchargé par `triggerPeekNext()`.

## Fix appliqué

- `VideoPlayerManager` : ajout de `nextPlayerVideo`, `currentSurfaceView` ;
  `swapToNext()` sans paramètre (utilise la surface stockée) ; met à jour `_currentPlayerState`.
- `PlayerViewModel.startPlayback()` : vérifie `nextPlayerVideo?.uri == video.uri`,
  appelle `swapToNext()` si match, sinon crée un nouveau player.
- `VideoPlayerManager.preloadNext()` : stocke `nextPlayerVideo`.

## Section spec impactée

swipeplayer-specs.md §8 (Pré-chargement), §14 (Performance : transition < 300ms)
