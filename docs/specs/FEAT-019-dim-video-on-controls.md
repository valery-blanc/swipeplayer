# FEAT-019 — Paramètre d'assombrissement de la vidéo lors des contrôles

**Status:** IN PROGRESS
**Date:** 2026-05-12

## Context

Quand les contrôles s'affichent (TopBar, CenterControls, ProgressBar), le fond du
ControlsOverlay est `#80000000` (50% noir transparent), ce qui assombrit la vidéo.
Certains utilisateurs préfèrent voir la vidéo sans assombrissement pour mieux juger
la qualité d'image pendant les réglages.

## Behavior

Un toggle dans le menu des paramètres (SettingsSheet) permet d'activer/désactiver
l'assombrissement de la vidéo lors de l'affichage des contrôles.

- **Activé (défaut)** : fond `#80000000` sur l'overlay — comportement actuel
- **Désactivé** : fond transparent — les contrôles s'affichent sans assombrir la vidéo ;
  les composants individuels (TopBar, CenterControls, ProgressBar) conservent leurs propres
  arrière-plans semi-transparents

La préférence est globale (non liée à une vidéo spécifique) et persistée dans SharedPreferences.

## Technical spec

- `PlayerUiState.kt` : ajout `dimVideoOnControls: Boolean = true`
- `VideoStateStore.kt` : `saveDimVideoOnControls(Boolean)` + `loadDimVideoOnControls(): Boolean`
  clé `"global::dim_video_on_controls"`
- `PlayerViewModel.kt` : chargement dans `init {}` ; `onDimVideoOnControlsChange(Boolean)`
- `ControlsOverlay.kt` : `background(if (uiState.dimVideoOnControls) Color(0x80000000) else Color.Transparent)`
- `SettingsSheet.kt` : nouveaux paramètres `dimVideoOnControls` + `onDimVideoOnControlsChange` ; Row avec Switch
- `PlayerScreen.kt` : passage des nouveaux paramètres à SettingsSheet

## Impact on existing code

- Aucun impact sur la logique de lecture
- L'AnimatedVisibility (fade in/out) reste inchangée — seul le fond change
- Les barres luminosité/volume hors AnimatedVisibility ne sont pas affectées
