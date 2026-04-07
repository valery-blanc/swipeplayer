# FEAT-015 — Luminosité persistante (globale)

**Statut** : DONE

## Contexte
Le réglage de luminosité se réinitialisait à chaque lancement de l'app.

## Comportement
- La luminosité réglée par geste est sauvegardée globalement (clé `"global::brightness"`).
- Au lancement de l'app (init du ViewModel), la luminosité sauvegardée est chargée et appliquée.
- Ce réglage est global, pas par vidéo (contrairement au volume ou au format).

## Spec technique
- `VideoStateStore` : `saveBrightness(Float)` + `loadBrightness(): Float` (défaut : -1f = système).
- `PlayerViewModel.init` : charge la luminosité et l'injecte dans `PlayerUiState`.
- `onBrightnessDelta` et `onBrightnessChange` : sauvegardent après chaque modification.
- `PlayerActivity` applique déjà `uiState.brightness` à `window.attributes.screenBrightness`.

## Impact
- `VideoStateStore.kt` : 2 nouvelles méthodes
- `PlayerViewModel.kt` : init + 2 handlers modifiés
