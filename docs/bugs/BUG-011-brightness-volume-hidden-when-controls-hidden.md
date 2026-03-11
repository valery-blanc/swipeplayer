# BUG-011 — Sliders luminosité/volume invisibles quand les contrôles sont cachés

## Statut : FIXED

## Symptôme

Quand l'utilisateur swipe verticalement sur la bande gauche (luminosité) ou droite
(volume) alors que les contrôles principaux sont masqués (auto-hide), les sliders
`BrightnessControl` et `VolumeControl` ne s'affichent pas.
Ils ne deviennent visibles que si les contrôles sont déjà affichés au moment du geste.

## Comportement attendu

Les sliders de luminosité et de volume doivent s'afficher **indépendamment** de la
visibilité des contrôles principaux. Dès qu'un swipe vertical débute sur la bande
gauche ou droite, le slider correspondant doit apparaître immédiatement, même si
les contrôles (TopBar, CenterControls, ProgressBar, ToolBar) sont masqués.

## Reproduction

1. Lancer la lecture d'une vidéo
2. Attendre 4s que les contrôles s'auto-cachent (ou taper pour les cacher)
3. Swiper verticalement sur la bande gauche ou droite
4. **Résultat actuel** : rien ne s'affiche
5. **Résultat attendu** : le slider apparaît immédiatement

## Cause racine (analyse)

`BrightnessControl` et `VolumeControl` sont rendus à l'intérieur du bloc
`AnimatedVisibility(visible = controlsVisible)` dans `ControlsOverlay.kt`.
Quand `controlsVisible == false`, le bloc entier est invisible, y compris les sliders.

## Fix appliqué

`BrightnessControl` et `VolumeControl` ont été sortis du bloc `AnimatedVisibility`
des contrôles principaux et placés directement dans le `Box` racine de `PlayerScreen.kt`.
Ils restent conditionnés par leur propre état (`showBrightnessBar` / `showVolumeBar`).

### Fichiers modifiés

- `ui/components/ControlsOverlay.kt` — suppression de `BrightnessControl` et
  `VolumeControl` (et de leurs imports `fillMaxHeight`, `wrapContentWidth`)
- `ui/screen/PlayerScreen.kt` — ajout de `BrightnessControl` et `VolumeControl`
  dans le `Box` principal, hors `AnimatedVisibility`

## Impact spec

`swipeplayer-specs.md` §6.3 et §6.4 mis à jour : les sliders s'affichent
indépendamment de la visibilité des contrôles principaux.
