# BUG-002 — Mode d'affichage par défaut incorrect : vidéo étirée au lieu de ADAPT

**Statut :** FIXED
**Priorité :** P1 — visible immédiatement à l'ouverture de toute vidéo

---

## Symptôme

Les vidéos s'affichent étirées pour remplir tout l'écran, en ignorant leur ratio natif. Le déformatage est visible pour toutes les vidéos dont le ratio n'est pas identique à celui de l'écran (observé sur 16:9 → écran, 4:3 → écran, 19:9 → écran).

Le comportement attendu est : affichage en mode **ADAPT** (fit, ratio conservé), avec des bandes noires si nécessaire (letterbox ou pillarbox).

## Étapes de reproduction

1. Ouvrir n'importe quelle vidéo via "Ouvrir avec"
2. Observer l'image dès le premier rendu
3. → La vidéo est étirée pour remplir l'écran sans respecter le ratio

## Logcat

Aucun crash ou erreur dans les logs. Problème purement visuel.

## Analyse de la cause racine

La valeur par défaut de `displayMode` dans `PlayerUiState` est correctement `DisplayMode.ADAPT` (`PlayerUiState.kt:64`). Le problème se situe donc dans l'implémentation de `VideoSurface` (composable `AndroidView` qui encapsule le `SurfaceView`) : le mode `ADAPT` n'est pas appliqué à la surface ExoPlayer.

Causes probables (à confirmer dans `VideoSurface.kt`) :

1. **`AspectRatioFrameLayout` absent ou mal configuré** : ExoPlayer applique nativement le scaling via `AspectRatioFrameLayout`, mais puisque l'UI est en Compose pur (sans `media3-ui`), ce composant n'est pas utilisé. Si aucune logique équivalente n'est implémentée, le `SurfaceView` s'étend à la taille de son parent → stretch.

2. **`graphicsLayer` ou `Modifier.aspectRatio` absent** : Pour implémenter `ADAPT` en Compose, il faut contraindre le `SurfaceView` à ses dimensions natives (ex : `Modifier.aspectRatio(videoWidth / videoHeight.toFloat())`), ce qui n'est peut-être pas fait.

3. **Ratio vidéo non récupéré** : Si la largeur/hauteur de la vidéo (issues du `VideoSize` de l'ExoPlayer via `Player.Listener.onVideoSizeChanged`) ne sont pas propagées jusqu'au composable surface, le ratio ne peut pas être calculé.

## Section spec impactée

- **UI Style** : "Display modes cycle: Adapt (fit) → Fill (crop) → Stretch → 100% (native)"
- **Video Surface + Compose** : "Use `SurfaceView` wrapped in `AndroidView` for the video surface only."

La spec indique explicitement que le mode par défaut est `ADAPT` et que l'UI est en Compose pur (pas de `media3-ui`). L'implémentation du scaling doit donc être faite manuellement.

## Comportement attendu par mode

| Mode | Comportement |
|------|-------------|
| ADAPT (défaut) | Ratio natif conservé, bandes noires si nécessaire |
| FILL | Rempli l'écran, rognage des bords |
| STRETCH | Remplit l'écran sans respecter le ratio |
| NATIVE_100 | Taille pixel-à-pixel (peut déborder) |

## Correction proposée

Dans `VideoSurface.kt` :

1. Écouter `Player.Listener.onVideoSizeChanged` pour récupérer `videoWidth` et `videoHeight`
2. Propager ces valeurs dans le `PlayerUiState` (ou directement dans le composable via `remember`)
3. Appliquer le bon `Modifier` selon `displayMode` :
   - `ADAPT` : `Modifier.aspectRatio(width / height.toFloat())` (avec `matchHeightConstraintsFirst` selon orientation)
   - `FILL` : `Modifier.fillMaxSize()` + `graphicsLayer { scaleX/scaleY calculé }`
   - `STRETCH` : `Modifier.fillMaxSize()` (comportement actuel, pas de contrainte)
   - `NATIVE_100` : taille fixe en px

## Tests de régression à ajouter

- UI test ou screenshot test : vidéo 4:3 en mode ADAPT → vérifier la présence de pillarbox
- UI test : vidéo 16:9 en mode ADAPT sur écran 19:9 → vérifier letterbox
- Unit test : cycle des modes via `onDisplayModeChange()` → vérifier la séquence ADAPT→FILL→STRETCH→NATIVE_100→ADAPT
