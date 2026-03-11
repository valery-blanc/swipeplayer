# BUG-003 — Menu réglages (⚙) se ferme immédiatement après ouverture

**Statut :** FIXED
**Priorité :** P1 — fonctionnalité réglages totalement inutilisable

---

## Symptôme

Le menu associé au bouton ⚙ (réglages) apparaît une fraction de seconde puis disparaît. Aucune interaction n'est possible avant la fermeture. Le bouton ⚙ est donc inutilisable.

## Étapes de reproduction

1. Ouvrir l'app avec une vidéo
2. Tapper sur l'écran pour afficher les contrôles (si nécessaire)
3. Tapper sur le bouton ⚙ (settings)
4. → Le bottom sheet apparaît brièvement (< 500ms) puis se ferme

## Logcat

Aucun crash ou erreur. Problème de gestion d'état UI.

## Analyse de la cause racine

La fermeture instantanée (fraction de seconde, pas 4 secondes) indique un conflit entre l'ouverture du bottom sheet et le mécanisme d'auto-hide des contrôles.

**Hypothèse principale : le tap sur ⚙ déclenche `onToggleControls()` via le gestionnaire de gestes.**

Séquence fautive probable :

1. L'utilisateur tappe sur ⚙ → `onClick` du bouton ouvre le bottom sheet → `showSettings = true`
2. Le même tap est également capturé par le `pointerInput` de la zone centrale qui gère les taps simples → appelle `onToggleControls()` → `controlsVisible = false`
3. Quand `controlsVisible = false` → la `ControlsOverlay` disparaît → le bottom sheet (qui en fait partie) se ferme

Le tap sur un bouton dans la `ControlsOverlay` devrait consommer l'événement et empêcher sa propagation vers le `pointerInput` parent. Si la propagation n'est pas stoppée (`consume()` manquant ou propagation inversée), le tap atteint le gestionnaire de gestes global.

**Hypothèse secondaire : conflit avec le timer auto-hide.**

Si le tap sur ⚙ ne réinitialise pas le timer auto-hide (4 secondes), et que ce timer était presque écoulé, les contrôles pourraient se masquer quelques millisecondes après l'ouverture du menu. Peu probable ("fraction de seconde") mais possible si le timer est court et déjà avancé.

**Hypothèse tertiaire : `AnimatedVisibility` et bottom sheet dans le même scope.**

Si le bottom sheet est rendu à l'intérieur du bloc `AnimatedVisibility(controlsVisible)` qui gère la `ControlsOverlay`, il disparaît mécaniquement avec les contrôles.

## Section spec impactée

- **spec §6.6** : menu réglages (bottom sheet)
- **Controls Auto-hide** : "Auto-hide after 4 seconds via `LaunchedEffect` with a cancellable coroutine timer"
- **Gesture Zones** : "tap = toggle controls" (zone centrale)

La spec ne précise pas explicitement que l'ouverture d'un bottom sheet doit suspendre l'auto-hide et bloquer la propagation des taps, mais c'est le comportement attendu.

## Correction proposée

Deux corrections nécessaires, à confirmer après lecture de `ControlsOverlay.kt` :

1. **Stopper la propagation du tap sur ⚙ :** S'assurer que le `onClick` du bouton appelle `consume()` sur l'événement pointer pour qu'il ne remonte pas jusqu'au gestionnaire de gestes.

2. **Suspendre l'auto-hide quand le bottom sheet est ouvert :** Dans le `LaunchedEffect` qui gère le timer auto-hide, ajouter une condition : si `showSettings == true` (ou quel que soit le flag d'état du bottom sheet), ne pas lancer (ou annuler) le timer.

   ```kotlin
   // Dans ControlsOverlay ou PlayerScreen :
   LaunchedEffect(controlsVisible, showSettingsSheet) {
       if (!controlsVisible || showSettingsSheet) return@LaunchedEffect
       delay(4_000L)
       onHideControls()
   }
   ```

3. **S'assurer que le bottom sheet est rendu hors du bloc `AnimatedVisibility(controlsVisible)`** ou qu'il persiste indépendamment de l'état de visibilité des contrôles.

## Tests de régression à ajouter

- UI test : ouvrir le menu ⚙ → vérifier qu'il reste visible après 5 secondes
- UI test : ouvrir le menu ⚙ → tapper un élément → vérifier que l'action est exécutée
- UI test : ouvrir le menu ⚙ → appuyer sur retour → vérifier que les contrôles sont toujours visibles
