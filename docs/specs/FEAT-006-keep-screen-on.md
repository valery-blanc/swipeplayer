# FEAT-006 — Maintien de l'écran allumé pendant la lecture

**Statut :** DONE

## Contexte

L'écran s'éteignait automatiquement après le délai de veille système même
pendant la lecture d'une vidéo. La permission `WAKE_LOCK` était déclarée dans
le manifest mais jamais activée.

## Comportement

| État | Écran |
|------|-------|
| Lecture en cours (`isPlaying = true`) | Reste allumé indéfiniment |
| Pause (`isPlaying = false`) | Reprend le comportement système normal (veille auto) |
| App fermée / en arrière-plan | Reprend le comportement système normal |

## Implémentation

`PlayerActivity.kt` — `LaunchedEffect(uiState.isPlaying)` dans `setContent` :

```kotlin
LaunchedEffect(uiState.isPlaying) {
    if (uiState.isPlaying) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```

`FLAG_KEEP_SCREEN_ON` est nettoyé automatiquement quand l'Activity est détruite,
donc pas de risque que le téléphone reste éveillé indéfiniment après fermeture.

## Permission

`WAKE_LOCK` déjà déclarée dans `AndroidManifest.xml` depuis la mise en place
initiale du projet (spec originale).
