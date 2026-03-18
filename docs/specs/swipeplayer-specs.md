# SwipePlayer — Spécifications Techniques Complètes

> **Version** : mise à jour intégrant FEAT-001 à FEAT-014, BUG-001 à BUG-027,
> revues de code CR-001 à CR-023, et corrections CRO-001 à CRO-031 (revue Opus 2026-03-12).
> Pour l'historique des bugs et features, voir `docs/bugs/` et `docs/specs/FEAT-*.md`.

## 1. Vue d'ensemble du projet

**Nom de l'application :** SwipePlayer
**Plateforme :** Android (API minimum 26 / Android 8.0, cible API 36)
**Type :** Lecteur vidéo local avec navigation par swipe vertical (style TikTok)
**Référence fonctionnelle :** MX Player — pour toute fonctionnalité non spécifiée ci-dessous, se baser sur le comportement de MX Player.

### Résumé en une phrase

Un lecteur vidéo Android haute performance avec décodage matériel, qui permet de naviguer entre les vidéos d'un même répertoire par swipe vertical (façon TikTok), avec une interface inspirée de Netflix.

---

## 2. Stack technique et choix technologiques

### Priorité absolue : fluidité et performance

| Composant | Technologie | Justification |
|---|---|---|
| Langage | **Kotlin** | Langage natif Android, performant, coroutines pour l'async |
| UI Framework | **Jetpack Compose** | UI déclarative moderne, animations fluides natives, gestion des gestures intégrée |
| Lecteur vidéo | **Media3 ExoPlayer** (androidx.media3) | Successeur officiel d'ExoPlayer, décodage HW+, support de tous les codecs matériels, gestion fine du cycle de vie |
| Navigation swipe | **Compose Foundation — `VerticalPager`** | Swipe natif performant avec pré-chargement des pages adjacentes |
| Architecture | **MVVM** avec Jetpack ViewModel + StateFlow | Réactivité, séparation des responsabilités, survie aux rotations |
| DI | **Hilt** | Injection de dépendances standard Android |
| Build | **Gradle Kotlin DSL** | Standard moderne |

### Dépendances principales

```kotlin
// build.gradle.kts (app)
dependencies {
    // Media3 ExoPlayer (media3-ui non utilisé : UI gérée en Compose pur)
    implementation("androidx.media3:media3-exoplayer:1.5.x")
    implementation("androidx.media3:media3-common:1.5.x")
    implementation("androidx.media3:media3-session:1.5.x")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.7.x")
    implementation("androidx.compose.foundation:foundation:1.7.x") // VerticalPager
    implementation("androidx.compose.material3:material3:1.3.x")
    implementation("androidx.compose.material:material-icons-extended:1.7.x")

    // Architecture
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.x")
    implementation("com.google.dagger:hilt-android:2.51.x")
    ksp("com.google.dagger:hilt-android-compiler:2.51.x")   // KSP, pas kapt

    // Accès fichiers / SAF
    implementation("androidx.documentfile:documentfile:1.0.1")
}
```

---

## 3. Point d'entrée

### Mode "Ouvrir avec" (principal)

SwipePlayer n'a pas d'explorateur de fichiers intégré. L'utilisateur utilise un
gestionnaire de fichiers externe et choisit "Ouvrir avec → SwipePlayer".

### Mode lancement depuis l'icône (FEAT-010/011/012)

Quand l'app est lancée depuis le lanceur, elle ouvre **`HomeActivity`** qui affiche
l'écran d'accueil avec trois onglets :

- **Collections** (défaut) : dossiers de vidéos, groupés par BUCKET_ID MediaStore.
  Cliquer un dossier affiche ses vidéos. Cliquer une vidéo ouvre le player avec
  navigation swipe pour toutes les vidéos du même dossier.
- **Vidéos** : liste plate de toutes les vidéos du stockage (scan MediaStore complet).
- **Parcourir** : explorateur de fichiers interne (voir §15).

L'écran d'accueil comporte un bandeau en haut avec l'icône de l'app et "SwipePlayer".
Les onglets sont accessibles par tap sur la NavigationBar ou par swipe gauche/droite
(HorizontalPager).

Sélectionner une vidéo lance `PlayerActivity` via `Intent(ACTION_VIEW, uri)`.
`PlayerActivity` se termine si lancée sans URI (`intent.data == null`).

### Déclaration du manifest (intent filters)

```xml
<activity
    android:name=".ui.PlayerActivity"
    android:exported="true"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
    android:theme="@style/Theme.SwipePlayer.Fullscreen"
    android:launchMode="singleTask">

    <!-- MIME type matching (principal) -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="content" android:mimeType="video/*" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="file" android:mimeType="video/*" />
    </intent-filter>

    <!-- Path pattern matching par extension (fallback quand MIME type absent) -->
    <!-- Un intent-filter par extension : .mp4, .mkv, .avi, .mov, .wmv, .webm,
         .flv, .m4v, .3gp, .ts, .mpg/.mpeg — pour file:// et content://.
         Permet à SwipePlayer d'apparaître dans les choosers qui n'envoient pas
         de MIME type (ex: FX File Explorer). -->
</activity>
```

### Logique de traitement de l'intent

1. Recevoir l'URI du fichier vidéo depuis l'intent.
2. Demander les permissions nécessaires (voir §12).
3. Lister tous les fichiers vidéo du **même répertoire parent** via la chaîne
   de fallback :
   a. SAF DocumentFile (URIs avec permission arborescence)
   b. MediaStore par RELATIVE_PATH sur tous les volumes (couvre les cartes SD)
   c. `File.listFiles()` via le chemin extrait de l'URI (nécessite
      `MANAGE_EXTERNAL_STORAGE` sur Android 11+)
4. Extensions supportées : `.mp4`, `.mkv`, `.avi`, `.mov`, `.wmv`, `.flv`,
   `.webm`, `.m4v`, `.3gp`, `.ts`, `.mpg`, `.mpeg`.
5. Trier la liste par nom de fichier (ordre alphabétique naturel, insensible à la casse).
6. Charger l'état persisté pour la vidéo (position, zoom, format — voir §15).

---

## 4. Architecture du lecteur vidéo

### Configuration ExoPlayer

```kotlin
val player = ExoPlayer.Builder(context)
    .setRenderersFactory(
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)   // Fallback SW si HW échoue (BUG-005)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
    )
    .setLoadControl(DefaultLoadControl.Builder()  // Buffers : 5s min / 30s max / 1s avant lecture
        .setBufferDurationsMs(5_000, 30_000, 1_000, 2_000)
        .build())
    .setPlaybackLooper(sharedLooper)          // Looper partagé entre les instances (requis si LoadControl partagé)
    .build()
```

**Deux instances ExoPlayer maximum** : une en lecture (current), une en pré-chargement (next).
Toutes les instances doivent utiliser le même `Looper` de lecture via `setPlaybackLooper()`
pour pouvoir partager le même `DefaultLoadControl`.

### Gestion des erreurs de décodage

Si le décodeur matériel ET le décodeur logiciel échouent tous les deux :
- Toast "Codec non supporté — passage à la vidéo suivante"
- Skip automatique après 2 secondes
- La vidéo est conservée dans la playlist (peut être retentée)

### Formats supportés

Tous les formats supportés par le décodeur du device (HW ou SW). Au minimum :
H.264, H.265/HEVC, VP9, AV1. Conteneurs : MP4, MKV, AVI, MOV, WebM, FLV, TS, 3GP.

### Gestion audio

- Pistes audio multiples : détection et sélection possible (menu réglages).
- Sous-titres : détection des pistes intégrées ET fichiers `.srt`/`.ass`/`.ssa`
  externes dans le même répertoire (même nom de base que la vidéo).
  Les fichiers externes sont passés au `MediaItem.Builder` via `SubtitleConfiguration`
  avant le `prepare()` de l'ExoPlayer (BUG-018).

---

## 5. Navigation par swipe — Système TikTok

### Concept

- **Swipe vers le haut** (zone centrale) → vidéo suivante aléatoire
- **Swipe vers le bas** (zone centrale) → retour dans l'historique

### Algorithme de navigation

```
État interne :
- playlistVideos: List<VideoFile>     // Toutes les vidéos du répertoire
- history: MutableList<VideoFile>     // Historique ordonné des vidéos consultées
- currentIndex: Int                   // Position dans l'historique

Swipe vers le haut (vidéo suivante) :
  SI currentIndex < history.lastIndex : avancer dans l'historique
  SINON : choisir une vidéo aléatoire non vue
    SI toutes vues : réinitialiser le pool (sauf la vidéo actuelle)

Swipe vers le bas (vidéo précédente) :
  SI currentIndex > 0 : reculer dans l'historique
  SINON : rebond visuel, pas de changement

Fin de vidéo (STATE_ENDED) :
  SI playlist > 1 vidéo : onSwipeUp() automatique (vidéo aléatoire suivante)
  SI mode fichier unique : seekTo(0) + play() (relecture depuis le début)

peekNext : dès que la vidéo courante démarre, pré-sélectionner et pré-charger
  la prochaine sans avancer currentIndex.
```

### Implémentation technique

`VerticalPager` avec 3 pages logiques (0=précédente, 1=courante, 2=suivante)
et reset silencieux après chaque transition :

```kotlin
val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
var isNavigating by remember { mutableStateOf(false) }  // CRO-027 : anti double-navigation

LaunchedEffect(pagerState.currentPage) {
    if (isNavigating) return@LaunchedEffect
    when (pagerState.currentPage) {
        0 -> { isNavigating = true; viewModel.onSwipeDown(); pagerState.scrollToPage(1); isNavigating = false }
        2 -> { isNavigating = true; viewModel.onSwipeUp();   pagerState.scrollToPage(1); isNavigating = false }
    }
}
```

Le flag `isNavigating` empêche les doubles navigations si l'animation de page est interrompue par un second geste rapide.

### Zones de geste

```
|  15%  |         70%         |  15%  |
| LUMI  |    ZONE SWIPE/TAP   |  SON  |
```

---

## 6. Interface utilisateur

### Mode plein écran

Toujours en plein écran (immersive sticky mode). Barres système masquées.
Contrôles apparaissent/disparaissent par tap au centre.

### Layout général

```
┌─────────────────────────────────────────────────────┐
│  [←]  Nom du fichier vidéo.mp4                      │  <- BARRE SUPÉRIEURE
│                                                     │
│                                                     │
│ L     [10s<]   [▶/||]   [>10s]                   V  │  <- CONTRÔLES CENTRAUX
│ U                                                O  │
│ M                                                L  │
│                                                     │
│  00:12:34 ════════●══════════════ 01:45:22          │  <- BARRE DE PROGRESSION
│  [1x]   [⚙]   [⛶]   [rot]                          │  <- BARRE D'OUTILS
└─────────────────────────────────────────────────────┘
```

### 6.1 Barre supérieure

| Élément | Comportement |
|---|---|
| Bouton retour | `finish()` → retour au gestionnaire de fichiers |
| Titre | Nom du fichier (sans chemin). 16sp, blanc, ellipsis si trop long. |

### 6.2 Contrôles centraux

- **Retour 10s** / **Play/Pause** / **Avance 10s**
- **Double tap gauche** → -10s avec feedback visuel (cercles + "-10s")
- **Double tap droit** → +10s avec feedback visuel (cercles + "+10s")

### 6.3 Réglage luminosité (bande gauche 15%)

Swipe vertical → `WindowManager.LayoutParams.screenBrightness` (0.0f–1.0f).
Feedback : barre verticale + icône soleil jaune. Local à l'app uniquement.
**Dead zone** : 20dp minimum avant activation (évite les déclenchements accidentels).

**Le slider de luminosité s'affiche dès que le swipe débute, indépendamment
de la visibilité des contrôles principaux (BUG-011).** Il est rendu hors du
bloc `AnimatedVisibility` des contrôles.

### 6.4 Réglage volume (bande droite 15%)

Swipe vertical → `player.volume` (0.0f–1.0f).
Feedback : barre verticale + icône haut-parleur bleu.
**Dead zone** : 20dp minimum avant activation (même règle que la luminosité).

**Le slider de volume s'affiche dès que le swipe débute, indépendamment
de la visibilité des contrôles principaux (BUG-011).** Il est rendu hors du
bloc `AnimatedVisibility` des contrôles.

### 6.5 Barre de progression (style Netflix)

- Couleur remplie : rouge `#E50914`
- Buffer : gris `#80FFFFFF`
- Fond : gris `#40FFFFFF`
- Timecodes : `MM:SS` ou `HH:MM:SS` si durée ≥ 1h. Police monospace 14sp.
- Tap / drag → seek

### 6.6 Barre d'outils

| Bouton | Fonction | Comportement |
|---|---|---|
| `[1x]` | Vitesse | `DropdownMenu` : 0.25x, 0.33x, 0.5x, 0.75x, 1x, 1.5x, 2x, 3x, 4x (FEAT-007) |
| `[⚙]` | Réglages | `ModalBottomSheet` : piste audio, sous-titres, info décodeur |
| `[⛶]` | Format | `DropdownMenu` avec 7 modes (voir §6.7) |
| `[rot]` | Orientation | Cycle : Auto → Paysage → Portrait |

**Note** : les menus dropdown (vitesse, format) et le BottomSheet (réglages)
suspendent le timer auto-hide pendant qu'ils sont ouverts.

### 6.7 Modes d'affichage vidéo (FEAT-002)

| Mode | Comportement |
|---|---|
| **Adapter** (défaut) | Ratio natif conservé, bandes noires si nécessaire |
| **Remplir** | Remplit l'écran, ratio conservé, bords rognés |
| **Étirer** | Remplit l'écran en déformant (ignore le ratio) |
| **100%** | 1 pixel vidéo = 1 pixel écran (peut dépasser l'écran) |
| **1:1** | Force un ratio carré (déformation) |
| **3:4** | Force le ratio 3:4 portrait (déformation) |
| **16:9** | Force le ratio 16:9 paysage (déformation) |

Mode par défaut : **Adapter**.

---

## 7. Gestes tactiles — Récapitulatif

| Zone | Geste | Action |
|---|---|---|
| Centre | Tap simple | Toggle contrôles. Auto-hide après 4s. |
| Centre | Double tap gauche | -10s |
| Centre | Double tap droit | +10s |
| Centre | Swipe vertical haut | Vidéo suivante |
| Centre | Swipe vertical bas | Vidéo précédente |
| Centre | Swipe horizontal | Seek temporel (FEAT-008) — 100% largeur = 100s ; image vidéo mise à jour en temps réel (CLOSEST_SYNC) pendant le geste, seek exact appliqué au relâchement |
| Centre | Pinch | Zoom/dézoom (1x–50x, continu) |
| Bande gauche | Swipe vertical | Luminosité |
| Bande droite | Swipe vertical | Volume |
| Barre progression | Tap/drag | Seek |

### Règles de priorité

1. **Pinch** (2 doigts) a la priorité absolue.
2. Le **zoom actif n'empêche pas le swipe** de navigation (BUG-007 — spec révisée).
3. **Mouvement initialement horizontal** sur la zone centrale → mode **seek horizontal**
   (FEAT-008) : vidéo en pause, indicateur affiché, seek appliqué au relâchement.
   Annule la détection de swipe vidéo vertical.
4. **Swipe vidéo** nécessite ≥ 80dp de déplacement + vélocité minimum ≥ 200dp/s (BUG-017).
5. **Double tap** : fenêtre de 200ms après le premier tap.
6. Les **taps consommés par les boutons** (ControlsOverlay) ne déclenchent pas
   le toggle des contrôles (`awaitFirstDown(requireUnconsumed = true)`).

---

## 8. Gestion du cycle de vie et performance

### Pré-chargement des vidéos

```
Vidéo courante (N)      : ExoPlayer actif, en lecture
Vidéo suivante (peekNext): ExoPlayer prêt, première frame décodée, en pause
Vidéo précédente        : libérée immédiatement après le swipe
```

Au swipe, `swapToNext()` est utilisé si `nextPlayer` correspond à la vidéo cible
(BUG-012). Sinon, un nouveau player est créé. Avant d'attacher le SurfaceView au
nouveau player, il est détaché de l'ancien (BUG-013).

### Mémoire

- Maximum 2 instances ExoPlayer simultanées.
- `player.clearVideoSurfaceView(sv)` puis `player.release()` pour libérer sans fuite.

### Gestion des interruptions

| Événement | Comportement |
|---|---|
| Appel téléphonique | Pause (audio focus) |
| Débranchement écouteurs | Pause (`ACTION_AUDIO_BECOMING_NOISY`) |
| App en arrière-plan | Pause |
| Retour au premier plan | Reprise si était en lecture |
| Écran éteint | Pause, libération partielle |
| Rotation | `configChanges` — pas de recréation d'activité |
| Fin de vidéo | Vidéo aléatoire suivante, ou relecture si mode fichier unique (FEAT-003) |

### Écran allumé (FEAT-006)

`FLAG_KEEP_SCREEN_ON` activé sur la fenêtre tant que `isPlaying == true`.
Désactivé automatiquement à la pause, à l'arrêt ou à la fermeture de l'app.

### Audio Focus

- `AUDIOFOCUS_LOSS` → pause
- `AUDIOFOCUS_LOSS_TRANSIENT` → pause, reprise à `AUDIOFOCUS_GAIN`
- `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` → volume à 30% (le volume utilisateur est sauvegardé)
- `AUDIOFOCUS_GAIN` → reprise + restauration du volume utilisateur (pas de reset à 1.0)

---

## 9. Thème et style visuel

### Couleurs

| Usage | Hex |
|---|---|
| Fond contrôles (semi-transparent) | `#80000000` |
| Barre de progression | `#E50914` (rouge Netflix) |
| Buffer | `#80FFFFFF` |
| Fond barre | `#40FFFFFF` |
| Texte / icônes | `#FFFFFF` |
| Indicateur luminosité | `#FFC107` |
| Indicateur volume | `#2196F3` |

### Icône de l'application (FEAT-004)

Icône fournie par l'utilisateur (`swipePlayer_icon.png`, 1024×1024).

- **Legacy** (`mipmap-*/ic_launcher.png`) : 48/72/96/144/192 px selon la densité.
- **Round** (`mipmap-*/ic_launcher_round.png`) : même tailles, découpe circulaire.
- **Adaptive foreground** (`drawable-*/ic_launcher_foreground.png`) : icône à 90%
  du canvas 108dp, centré sur fond transparent.
- **Adaptive background** (`drawable/ic_launcher_background.xml`) : couleur unie
  `#0B2F57` (bleu marine issu du dégradé de l'icône).

### Animations

| Animation | Durée | Courbe |
|---|---|---|
| Apparition/disparition contrôles | 200ms | FastOutSlowIn |
| Transition swipe | 300ms | DecelerateInterpolator |
| Feedback double tap | 500ms (100ms in + 400ms out) | — |
| Barre luminosité/volume | 100ms | Linear |

---

## 10. Structure du projet

```
app/src/main/java/com/swipeplayer/
├── SwipePlayerApp.kt
├── di/AppModule.kt
├── data/
│   ├── VideoFile.kt               // VideoFile, FolderInfo, StorageVolumeInfo, SubtitleFile
│   ├── VideoRepository.kt         // Listing multi-stratégie (SAF, MediaStore, File)
│   ├── PlaybackHistory.kt         // + playbackOrder RANDOM/ALPHABETICAL (FEAT-013)
│   └── VideoStateStore.kt         // Persistance position/zoom/format + playbackOrder
├── player/
│   ├── VideoPlayerManager.kt      // Max 2 instances, looper partagé
│   ├── PlayerConfig.kt            // MAX_ZOOM_SCALE=50f, enableDecoderFallback=true
│   └── AudioFocusManager.kt
└── ui/
    ├── PlayerActivity.kt          // FLAG_KEEP_SCREEN_ON, finish() si intent.data==null
    ├── PlayerViewModel.kt
    ├── PlayerUiState.kt           // DisplayMode 7 modes, PlaybackOrder RANDOM/ALPHABETICAL
    ├── home/
    │   ├── HomeActivity.kt        // LAUNCHER, @AndroidEntryPoint
    │   ├── HomeViewModel.kt       // scan, browse, volumes, hidden files
    │   ├── HomeUiState.kt         // HomeTab, storageVolumes, showHiddenFiles
    │   ├── screen/
    │   │   ├── HomeScreen.kt      // HorizontalPager 3 tabs + HomeTopBar (bitmap icon)
    │   │   ├── CollectionsScreen.kt
    │   │   ├── VideosScreen.kt
    │   │   └── FileBrowserScreen.kt // volume picker + hidden files toggle
    │   └── components/
    │       ├── VideoListItem.kt
    │       └── CollectionListItem.kt
    ├── screen/
    │   └── PlayerScreen.kt
    ├── components/
    │   ├── VideoSurface.kt        // BoxWithConstraints + requiredSize par mode
    │   ├── ControlsOverlay.kt     // Timer suspendu si menu/sheet ouvert
    │   ├── TopBar.kt
    │   ├── CenterControls.kt
    │   ├── ProgressBar.kt
    │   ├── ToolBar.kt
    │   ├── FormatSelector.kt      // DropdownMenu 7 modes (FEAT-002)
    │   ├── SpeedSelector.kt
    │   ├── SettingsSheet.kt       // + section ordre de lecture (FEAT-013)
    │   ├── BrightnessControl.kt
    │   ├── VolumeControl.kt
    │   └── DoubleTapFeedback.kt
    └── gesture/
        ├── GestureHandler.kt      // requireUnconsumed=true, zoomScale en () -> Float
        ├── SwipeDetector.kt
        └── PinchZoomHandler.kt
```

---

## 11. Persistance de l'état vidéo (FEAT-001)

### Comportement

À chaque ouverture d'une vidéo, l'app charge l'état sauvegardé (s'il existe) :
- **Position** : reprend là où on s'était arrêté
- **Zoom** : restaure le niveau de zoom
- **Format** : restaure le mode d'affichage

Si aucun état sauvegardé → position 0, zoom 1x, format Adapter.

### Règles de sauvegarde

- **Déclencheur** : au swipe (changement de vidéo) et à la mise en arrière-plan
- **Clé** : nom de fichier seul (sans chemin) — survit aux déplacements de fichier
- **Position 0 si** : visionnage < 5s OU position > durée - 5s (fin de vidéo)
- **Stockage** : `SharedPreferences` ("swipeplayer_video_prefs")

### Zoom

Plage : **1x à 50x** (pratiquement illimité). Le zoom n'empêche pas le swipe de navigation.

---

## 12. Cas limites

| Situation | Comportement |
|---|---|
| Répertoire avec 1 seule vidéo | Swipe désactivé (rebond visuel). Fin de vidéo → relecture depuis le début. |
| Codec non supporté (HW+SW) | Toast "Codec non supporté — passage à la vidéo suivante", skip automatique après 2s |
| Fichier supprimé entre listing et lecture | Toast, retrait de la playlist, skip |
| content:// sans accès répertoire | Mode fichier unique, swipe désactivé, toast |
| Vidéo > 4h | Format HH:MM:SS, aucune limitation |
| Toutes vidéos vues | Reset pool (sauf vidéo courante), continuer aléatoire |
| Lancement depuis l'icône | HomeActivity : écran d'accueil Collections/Vidéos/Parcourir |

---

## 13. Permissions

```xml
<!-- Lecture des vidéos media (API 33+) -->
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<!-- Fallback API < 33 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<!-- Accès File API aux cartes SD (Android 11+) pour lister les répertoires
     quand ni SAF ni MediaStore ne peuvent indexer les fichiers -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<!-- Maintien de l'écran allumé pendant la lecture -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

`MANAGE_EXTERNAL_STORAGE` n'est **pas** demandé proactivement au runtime (BUG-020).
L'utilisateur peut l'accorder manuellement dans les paramètres de l'app si nécessaire
pour le listing des cartes SD. Sans cette permission, `VideoRepository` utilise
automatiquement les stratégies SAF → MediaStore → mode fichier unique.

---

## 14. Critères de qualité et performance

| Métrique | Cible |
|---|---|
| Temps de lancement (intent → première frame) | < 500ms |
| Temps de transition swipe | < 300ms |
| Frame drops pendant le swipe | 0 |
| Mémoire (lecture normale) | < 150 MB |
| Mémoire (transition swipe, 2 players) | < 250 MB |
| Latence seek | < 200ms |

---

## 15. Notes d'implémentation

1. **`SurfaceView` wrappé dans `AndroidView`** pour le rendu vidéo uniquement.
   Tout l'overlay UI en Compose pur.

2. **`VerticalPager` 3 pages logiques** (0=précédente, 1=courante, 2=suivante)
   avec reset silencieux `scrollToPage(1)` après chaque transition.

3. **Un seul `pointerInput` modifier** pour tous les gestes. Routage par position X
   du premier pointeur. `awaitFirstDown(requireUnconsumed = true)` pour ne pas
   interférer avec les boutons de l'overlay.

4. **Pinch-to-zoom** : `zoomScale` et `canSwipeDown` passés en `() -> Float` / `() -> Boolean`
   (lambdas) dans le `gestureHandler` pour ne pas être des clés du `pointerInput` — évite
   le redémarrage du handler (et la réinitialisation de `lastTapTimeMs`) à chaque frame
   du pinch ou à chaque swipe. Zoom actif (scale > 1x) **n'empêche pas** le swipe de navigation.

5. **Décodeur** : `setEnableDecoderFallback(true)` — fallback SW si HW échoue.
   Toast + skip uniquement si aucun décodeur disponible.

6. **Looper partagé** : toutes les instances `ExoPlayer` créées avec
   `setPlaybackLooper(sharedLooper)` pour partager le même `DefaultLoadControl`
   (requis par Media3 1.5+). Le `HandlerThread` démarre en `lazy` (première
   création de player seulement).

9. **VideoPlayerManager Singleton immortel** : `close()` supprimé. Le scope et le thread
   du manager vivent toute la durée du processus. `releaseAll()` libère les players
   et nullifie les callbacks dans `ViewModel.onCleared()` via
   `runBlocking(Dispatchers.Main.immediate)` (thread-safe car `onCleared` s'exécute
   sur le main thread). La `SurfaceView` est stockée en `WeakReference` pour éviter
   de retenir l'Activity.

10. **Navigation thread-safe** : `onSwipeUp()`, `onSwipeDown()` et `onSourceErrorDetected()`
    utilisent un `Mutex` (`navigationMutex`) pour sérialiser les accès à `PlaybackHistory`.
    `PlaybackHistory.peekBack()` remplace le pattern fragile `navigateBack()+navigateForward()`.

11. **Callbacks ExoPlayer sur main thread** : les `Player.Listener` s'exécutent sur le
    `playbackThread` (looper partagé). Tous les callbacks (`onPlayerError`, `onTracksChanged`,
    `onPlaybackStateChanged`) sont dispatchés via `Handler(Looper.getMainLooper()).post { }`
    avant d'invoquer les lambdas du ViewModel.

12. **Clés SharedPreferences** : séparateur `::` dans les clés (ex: `"nom_video.mp4::pos"`)
    pour éviter les collisions avec des fichiers dont le nom contient `_pos`, `_zoom`, etc.

7. **Auto-hide des contrôles** : `LaunchedEffect` suspendu si un `DropdownMenu`
   (vitesse, format) ou le `ModalBottomSheet` (réglages) est ouvert. Le `BottomSheet`
   est rendu hors du bloc `AnimatedVisibility` pour survivre à la disparition
   des contrôles.

8. **Tri naturel** : `video2.mp4` < `video10.mp4`. Tokenisation regex `(\d+)|(\D+)`.

9. **`MediaSession`** (`media3-session`) : obligatoire Android 12+ pour les apps
   média de premier plan. Contrôles dans la notification système.

10. **Persistance état vidéo** (`VideoStateStore`) : clé = nom de fichier, valeurs
    = position/zoom/format. Chargé au démarrage de chaque vidéo, sauvegardé
    au swipe et à la mise en arrière-plan.

11. **Listing de répertoire** pour les URI FileProvider (`content://authority/path`) :
    chaîne de fallback SAF → MediaStore RELATIVE_PATH (tous volumes) →
    `File.listFiles()` (nécessite `MANAGE_EXTERNAL_STORAGE`).
    `EXTERNAL_CONTENT_URI` ne couvre pas les cartes SD sur Android 10+ —
    utiliser `MediaStore.getExternalVolumeNames()` + `getContentUri(volumeName)`.

13. **Icône app dans Compose** (BUG-026) : ne pas utiliser `painterResource(R.mipmap.ic_launcher)`.
    Sur API 26+, les icônes adaptatives (XML `<adaptive-icon>`) ne sont pas supportées par
    `painterResource`. Utiliser `PackageManager.getApplicationIcon()` + rendu Bitmap à la place.

14. **Ordre de lecture** (FEAT-013) : `PlaybackOrder.RANDOM` (défaut) ou `ALPHABETICAL`.
    Persisté dans `VideoStateStore` (clé `"global::playback_order"`).
    Réglage accessible dans `SettingsSheet` → section "Ordre de lecture".
    `PlaybackHistory.playbackOrder` contrôle le choix de `pickNext()` entre `pickRandom()`
    et `pickSequential()` (index courant + 1 mod taille, lookup par identité puis URI).

16. **Fin de vidéo → animation TikTok** (BUG-027) : quand `onPlaybackStateChanged` détecte
    `STATE_ENDED`, le ViewModel émet un `SharedFlow<Unit> autoSwipeUpEvent` au lieu d'appeler
    `onSwipeUp()` directement. `PlayerScreen` collecte ce flow dans un `LaunchedEffect(Unit)`
    et exécute `doSwipeUpAnimation()` — exactement la même animation ping-pong que le geste
    manuel. La vidéo terminée monte, la vidéo suivante arrive par le bas. Le mode single-file
    reste géré directement dans le ViewModel (seekTo(0) + play()).

15. **Explorateur de fichiers** (FEAT-014) : l'onglet "Parcourir" de `HomeActivity` utilise
    `browseDirectory(dir, showHiddenFiles)` de `VideoRepository`.
    - Volumes disponibles détectés via `StorageManager.getStorageVolumes()` (API 30+)
      ou `Environment + getExternalFilesDirs()` (API 26-29).
    - `showHiddenFiles = false` par défaut : filtrage des noms commençant par ".".
    - Le sélecteur de volume remplace l'affichage du chemin complet en haut de liste.
