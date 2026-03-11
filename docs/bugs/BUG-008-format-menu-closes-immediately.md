# BUG-008 — Menu format se referme immédiatement

**Statut :** FIXED
**Priorité :** P1 — menu format inutilisable

## Symptôme

Le menu déroulant du bouton format (FormatSelector) apparaît une fraction
de seconde puis se referme avant que l'utilisateur puisse choisir un mode.
Idem pour le menu vitesse (SpeedSelector).

## Étapes de reproduction

1. Ouvrir une vidéo
2. Tapper sur le bouton format (icône Fullscreen dans la barre d'outils)
3. → Le menu s'ouvre et se referme immédiatement

## Analyse de la cause racine

Même cause que BUG-003 (SettingsSheet) : l'état `expanded` de FormatSelector
et SpeedSelector est local à ces composables, qui vivent à l'intérieur du bloc
`AnimatedVisibility(visible = uiState.controlsVisible)`. Quand le timer auto-hide
se déclenche (4s d'inactivité) :

1. `viewModel.onToggleControls()` → `controlsVisible = false`
2. `AnimatedVisibility` commence la sortie → ControlsOverlay → ToolBar →
   FormatSelector/SpeedSelector sont retirés de la composition
3. `DropdownMenu` (qui est un Popup attaché à FormatSelector) est aussi détruit

Contrairement à BUG-003, le timer peut être déjà quasi-expiré au moment de
l'ouverture du menu → fermeture en fraction de seconde.

## Section spec impactée

- **Controls Auto-hide** : "Auto-hide after 4 seconds via LaunchedEffect"
- La spec ne précise pas que le timer doit être suspendu pendant les menus,
  mais c'est le comportement attendu (comme le SettingsSheet).

## Correction

Ajouter un callback `onMenuStateChange: (Boolean) -> Unit` à FormatSelector
et SpeedSelector. ToolBar propage ce callback. ControlsOverlay suspend le
timer auto-hide quand `isAnyMenuOpen || showSettingsSheet`.

Fichiers : FormatSelector.kt, SpeedSelector.kt, ToolBar.kt, ControlsOverlay.kt
