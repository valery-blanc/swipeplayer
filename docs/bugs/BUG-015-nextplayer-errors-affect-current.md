# BUG-015 — Erreurs nextPlayer déclenchent actions sur currentPlayer

**Statut** : FIXED
**Sévérité** : MAJEUR
**Découvert via** : Code review 2026-03-12 (CR-004)

## Symptôme

Si la vidéo pré-chargée (nextPlayer) a une erreur codec ou fichier manquant,
un skip intempestif ou un toast incorrect s'affiche sur la vidéo courante.

## Cause racine

`buildListener()` dans `VideoPlayerManager` : `onPlayerError` manquait le
filtre `player === currentPlayer` présent dans `onTracksChanged` et
`onPlaybackStateChanged`.

## Fix appliqué

Ajout de `if (player !== currentPlayer) return` en début de `onPlayerError`.

## Section spec impactée

swipeplayer-specs.md §4 (Gestion des erreurs de décodage)
