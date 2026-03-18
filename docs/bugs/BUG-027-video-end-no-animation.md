# BUG-027 — Fin de vidéo : pas d'animation, image figée + son de la vidéo suivante

## Statut
`FIXED`

## Symptôme
Quand une vidéo se termine, la vidéo suivante démarre avec le son mais :
- L'image reste figée sur la dernière frame de la vidéo précédente
- Aucune animation de transition n'est jouée
- L'utilisateur voit pendant un moment la mauvaise image

## Reproduction
1. Ouvrir une vidéo dans un dossier avec plusieurs vidéos
2. Laisser la vidéo se terminer naturellement
3. Observer : la vidéo suivante démarre en audio mais l'image n'a pas transitionné

## Cause racine
`onVideoEnded()` dans `PlayerViewModel` appelait directement `onSwipeUp()`, qui fait
le swap de player en interne (côté ViewModel) **sans déclencher l'animation TikTok**
du `PlayerScreen`.

L'animation swipe-up (translation des deux surfaces A/B, flip ping-pong) est
entièrement gérée dans `PlayerScreen` et n'est déclenchée que par les callbacks
de geste (`onSwipeUp` dans `gestureHandler`). En bypassant ce chemin, on se
retrouvait avec le nouveau player actif mais l'ancienne surface affichée.

## Fix appliqué
- `PlayerViewModel` : ajout d'un `SharedFlow<Unit> autoSwipeUpEvent` émis par
  `onVideoEnded()` à la place de l'appel direct à `onSwipeUp()`.
- `PlayerScreen` : `LaunchedEffect` qui collecte `autoSwipeUpEvent` et exécute
  exactement la même animation swipe-up que le geste manuel (translation des
  surfaces, flip `aIsFront`, appel `viewModel.onSwipeUpNoSurface()`).
- Le mode single-file (replay) est conservé : le ViewModel continue à gérer ce
  cas directement sans émettre l'event.

## Section spec impactée
§ FEAT-003 Auto-next — §15 Notes d'implémentation
