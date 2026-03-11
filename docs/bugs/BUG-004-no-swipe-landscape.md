# BUG-004 — Swipe absent en mode paysage

**Statut :** FIXED
**Priorité :** P1 — navigation impossible en paysage

---

## Symptôme

En mode portrait le swipe vertical (zone centrale) fonctionne correctement.
En mode paysage, aucune réaction au swipe — la vidéo reste figée.

## Étapes de reproduction

1. Ouvrir l'app via "Ouvrir avec" sur une vidéo dans un répertoire avec plusieurs fichiers
2. Faire pivoter le téléphone en mode paysage (manuellement ou via le verrouillage auto)
3. Swiper verticalement dans la zone centrale de l'écran
4. → Aucune réaction (pas de changement de vidéo)

## Analyse de la cause racine

`GestureHandler.gestureHandler()` reçoit `screenWidthPx` et `screenHeightPx` comme
paramètres lambda, mais le bloc `pointerInput` est keyed uniquement sur
`(zoomScale, isSwipeEnabled, canSwipeDown)` :

```kotlin
}: Modifier = this.pointerInput(zoomScale, isSwipeEnabled, canSwipeDown) {
```

En Compose, la lambda `pointerInput` est recréée uniquement quand l'une des clés
change. Les valeurs `screenWidthPx` et `screenHeightPx` sont capturées dans la
closure au moment de la première composition (portrait). Lors d'une rotation :
- `configuration.screenWidthDp` passe de ~360dp à ~740dp (exemple)
- `screenWidthPx` capture dans la closure reste à la valeur portrait (ex. 1080px)
- Le modifier ne se recrée pas car ses clés n'ont pas changé

Conséquence : `classifyZone(x, 1080)` sur un écran paysage de 2340px de large :
- Zone LEFT : x < 162px (au lieu de ~351px)
- Zone RIGHT : x > 918px (au lieu de ~1989px)
- Zone CENTER : seulement 162–918px

**Toute touche à x > 918px est classée zone RIGHT (volume) au lieu de CENTER (swipe).**
En paysage, la moitié droite de l'écran est hors de la zone CENTER, ce qui rend
le swipe pratiquement inutilisable.

## Fichiers impactés

- `ui/gesture/GestureHandler.kt:62` — clés du `pointerInput`
- `ui/screen/PlayerScreen.kt` — calcul et transmission de `screenWidthPx`/`screenHeightPx`

## Section spec impactée

- **Gesture Zones** : "Left 15%: brightness, Right 15%: volume, Center 70%: navigation"
- **Intent Handling** : `configChanges="orientation|..."` — l'Activity ne recrée pas,
  donc Compose doit gérer la rotation par recomposition

## Correction

Ajouter `screenWidthPx` et `screenHeightPx` aux clés du `pointerInput` :

```kotlin
}: Modifier = this.pointerInput(zoomScale, isSwipeEnabled, canSwipeDown, screenWidthPx, screenHeightPx) {
```

Le bloc se recrée à chaque rotation → closure re-capture les bonnes dimensions.
