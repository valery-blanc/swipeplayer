# BUG-006 — Zoom pinch saccadé : un micro-zoom par geste

**Statut :** FIXED
**Priorité :** P1 — zoom inutilisable

## Symptôme

Le zoom pinch produit un tout petit incrément à chaque geste. Il faut répéter le
pinch de nombreuses fois pour atteindre un zoom significatif. Le zoom ne suit pas
le mouvement des doigts en temps réel.

## Étapes de reproduction

1. Ouvrir une vidéo
2. Faire un geste pinch-to-zoom (écarter deux doigts)
3. → Le zoom augmente d'un infime incrément, puis s'arrête
4. Recommencer plusieurs fois pour obtenir un zoom perceptible

## Analyse de la cause racine

`GestureHandler.gestureHandler()` déclare `zoomScale` comme **clé** du `pointerInput` :

```kotlin
): Modifier = this.pointerInput(zoomScale, isSwipeEnabled, canSwipeDown, ...) {
```

À chaque frame du pinch, le handler appelle `onZoom(newScale)` →
`viewModel.onZoomChange()` → `uiState.zoomScale` change → `PlayerScreen` recompose
et passe la nouvelle valeur à `gestureHandler` → la clé `zoomScale` change →
**le bloc `pointerInput` est annulé et recréé**.

À la recréation :
- `previousSpan` est remis à `0f`
- La gesture en cours est perdue
- On n'a capturé qu'une seule frame de mouvement → micro-zoom

## Section spec impactée

- **Pinch-to-Zoom** : "Apply scale via graphicsLayer"
- **Gesture Zones** : "All gestures must be handled in a single pointerInput modifier"

## Correction

Passer `zoomScale` comme lambda `() -> Float` au lieu d'une valeur scalaire.
Retirer `zoomScale` des clés du `pointerInput` → la gesture n'est plus interrompue.
La lambda lit toujours la valeur courante depuis le state sans recréer le handler.

Fichiers : `GestureHandler.kt`, `PlayerScreen.kt`
