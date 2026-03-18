# FEAT-009 — Animation de swipe style TikTok

**Statut** : EN COURS
**Priorité** : Haute
**Impact** : PlayerScreen.kt, GestureHandler.kt, PlayerViewModel.kt, PlayerConfig.kt

---

## Contexte

Le système de swipe actuel utilise un VerticalPager avec `userScrollEnabled = false`.
Les pages adjacentes (0 = prev, 2 = next) sont noires pendant la transition : l'utilisateur
ne voit pas la vidéo suivante apparaître — il y a juste un écran noir entre les vidéos.

TikTok, RedNote et YouTube Shorts affichent la vidéo suivante en pause (image fixe)
qui remonte depuis le bas pendant le geste, tandis que la vidéo courante monte.

---

## Comportement attendu

### Swipe vers le haut (vidéo suivante)

1. Dès que l'utilisateur commence à glisser verticalement dans la zone centre,
   la vidéo courante suit le doigt (translationY = offset du geste).
2. La vidéo suivante (preloadée, en pause sur le premier frame) apparaît par le bas
   dans l'espace libéré, à `translationY = screenHeight + offsetGeste`.
3. La vidéo courante continue de jouer pendant le déplacement.
4. **Seuil de validation** : `|offset| >= screenHeight * 0.25` OU vélocité >= 800 dp/s
   → animation complète vers `-screenHeight`, puis `viewModel.onSwipeUp()`, puis reset.
5. En dessous du seuil : animation spring de retour à 0 (elastic bounce).

### Swipe vers le bas (vidéo précédente)

1. Même logique de déplacement (translationY positive).
2. Pas de vidéo précédente preloadée → fond noir visible par le haut.
3. Même seuil de validation.
4. Désactivé si `!canGoBack` : elastic bounce uniquement (inchangé).

---

## Architecture technique

### Suppression du VerticalPager

Le `VerticalPager` et son `LaunchedEffect(pagerState.currentPage)` sont supprimés.
Le nouveau mécanisme est entièrement piloté par `Animatable<Float>` :

```
dragOffset: Animatable(0f)
  |
  ├─ onVideoDragUpdate(dy) → snapTo(dy)       -- temps réel, sans animation
  ├─ onSwipeUp()           → animateTo(-H)    -- commit up
  │                        → viewModel.onSwipeUp()
  │                        → snapTo(0f)
  ├─ onSwipeDown()         → animateTo(+H)    -- commit down
  │                        → viewModel.onSwipeDown()
  │                        → snapTo(0f)
  └─ onVideoDragCancel()   → animateTo(0f, spring)  -- annulation
```

### Couches vidéo

```
Box(fillMaxSize) {
  // Couche arrière : vidéo suivante (swipe up) ou noire (swipe down)
  VideoSurface(
    player = behindPlayer,   // capturé au début du drag
    modifier = graphicsLayer { translationY = screenHeightPx + dragOffset }
  )

  // Couche avant : vidéo courante
  VideoSurface(
    player = currentPlayer,
    modifier = graphicsLayer { translationY = dragOffset }
  )

  // Controls overlay (inchangé, par-dessus tout)
}
```

### GestureHandler — nouveaux callbacks

| Callback | Moment | Données |
|----------|--------|---------|
| `onVideoDragUpdate(dy: Float)` | Pendant le drag vertical centre | Delta Y depuis le début du geste |
| `onVideoDragCancel()` | Fin du drag, seuil non atteint | — |
| `onSwipeUp()` / `onSwipeDown()` | Seuil atteint | — (inchangé) |

Le drag démarre visuellement dès 10 px de déplacement vertical (contre 80 dp avant).
Le seuil de validation est `screenHeight * 0.25` (25%) OU `velocityY >= 800 dp/s`.

### PlayerConfig — nouvelles constantes

```kotlin
const val SWIPE_COMMIT_FRACTION: Float = 0.25f   // 25% de l'écran
const val SWIPE_COMMIT_VELOCITY_DP: Float = 800f // dp/s
const val SWIPE_DRAG_START_PX: Float = 10f       // dead zone avant animation
```

### PlayerViewModel — propriété exposée

```kotlin
val nextPlayer get() = playerManager.nextPlayer
```

---

## Gestion des cas limites

| Situation | Comportement |
|-----------|--------------|
| `nextPlayer == null` (pas encore preloadé) | Fond noir sous la vidéo courante pendant le swipe |
| Swipe down sans historique | Bounce élastique, pas de déplacement |
| Swipe annulé (< seuil) | Spring animation retour à 0 |
| Pinch pendant swipe | Swipe ignoré, dragOffset annulé |
| Swipe horizontal détecté | onVideoDragCancel(), passage en seekbar mode |

---

## Timings

- Animation commit up/down : 300 ms (`tween`, `FastOutSlowIn`)
- Animation cancel (spring) : ~200 ms (`DampingRatioMediumBouncy`, `StiffnessMedium`)
- Démarrage animation drag : immédiat après 10 px de mouvement

---

## Fichiers modifiés

1. `docs/specs/FEAT-009-tiktok-swipe-animation.md` (ce fichier)
2. `docs/specs/swipeplayer-specs.md` — section "Swipe transition"
3. `docs/tasks/TASKS.md` — FEAT-009
4. `app/src/main/java/com/swipeplayer/player/PlayerConfig.kt`
5. `app/src/main/java/com/swipeplayer/ui/gesture/GestureHandler.kt`
6. `app/src/main/java/com/swipeplayer/ui/screen/PlayerScreen.kt`
7. `app/src/main/java/com/swipeplayer/ui/PlayerViewModel.kt`
