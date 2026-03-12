# BUG-013 — Surface GL non détachée avant swap

**Statut** : FIXED
**Sévérité** : CRITIQUE
**Découvert via** : Code review 2026-03-12 (CR-002)

## Symptôme

Artifacts visuels (frames de l'ancienne vidéo) lors du swipe. Possible fuite GL.

## Cause racine

`swapToNext()` attache le SurfaceView au nouveau player sans l'avoir détaché
de l'ancien. L'ancien player peut continuer à rendre des frames.

## Fix appliqué

Dans `swapToNext()` : appel de `oldCurrent.clearVideoSurfaceView(sv)` AVANT
`newCurrent.setVideoSurfaceView(sv)`. Résout aussi le cas VideoSurface.update()
en stockant une référence à la surface active.

## Section spec impactée

swipeplayer-specs.md §8 (Mémoire : clearVideoSurfaceView avant release)
