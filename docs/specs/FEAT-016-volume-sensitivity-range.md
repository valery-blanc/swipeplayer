# FEAT-016 — Volume : sensibilité + plage 0–150% + persistance par vidéo

**Statut** : DONE

## Contexte
Le réglage du volume était peu sensible et limité à 100% du volume général.

## Comportement
1. **Sensibilité** : 1/3 de la hauteur d'écran suffit pour passer de 0% à 100% (×3 vs avant).
2. **Plage étendue** : 0% à 150% du volume général (amplification au-delà de 100%).
3. **Persistance par vidéo** : le volume est sauvegardé/restauré par vidéo (clé `"${filename}::vol"`), comme le zoom et le format.
4. **Affichage** : la barre verticale représente la plage 0–150% (150% = barre pleine).

## Spec technique
- `GestureHandler` : delta volume multiplié par 3 (`/ screenHeightPx * 3f`).
- `PlayerViewModel.onVolumeDelta/onVolumeChange` : plage `coerceIn(0f, 1.5f)`.
- `player.volume` : valeur directement passée à ExoPlayer (Media3 supporte > 1.0f pour l'amplification).
- `VideoStateStore.VideoState` : ajout de `volume: Float = 1f`.
- `VideoStateStore.save/load` : clé `"${filename}::vol"`.
- `VolumeControl` : normalise `volume / 1.5f` pour l'affichage de la barre.
- Au chargement d'une vidéo (`startPlayback`) : restaure le volume sauvegardé.

## Impact
- `GestureHandler.kt`, `PlayerViewModel.kt`, `VideoStateStore.kt`, `VolumeControl.kt`
