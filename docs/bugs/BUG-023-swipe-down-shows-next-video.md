# BUG-023 — Swipe bas : flash de la vidéo suivante (au lieu de la précédente)

## Statut
`FIXED`

## Symptôme
Pendant le swipe vers le bas (retour à la vidéo précédente), la vidéo suivante
(nextPlayer) apparaissait brièvement en haut de l'écran à la place de la vidéo
précédente.

## Cause racine
`backOffset()` utilisait `swipeDir = 1 → -screenHeightPx + dragOffset` pour faire
monter la surface arrière depuis le haut. Mais la surface arrière contenait
`nextPlayer` (pas `prevPlayer`). La vidéo visible pendant le drag DOWN était donc
la mauvaise vidéo.

## Fix appliqué
**Architecture ping-pong symétrique** : le swipe DOWN est maintenant un miroir
exact du swipe UP.

1. **`onVideoDragUpdate`** : au premier pixel de drag vers le bas, `prevPlayer` est
   affecté à la surface arrière (comme `nextPlayer` l'est lors du premier drag vers
   le haut). La surface arrière contient désormais toujours la vidéo correcte.

2. **`backOffset()`** : prend en charge les deux directions :
   ```kotlin
   -1 → screenHeightPx + dragOffset    // UP : arrière monte depuis le bas
    1 → -screenHeightPx + dragOffset   // DOWN : arrière descend depuis le haut
    0 → screenHeightPx                 // repos : arrière parqué en bas
   ```

3. **`onSwipeDown`** : devient symétrique à `onSwipeUp` — flip de `aIsFront`,
   `snapTo(0)`, `swapPrevNoSurface()` via `viewModel.onSwipeDown()`.

4. **`onVideoDragCancel`** : si un drag DOWN a été annulé, la surface arrière est
   restaurée vers `nextPlayer` pour le prochain swipe UP.

## Fichiers modifiés
- `app/src/main/java/com/swipeplayer/ui/screen/PlayerScreen.kt`
