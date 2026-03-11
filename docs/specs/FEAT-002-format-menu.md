# FEAT-002 — Bouton format → menu déroulant avec ratios forcés

## Contexte

Le bouton format actuel cycle entre 4 modes (Adapter → Remplir → Étirer → 100%).
L'utilisateur veut un menu déroulant (comme la vitesse) avec plus d'options,
dont des ratios d'affichage forcés qui déforment la vidéo.

## Options du menu

| Label  | Valeur interne | Comportement |
|--------|---------------|-------------|
| Adapter | ADAPT | Ratio natif, bandes noires si nécessaire (défaut) |
| Remplir | FILL | Remplit l'écran, conserve le ratio, rogne les bords |
| Étirer | STRETCH | Remplit l'écran en déformant (ignore le ratio) |
| 100% | NATIVE_100 | 1 pixel vidéo = 1 pixel écran |
| 1:1 | RATIO_1_1 | Force un ratio carré (déformation) |
| 3:4 | RATIO_3_4 | Force un ratio portrait 3:4 (déformation) |
| 16:9 | RATIO_16_9 | Force un ratio paysage 16:9 (déformation) |

## Spécification technique

### DisplayMode (enum) — nouveaux membres
```kotlin
RATIO_1_1,   // forcer 1:1
RATIO_3_4,   // forcer 3:4 (largeur/hauteur = 0.75)
RATIO_16_9,  // forcer 16:9 (largeur/hauteur ≈ 1.777)
```

### VideoSurface — nouveaux ratios forcés
Pour les modes RATIO_*, utiliser `Modifier.requiredSize(w.dp, h.dp)` avec
le ratio forcé calculé depuis les dimensions du container :
- RATIO_1_1 : `min(containerW, containerH)` × `min(containerW, containerH)`
- RATIO_3_4 : si containerW/containerH < 0.75 → fit by width ; sinon fit by height
- RATIO_16_9 : si containerW/containerH > 1.777 → fit by height ; sinon fit by width
Puis appliquer la même logique que ADAPT mais avec le ratio forcé au lieu du ratio vidéo natif.

### FormatSelector composable
Nouveau composable `ui/components/FormatSelector.kt` (modèle de SpeedSelector) :
- `DropdownMenu` avec 7 options
- Option active mise en évidence
- Callback `onFormatSelected: (DisplayMode) -> Unit`

### ToolBar — remplacement du bouton cycle
Remplacer `IconButton(onClick = onDisplayModeChange)` par `FormatSelector`.
Supprimer `onDisplayModeChange` de l'interface ToolBar, ajouter `onFormatSelected`.

### PlayerViewModel
Remplacer `onDisplayModeChange()` (cycle) par `onDisplayModeSet(mode: DisplayMode)` (sélection directe).
Garder `onDisplayModeChange()` comme alias du cycle pour compatibilité des tests.

## Impact sur l'existant

- `PlayerUiState.kt` : nouveaux membres dans `DisplayMode` enum
- `VideoSurface.kt` : 3 nouveaux cases dans le `when(displayMode)`
- `ToolBar.kt` : remplacer bouton cycle par FormatSelector
- `ControlsOverlay.kt` : adapter la signature
- `PlayerViewModel.kt` : ajouter `onDisplayModeSet()`
- `PlayerViewModelTest.kt` : mettre à jour le test du cycle
