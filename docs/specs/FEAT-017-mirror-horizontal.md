# FEAT-017 — Miroir horizontal persistant par vidéo

**Statut** : DONE

## Contexte
Certaines vidéos sont enregistrées en miroir (caméra frontale, capture d'écran retournée).

## Comportement
- Bouton dans la ToolBar (entre Format et Orientation) pour basculer le miroir horizontal.
- L'effet miroir est appliqué uniquement à la surface vidéo (pas aux contrôles).
- Deux états visuels de l'icône :
  - **Inactif** : `Icons.Filled.Flip` orientation normale
  - **Actif** : `Icons.Filled.Flip` retourné horizontalement (`graphicsLayer { scaleX = -1f }` sur l'icône uniquement)
- Persistance par vidéo : clé `"${filename}::mirror"` dans `VideoStateStore`.
- Restauré automatiquement à l'ouverture de chaque vidéo.

## Spec technique

### Surface vidéo : TextureView (pas SurfaceView)
`SurfaceView` rend dans un buffer séparé composité par SurfaceFlinger, en dehors du
pipeline RenderNode de l'app. Ni `graphicsLayer { scaleX = -1f }` ni `View.scaleX = -1f`
n'atteignent le contenu vidéo d'un `SurfaceView`.

`TextureView` rend dans une texture OpenGL intégrée au pipeline de rendu de l'app.
`tv.scaleX = -1f` s'applique directement au contenu vidéo. C'est pourquoi `VideoSurface`
utilise `TextureView` (avec `player.setVideoTextureView` / `clearVideoTextureView`).

Note : le décodage HW+ (`MediaCodec`) est inchangé — il est indépendant du type de surface.
TextureView a une légère surcharge GPU (pas de hardware overlay), acceptable pour des
fichiers locaux sans DRM.

### Implémentation
- `PlayerUiState` : `isMirrored: Boolean = false`.
- `VideoStateStore.VideoState` : ajout de `isMirrored: Boolean = false`.
- `VideoStateStore.save/load` : clé `"${filename}::mirror"` (Boolean → Int 0/1).
- `PlayerViewModel.onMirrorToggle()` : bascule + sauvegarde immédiate.
- `VideoSurface` : migré `SurfaceView` → `TextureView` ; `tv.scaleX = if (isMirrored) -1f else 1f`
  dans le bloc `update` de `AndroidView` (appliqué à chaque recomposition).
- `ControlsOverlay` : transmet `isMirrored` et `onMirrorToggle` à `ToolBar`.
- `PlayerScreen` : transmet `uiState.isMirrored` aux deux `VideoSurface` (front uniquement,
  `false` pour la surface back).
- `ToolBar` : bouton `Icons.Filled.Flip` avec `graphicsLayer { scaleX = if (isMirrored) -1f else 1f }`
  sur l'icône — elle se retourne visuellement quand actif.

## Impact
- `PlayerUiState.kt`, `VideoStateStore.kt`, `PlayerViewModel.kt`, `VideoSurface.kt`,
  `ToolBar.kt`, `ControlsOverlay.kt`, `PlayerScreen.kt`
