# BUG-014 — onSourceErrorDetected() : logique d'historique défectueuse

**Statut** : FIXED
**Sévérité** : CRITIQUE
**Découvert via** : Code review 2026-03-12 (CR-003)

## Symptôme

Quand un fichier est supprimé pendant la lecture, le skip peut crasher ou
naviguer vers une mauvaise vidéo.

## Cause racine

`history.navigateForward()` était appelé sur l'ancien historique (avec le
fichier supprimé encore dedans), puis `history.init()` était rappelé avec
un mauvais startVideo potentiellement absent du newPlaylist.

## Fix appliqué

Réécriture : filtrage du playlist → si vide, erreur FileNotFound.
Sinon, pick du premier video disponible, `history.init(nextVideo, newPlaylist)`,
puis `startPlayback(nextVideo)`.

## Section spec impactée

swipeplayer-specs.md §12 (Cas limites : fichier supprimé)
