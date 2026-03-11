# FEAT-008 — Seek par swipe horizontal (style MX Player)

## Statut : DONE

## Contexte

MX Player propose un seek intuitif par glissement horizontal sur la zone centrale.
Le doigt reste sur l'écran pendant le seek ; l'image vidéo se met à jour en temps
réel à chaque déplacement, et la lecture reprend à la position finale au relâchement.

## Comportement implémenté

### Déclenchement

- Geste sur la **zone centrale** (70% de l'écran)
- **Mouvement initialement horizontal** (|deltaX| > |deltaY| × 1.5) — détecté
  par `SwipeDetector.isHorizontalIntent()`, déjà utilisé pour annuler le swipe
  vidéo ; il devient le déclencheur du seek horizontal.

### Pendant le geste (doigt posé)

- La **vidéo se met en pause** immédiatement (si elle était en lecture).
- `player.setSeekParameters(SeekParameters.CLOSEST_SYNC)` : seek rapide vers le
  keyframe le plus proche — permet d'afficher l'image en temps réel sans latence
  excessive.
- À chaque mouvement, `player.seekTo(target)` est appelé → **l'image vidéo se
  met à jour en temps réel** pendant le glissement.
- Un **indicateur visuel** s'affiche au centre (`HorizontalSeekIndicator`) :
  - Temps cible : `MM:SS` ou `HH:MM:SS` (monospace, 28sp, blanc)
  - Delta : `+Xs` / `-Xs` (16sp, gris clair)
  - Fond semi-transparent `#99000000`, coins arrondis 8dp

### Calcul de la position cible

```
100% de la largeur d'écran = 100 secondes
deltaMs = (totalDeltaX / screenWidthPx) * 100_000ms
cible = clamp(positionAuDebutDuGeste + deltaMs, 0, duration)
```

- `totalDeltaX` : déplacement total depuis le début du geste (pas incrémental)
- `positionAuDebutDuGeste` : capturée au moment de `onHorizontalSeekStart()`
- Swipe **à droite** (deltaX > 0) → avance
- Swipe **à gauche** (deltaX < 0) → recule

### Au relâchement (doigt levé)

- `player.setSeekParameters(SeekParameters.EXACT)` — précision maximale.
- `player.seekTo(positionFinale)` — seek de précision vers la position exacte.
- La lecture **reprend** si elle était active avant le geste.
- L'indicateur visuel disparaît.

### Interruption par pinch

- Si un 2e doigt touche pendant le geste → `onHorizontalSeekCancel()` :
  pas de seek supplémentaire, `SeekParameters` remis à `EXACT`, lecture
  reprise si nécessaire.

## État `PlayerUiState` ajouté

```kotlin
val isSeekingHorizontally: Boolean = false
val horizontalSeekTargetMs: Long = 0L
val horizontalSeekDeltaMs: Long = 0L   // cible - position de départ
```

## Fonctions `PlayerViewModel` ajoutées

```kotlin
fun onHorizontalSeekStart()                                    // pause + CLOSEST_SYNC
fun onHorizontalSeekUpdate(deltaX: Float, screenWidthPx: Float) // seekTo + uiState
fun onHorizontalSeekEnd()                                      // EXACT + seekTo + reprise
fun onHorizontalSeekCancel()                                   // EXACT + reprise sans seek
```

Variables privées ajoutées dans le ViewModel :
```kotlin
private var seekStartPositionMs = 0L
private var wasPlayingBeforeHorizontalSeek = false
```

## Fichiers créés / modifiés

| Fichier | Action |
|---|---|
| `ui/components/HorizontalSeekIndicator.kt` | Créé |
| `ui/gesture/GestureHandler.kt` | Modifié — 4 callbacks seek horizontal ajoutés en paramètres optionnels ; logique CENTER zone mise à jour |
| `ui/PlayerUiState.kt` | Modifié — 3 champs ajoutés |
| `ui/PlayerViewModel.kt` | Modifié — 4 fonctions + 2 variables privées ajoutées ; import `SeekParameters` |
| `ui/screen/PlayerScreen.kt` | Modifié — callbacks branchés dans `gestureHandler`, `HorizontalSeekIndicator` rendu hors `AnimatedVisibility` |

## Impact sur les specs générales

- `swipeplayer-specs.md` §7 — tableau des gestes : ligne "swipe horizontal" ajoutée
- `swipeplayer-specs.md` §7 règles de priorité : point 3 mis à jour (mode seek horizontal)
