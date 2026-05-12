# FEAT-020 — Volume max étendu à 200% du son système

**Status:** IN PROGRESS
**Date:** 2026-05-12

## Context

FEAT-016 avait fixé le max à 150% (volume ExoPlayer = 1.5f). L'utilisateur juge
la plage encore insuffisante et demande un max à 200%.

## Behavior

- 0% barre → 0% son système (volume = 0f)
- 50% barre → 100% son système (volume = 1.0f) — position par défaut
- 100% barre → 200% son système (volume = 2.0f)

## Technical spec

`AudioTrack.getMaxVolume()` = 1.0f sur Android — `ExoPlayer.setVolume()` est clampé là.
Pour dépasser 100%, on utilise `android.media.audiofx.LoudnessEnhancer` :
- `player.volume = min(volume, 1.0f)` pour la plage 0–100%
- `LoudnessEnhancer(audioSessionId)` avec gain = `20 * log10(volume) * 100` mB pour > 100%
- Enhancer attaché à la session audio du player courant ; relâché si la session change (swap)

Fichiers :
- `PlayerViewModel.kt` : imports `LoudnessEnhancer`, `log10`, `roundToInt` ; champs
  `loudnessEnhancer` + `loudnessSessionId` ; helper `applyVolume()` ; tous les
  `player?.volume = x` remplacés ; `release()` dans `onCleared()`
- `VolumeControl.kt` : `volume / 1.5f` → `volume / 2.0f`
- La sensibilité geste (×3) reste inchangée :
  1/3 d'écran = +1.0 = +100% son système ; 2/3 d'écran = plein volume (200%)

## Impact on existing code

- Les états sauvegardés (volume ≤ 1.5f) restent valides — la plage est juste étendue
- Valeur par défaut 1.0f → 50% de la barre ✓ (pas de changement nécessaire)
