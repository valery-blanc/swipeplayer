# BUG-001 — Crash silencieux au swipe : PlaybackHistory non initialisée

**Statut :** FIXED
**Priorité :** P0 — bloquant (swipe navigation inutilisable)

---

## Symptôme

Le swipe vertical dans la zone centrale de l'écran n'a aucun effet visible. Aucun Toast, aucune animation, aucun changement de vidéo. L'app continue de jouer la vidéo courante.

## Étapes de reproduction

1. Ouvrir l'app via "Ouvrir avec" depuis un gestionnaire de fichiers (URI `content://`)
2. Attendre que la vidéo commence à jouer
3. Swiper verticalement vers le haut dans la zone centrale de l'écran
4. → Aucune réaction

## Logcat

```
E/AndroidRuntime( 6813): Process: com.swipeplayer, PID: 6813
E/AndroidRuntime( 6813):   at com.swipeplayer.data.PlaybackHistory.navigateForward(PlaybackHistory.kt:65)
E/AndroidRuntime( 6813):   at com.swipeplayer.ui.PlayerViewModel$onSwipeUp$1.invokeSuspend(PlayerViewModel.kt:144)
E/AndroidRuntime( 6813):   at com.swipeplayer.ui.PlayerViewModel.onSwipeUp(PlayerViewModel.kt:142)
E/AndroidRuntime( 6813):   at com.swipeplayer.ui.screen.PlayerScreenKt$PlayerScreen$2$1.invokeSuspend(PlayerScreen.kt:64)
```

L'exception levée est `IllegalStateException: "PlaybackHistory not initialised"` (ligne 65 de `PlaybackHistory.kt` : `check(history.isNotEmpty())`). Elle est absorbée silencieusement par le scope coroutine du ViewModel, d'où l'absence de message visible.

Le crash se reproduit à chaque session (observé sur plusieurs PIDs : 6813, 12654, 12421).

## Analyse de la cause racine

**Race condition entre l'initialisation et le geste de swipe.**

`PlayerUiState.isSwipeEnabled` a pour valeur par défaut `true` (`PlayerUiState.kt:37`). Cela signifie que dès le démarrage de l'activité, avant même que `onIntentReceived()` ait terminé son exécution (qui tourne en coroutine asynchrone), le swipe est considéré comme actif par `onSwipeUp()`.

Séquence fautive :

1. `PlayerActivity` démarre → `PlayerUiState(isSwipeEnabled = true)` immédiatement
2. `onIntentReceived(uri)` est lancé dans une coroutine (`viewModelScope.launch`) — exécution asynchrone
3. L'utilisateur swipe avant que la coroutine ait atteint `history.init(startVideo, playlist)`
4. `onSwipeUp()` passe le guard `if (!isSwipeEnabled) return` (car `isSwipeEnabled == true`)
5. `history.navigateForward()` est appelé alors que `history` est vide → `check(history.isNotEmpty())` échoue → `IllegalStateException`

**Fichiers impliqués :**
- `PlayerUiState.kt:37` — valeur par défaut incorrecte `isSwipeEnabled = true`
- `PlaybackHistory.kt:65` — `check()` déclenche l'exception
- `PlayerViewModel.kt:140–147` — `onSwipeUp()` ne se protège pas contre l'état non initialisé

## Section spec impactée

- **Navigation Algorithm** : "Swipe up: advance in history or pick a random unseen video"
- **VerticalPager Setup** : "block swipe-down at the start of history"

La spec ne précise pas la valeur par défaut de `isSwipeEnabled` mais l'intention implicite est qu'il est `false` jusqu'à ce que la playlist soit chargée.

## Correction proposée

Changer la valeur par défaut dans `PlayerUiState.kt` :

```kotlin
// AVANT
val isSwipeEnabled: Boolean = true,

// APRES
val isSwipeEnabled: Boolean = false,
```

La valeur est correctement mise à jour dans `onIntentReceived()` une fois le chargement terminé (`isSwipeEnabled = playlist.size > 1`), donc cette seule modification suffit à fermer la fenêtre de race.

## Tests de régression à ajouter

- Unit test : appeler `onSwipeUp()` avant `onIntentReceived()` → vérifier qu'il ne crash pas et ne navigue pas
- Unit test : appeler `onSwipeUp()` pendant `onIntentReceived()` (playlist vide) → même vérification
