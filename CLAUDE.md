# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SwipePlayer is an Android local video player with TikTok-style vertical swipe navigation. Videos from the same directory are navigated by swiping up/down. The UI is inspired by Netflix. The specification is the source of truth: `docs/specs/swipeplayer-specs.md`.

**Package:** `com.swipeplayer` | **Min SDK:** API 26 | **Target SDK:** 36

## Règles ADB (OBLIGATOIRE)

**Ne JAMAIS utiliser `adb shell pm clear <package>` sur un launcher.**
Cette commande efface toutes les données du launcher (raccourcis, fond d'écran, disposition), pas seulement le cache icônes. C'est irréversible.

Pour vider uniquement le cache icônes du launcher après un changement d'icône :
```bash
adb shell am force-stop <launcher_package>   # tuer le launcher
adb shell am start -n <launcher_package>/.MainActivity  # relancer
# ou simplement laisser l'utilisateur appuyer sur Home
```

## Build & Deploy

```bash
./gradlew build                 # Build
./gradlew installDebug          # Deploy to connected device
./gradlew test                  # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests
```

## Workflow Rules

### Task Tracking
For any task that involves more than 3 files or more than 3 steps:
1. BEFORE starting, create/update a checklist in `docs/tasks/TASKS.md`
2. Mark each sub-step with `[ ]` (todo), `[x]` (done), or `[!]` (blocked)
3. Update the checklist AFTER completing each sub-step
4. If the session is interrupted, the checklist is the source of truth for resuming work

### Resuming Work
When starting a new session or after /clear, ALWAYS:
1. Read `docs/tasks/TASKS.md` to check current progress
2. Identify the first unchecked item
3. Resume from there — do NOT restart completed work

### Documentation Synchronization (OBLIGATOIRE)

**À chaque demande de modification, bug fix ou nouvelle feature — quelle que soit
la façon dont elle est formulée (message direct, fichier temp_*.txt, description
orale) — TOUJOURS :**

1. **Créer ou mettre à jour le fichier de bug** (`docs/bugs/BUG-XXX-*.md`)
   ou de feature (`docs/specs/FEAT-XXX-*.md`) correspondant.

2. **Mettre à jour `docs/specs/swipeplayer-specs.md`** — OBLIGATOIRE, SANS EXCEPTION.
   Ce fichier est la source de vérité de l'application. Il doit refléter à tout
   moment le comportement réel du code. Mettre à jour :
   - La section concernée (UI, gestes, navigation, persistance, architecture, etc.)
   - Le numéro de version en en-tête (FEAT-XXX / BUG-XXX)
   - La structure du projet §10 si des fichiers sont ajoutés/supprimés
   - Les cas limites si un nouveau cas est géré
   Ne pas attendre qu'on le demande. Si la feature est trop petite pour un §
   dédié, intégrer l'info dans la section la plus proche.

3. **Mettre à jour `docs/tasks/TASKS.md`** — toujours, sans condition :
   ajouter l'entrée si elle n'existe pas, cocher `[x]` les étapes terminées.

Cette règle s'applique MÊME pour les petites modifications demandées directement
dans le chat (ex : "désactive la mise en veille", "change la couleur", etc.).
Si c'est trop petit pour un fichier BUG/FEAT dédié, au minimum mettre à jour
`swipeplayer-specs.md` si le comportement change.

### Règle de déploiement et confirmation (OBLIGATOIRE)

**Aucun commit ne doit être créé avant que l'utilisateur ait testé et confirmé.**

Ordre impératif pour tout bug fix ou feature :

```
[code] → [docs] → [./gradlew installDebug] → [demander test] → [attendre OK] → [commit]
```

- Le commit regroupe TOUJOURS : code source + fichiers de doc + TASKS.md
- Si l'utilisateur signale un problème après test → corriger, re-déployer,
  re-demander confirmation AVANT de committer
- **Si un crash est découvert lors du test** → créer `docs/bugs/BUG-XXX-*.md`
  (même si le crash a déjà été corrigé), mettre à jour `swipeplayer-specs.md`
  avec la règle à retenir, et référencer dans TASKS.md
- Aucune exception : même pour une modification d'une seule ligne

### Bug Fix Workflow
1. Documenter le bug dans `docs/bugs/BUG-XXX-short-name.md` (symptôme,
   reproduction, logcat, section spec impactée)
2. Analyser la cause racine AVANT d'écrire le fix (Plan Mode)
3. Implémenter le fix
4. Mettre à jour toute la documentation :
   - `docs/bugs/BUG-XXX-*.md` → statut `FIXED`, fix appliqué décrit
   - **`docs/specs/swipeplayer-specs.md` → OBLIGATOIRE** : mettre à jour la section
     du comportement corrigé
   - `docs/tasks/TASKS.md` → cocher `[x]` toutes les étapes terminées
5. **Déployer sur le téléphone** : `./gradlew installDebug`
6. **Demander à l'utilisateur de tester et attendre sa confirmation explicite**
   — NE PAS committer avant que l'utilisateur confirme que c'est OK
7. Une fois confirmé : committer TOUS les fichiers modifiés en un seul commit
   (code + docs + TASKS.md) : `"FIX BUG-XXX: description courte"`

### Feature Evolution Workflow
1. Écrire la spec dans `docs/specs/FEAT-XXX-short-name.md` (contexte,
   comportement, spec technique, impact sur l'existant)
2. Analyser l'impact sur le code existant (Plan Mode) : risques, conflits,
   lacunes de la spec
3. Décomposer en tâches dans `docs/tasks/TASKS.md`
4. Implémenter
5. Mettre à jour toute la documentation :
   - `docs/specs/FEAT-XXX-*.md` → statut `DONE`, implémentation décrite
   - **`docs/specs/swipeplayer-specs.md` → OBLIGATOIRE** : intégrer le nouveau
     comportement dans la/les section(s) concernée(s), incrémenter la version
   - `docs/tasks/TASKS.md` → cocher `[x]` toutes les étapes terminées
6. **Déployer sur le téléphone** : `./gradlew installDebug`
7. **Demander à l'utilisateur de tester et attendre sa confirmation explicite**
   — NE PAS committer avant que l'utilisateur confirme que c'est OK
8. Une fois confirmé : committer TOUS les fichiers modifiés en un seul commit
   (code + docs + TASKS.md) : `"FEAT-XXX: description courte"`
9. Mettre à jour CLAUDE.md si des règles d'architecture ont changé

### Règle de build release (OBLIGATOIRE)

**À chaque build release (`./gradlew bundleRelease`) :**

1. **Incrémenter `versionCode`** dans `app/build.gradle.kts` AVANT de builder.
   Le Play Store rejette tout AAB dont le `versionCode` a déjà été uploadé.
   Règle : `versionCode` = numéro séquentiel strictement croissant, sans exception.
   Mettre à jour `versionName` si la version utilisateur change (ex: "1.1", "2.0").

2. **Vérifier `proguard-rules.pro`** avant d'activer ou de modifier la minification.
   La minification R8 (`isMinifyEnabled = true`) casse silencieusement :
   - **Hilt** : les classes `@HiltViewModel` et `@Inject` doivent être conservées
   - **Media3 / ExoPlayer** : toutes les classes ExoPlayer doivent être conservées
   - **Compose** : les classes de l'UI Compose doivent être conservées
   Le fichier `app/proguard-rules.pro` contient toutes ces règles.
   Si un nouveau ViewModel ou composant est ajouté, vérifier qu'il est couvert.

## Architecture

**Pattern:** MVVM + Hilt, Jetpack Compose UI

```
UI (Compose screens)
  HomeActivity
    HomeScreen — HorizontalPager 3 onglets (Collections / Vidéos / Parcourir)
    CollectionsScreen / VideosScreen / FileBrowserScreen

  PlayerActivity
    PlayerScreen — VerticalPager 3 pages logiques (prev/current/next)
    VideoSurface (SurfaceView dans AndroidView)
    ControlsOverlay (Compose pur)
    GestureHandler (un seul pointerInput, routage par zone X)

ViewModels (Hilt-injected, StateFlow)
  HomeViewModel / PlayerViewModel

Repository layer
  VideoRepository — listing multi-stratégie (SAF → MediaStore → File)
  VideoStateStore — persistance position/zoom/format/playbackOrder

Player layer
  VideoPlayerManager — max 2 instances ExoPlayer, looper partagé (Singleton immortel)
  PlaybackHistory — historique navigation + algorithme aléatoire
  AudioFocusManager
```

**DI:** Hilt — KSP (pas kapt) : `ksp("com.google.dagger:hilt-android-compiler:...")`
**Package root:** `com.swipeplayer`
**Entry points:** `HomeActivity` (LAUNCHER), `PlayerActivity` (ACTION_VIEW vidéo)

## Technology Stack

| Composant | Bibliothèque |
|---|---|
| UI | Jetpack Compose BOM (foundation, material3, material-icons-extended) |
| Navigation swipe | `VerticalPager` / `HorizontalPager` (Compose Foundation) |
| Lecteur vidéo | AndroidX Media3 / ExoPlayer 1.5.x |
| Architecture | Lifecycle ViewModel + StateFlow |
| DI | Hilt 2.51.x + **KSP** |
| Accès fichiers | DocumentFile (SAF) |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 36 |

## Key Architecture Rules

### ExoPlayer
- **Maximum 2 instances simultanées** : une en lecture (current), une en pré-chargement (next/peek).
- **Looper partagé obligatoire** : toutes les instances créées avec `setPlaybackLooper(sharedLooper)` pour partager le même `DefaultLoadControl`. Le `HandlerThread` démarre en `lazy`.
- **`setEnableDecoderFallback(true)`** : fallback SW si HW échoue. Toast + skip uniquement si aucun décodeur disponible.
- **`VideoPlayerManager` est un Singleton immortel** : pas de `close()`. `releaseAll()` libère les players dans `ViewModel.onCleared()` via `runBlocking(Dispatchers.Main.immediate)`. La `SurfaceView` est en `WeakReference`.
- Libération propre : `player.clearVideoSurfaceView(sv)` puis `player.release()`.

### Navigation (VerticalPager)
- 3 pages logiques (0=précédente, 1=courante, 2=suivante) avec reset silencieux `scrollToPage(1)` après chaque transition.
- Flag `isNavigating` pour éviter les doubles navigations sur gestes rapides (CRO-027).
- `onSwipeUp()` / `onSwipeDown()` protégés par un `Mutex` (`navigationMutex`).

### Gestes
- **Un seul `pointerInput` modifier** pour tous les gestes. Routage par position X du premier pointeur.
- `awaitFirstDown(requireUnconsumed = true)` pour ne pas interférer avec les boutons de l'overlay.
- `zoomScale` passé en `() -> Float` (lambda) pour ne pas être une clé du `pointerInput` — évite le redémarrage du handler.
- Zones : 15% gauche = luminosité | 70% centre = swipe/tap/seek/zoom | 15% droite = volume.
- Dead zone 20dp pour luminosité et volume.

### Callbacks ExoPlayer
- Les `Player.Listener` s'exécutent sur le `playbackThread`. Tous les callbacks sont dispatchés via `Handler(Looper.getMainLooper()).post { }` avant d'invoquer les lambdas du ViewModel.

### Persistance
- **Clés SharedPreferences** avec séparateur `::` (ex: `"nom_video.mp4::pos"`) pour éviter les collisions.
- Clé = nom de fichier seul (sans chemin) — survit aux déplacements de fichier.
- Position sauvée à 0 si visionnage < 5s ou position > durée - 5s.

### Icône app dans Compose
- **Ne pas utiliser `painterResource(R.mipmap.ic_launcher)`** sur API 26+ (les `<adaptive-icon>` XML ne sont pas supportés par `painterResource`). Utiliser `PackageManager.getApplicationIcon()` + rendu Bitmap (BUG-026).

### Auto-hide des contrôles
- Timer suspendu si un `DropdownMenu` (vitesse, format) ou le `ModalBottomSheet` (réglages) est ouvert.
- Le `BottomSheet` et les sliders luminosité/volume sont rendus **hors** du bloc `AnimatedVisibility` des contrôles principaux (BUG-011).

### Fin de vidéo
- Le ViewModel émet un `SharedFlow<Unit> autoSwipeUpEvent`. `PlayerScreen` collecte ce flow et exécute la même animation ping-pong que le geste manuel (BUG-027). Mode single-file : `seekTo(0) + play()` sans animation.

### Tri naturel
- `video2.mp4` < `video10.mp4`. Tokenisation regex `(\d+)|(\D+)`.

### MediaSession
- `media3-session` obligatoire Android 12+ pour les apps média de premier plan.

## Listing de répertoire — Chaîne de fallback
SAF DocumentFile → MediaStore RELATIVE_PATH (tous volumes via `getExternalVolumeNames()`) → `File.listFiles()` (nécessite `MANAGE_EXTERNAL_STORAGE`).

`MANAGE_EXTERNAL_STORAGE` n'est **pas** demandé proactivement au runtime.
