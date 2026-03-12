# BUG-019 — Race condition dans startPlayback() lors de swipes rapides

**Statut** : FIXED
**Sévérité** : MAJEUR
**Découvert via** : Code review 2026-03-12 (CR-010)

## Symptôme

Swipes très rapides peuvent créer deux ExoPlayers simultanément et tenter
de libérer le même player deux fois → IllegalStateException.

## Cause racine

`startPlayback()` capture `oldPlayer = playerManager.currentPlayer` avant
`preparePlayer()` (qui est suspend). Une seconde invocation concurrent peut
avoir changé `currentPlayer` entretemps.

## Fix appliqué

Ajout d'un compteur de génération `playbackStartToken` dans le ViewModel.
Après `preparePlayer()`, vérification que le token n'a pas changé.
Si changé (autre startPlayback() a démarré), le nouveau player est libéré
immédiatement et la fonction retourne.

## Section spec impactée

swipeplayer-specs.md §8 (Gestion du cycle de vie)
