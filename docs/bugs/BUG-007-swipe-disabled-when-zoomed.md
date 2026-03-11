# BUG-007 — Navigation swipe impossible quand la vidéo est zoomée

**Statut :** FIXED
**Priorité :** P2 — limitation de navigation contraignante

## Symptôme

Une fois la vidéo zoomée (pinch > 1x), le swipe vertical ne change plus de vidéo.
Il faut d'abord dé-zoomer (double-tap ou pinch retour à 1x) pour pouvoir naviguer.

## Étapes de reproduction

1. Ouvrir l'app, zoomer la vidéo à 2x via pinch
2. Swiper verticalement vers le haut (zone centrale)
3. → Aucune réaction, pas de changement de vidéo

## Analyse de la cause racine

Dans `GestureHandler.kt`, la condition de déclenchement du swipe inclut une garde
sur le zoom :

```kotlin
isDragging && !isHorizontal && isSwipeEnabled && zoomScale <= 1f -> {
```

`zoomScale <= 1f` bloque intentionnellement la navigation quand on est zoomé
(spec originale : "When scale > 1x, disable the vertical swipe-to-navigate gesture").

L'utilisateur souhaite modifier cette règle : le swipe doit fonctionner indépendamment
du niveau de zoom.

## Section spec impactée (à réviser)

Spec actuelle : "When scale > 1x, disable the vertical swipe-to-navigate gesture.
The user must first pinch back to 1x (or double-tap to reset zoom) before video
navigation is re-enabled."

**Nouvelle règle** : le swipe de navigation fonctionne quel que soit le niveau de zoom.

## Correction

Supprimer `zoomScale <= 1f` de la condition dans `GestureHandler.kt` :

```kotlin
// Avant
isDragging && !isHorizontal && isSwipeEnabled && zoomScale <= 1f -> {

// Après
isDragging && !isHorizontal && isSwipeEnabled -> {
```

Fichier : `GestureHandler.kt`
