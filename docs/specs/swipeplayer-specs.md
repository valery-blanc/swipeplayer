# SwipePlayer — Spécifications Techniques Complètes

## 1. Vue d'ensemble du projet

**Nom de l'application :** SwipePlayer
**Plateforme :** Android (API minimum 26 / Android 8.0, cible API 35)
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
| Navigation swipe | **Compose Foundation — `VerticalPager`** (HorizontalPager de Accompanist ou Foundation) | Swipe natif performant avec pré-chargement des pages adjacentes |
| Architecture | **MVVM** avec Jetpack ViewModel + StateFlow | Réactivité, séparation des responsabilités, survie aux rotations |
| DI | **Hilt** | Injection de dépendances standard Android |
| Build | **Gradle Kotlin DSL** | Standard moderne |

### Dépendances principales

```kotlin
// build.gradle.kts (app)
dependencies {
    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.5.x")
    implementation("androidx.media3:media3-ui:1.5.x")
    implementation("androidx.media3:media3-common:1.5.x")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.7.x")
    implementation("androidx.compose.foundation:foundation:1.7.x") // VerticalPager
    implementation("androidx.compose.material3:material3:1.3.x")
    implementation("androidx.compose.material:material-icons-extended:1.7.x")

    // Architecture
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.x")
    implementation("com.google.dagger:hilt-android:2.51.x")

    // Gestion des gestes avancés
    implementation("androidx.compose.foundation:foundation:1.7.x")
}
```

---

## 3. Point d'entrée — Intent "Ouvrir avec"

SwipePlayer n'a **pas** d'explorateur de fichiers intégré. L'utilisateur utilise un explorateur de fichiers externe (Files by Google, Solid Explorer, etc.) et choisit "Ouvrir avec → SwipePlayer".

### Déclaration du manifest

```xml
<activity
    android:name=".ui.PlayerActivity"
    android:exported="true"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
    android:theme="@style/Theme.SwipePlayer.Fullscreen"
    android:launchMode="singleTask">

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="video/*" />
    </intent-filter>

    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="file" />
        <data android:scheme="content" />
        <data android:mimeType="video/*" />
    </intent-filter>
</activity>
```

### Logique de traitement de l'intent

1. Recevoir l'URI du fichier vidéo depuis l'intent.
2. Résoudre le chemin réel du fichier.
3. Lister tous les fichiers vidéo du **même répertoire parent** (extensions : `.mp4`, `.mkv`, `.avi`, `.mov`, `.wmv`, `.flv`, `.webm`, `.m4v`, `.3gp`, `.ts`, `.mpg`, `.mpeg`).
4. Trier la liste par nom de fichier (ordre alphabétique naturel, insensible à la casse).
5. Positionner le lecteur sur la vidéo sélectionnée.

### Gestion des permissions

- Demander `READ_MEDIA_VIDEO` (API 33+) ou `READ_EXTERNAL_STORAGE` (API < 33) si nécessaire pour lister le répertoire.
- Si l'URI est de type `content://`, utiliser le `ContentResolver` et `DocumentFile` pour accéder au répertoire parent via SAF (Storage Access Framework).

---

## 4. Architecture du lecteur vidéo

### Configuration ExoPlayer — Décodage HW+

Le mode **HW+** signifie : décodage matériel forcé, pas de fallback logiciel. Comme MX Player en mode HW+.

```kotlin
val player = ExoPlayer.Builder(context)
    .setRenderersFactory(
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(false) // Pas de fallback SW = mode HW+
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
    )
    .setLoadControl(
        DefaultLoadControl.Builder()
            .setBufferDurations(
                /* minBufferMs = */ 5_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .build()
    )
    .build()
```

### Formats supportés

Tous les formats supportés par le décodeur matériel du device. Au minimum : H.264, H.265/HEVC, VP9, AV1 (si supporté par le hardware). Conteneurs : MP4, MKV, AVI, MOV, WebM, FLV, TS, 3GP.

### Gestion audio

- Pistes audio multiples : détection et sélection possible (via menu réglages).
- Sous-titres : détection des pistes intégrées et fichiers `.srt`/`.ass`/`.ssa` externes dans le même répertoire.

---

## 5. Navigation par swipe — Système TikTok

### Concept

Le swipe vertical permet de naviguer entre les vidéos du répertoire. Le comportement est inspiré de TikTok :

- **Swipe vers le haut** (au centre de l'écran) → vidéo **suivante** (aléatoire parmi les vidéos non vues du répertoire)
- **Swipe vers le bas** (au centre de l'écran) → vidéo **précédente** (retour dans l'historique de visualisation)

### Algorithme de navigation

```
État interne :
- playlistVideos: List<VideoFile>     // Toutes les vidéos du répertoire
- history: MutableList<VideoFile>     // Historique ordonné des vidéos consultées
- currentIndex: Int                   // Position dans l'historique

Au lancement :
  history = [vidéo ouverte par l'intent]
  currentIndex = 0

Swipe vers le haut (vidéo suivante) :
  SI currentIndex < history.lastIndex :
      // On est dans l'historique, on avance
      currentIndex++
      Lire history[currentIndex]
  SINON :
      // On est en bout d'historique, choisir une nouvelle vidéo
      candidats = playlistVideos - history.toSet()
      SI candidats est vide :
          // Toutes les vidéos ont été vues, recommencer le cycle
          candidats = playlistVideos - setOf(history.last())
      nextVideo = candidats.random()
      history.add(nextVideo)
      currentIndex = history.lastIndex
      Lire nextVideo

Swipe vers le bas (vidéo précédente) :
  SI currentIndex > 0 :
      currentIndex--
      Lire history[currentIndex]
  SINON :
      // Déjà au début, feedback visuel (rebond) — pas de changement
```

### Implémentation technique du swipe

Utiliser `VerticalPager` de Jetpack Compose Foundation :

```kotlin
val pagerState = rememberPagerState(
    initialPage = LARGE_OFFSET, // Offset pour permettre le swipe dans les deux directions
    pageCount = { Int.MAX_VALUE }
)

VerticalPager(
    state = pagerState,
    beyondBoundsPageCount = 1, // Pré-charge 1 page adjacente pour fluidité
    modifier = Modifier.fillMaxSize()
) { page ->
    VideoPlayerPage(
        video = viewModel.getVideoForPage(page),
        isCurrentPage = page == pagerState.currentPage
    )
}
```

### Zones de swipe

Le swipe ne doit fonctionner que dans la **zone centrale** de l'écran. Les bandes latérales sont réservées aux réglages luminosité/son. Définir les zones ainsi :

```
|  15%  |         70%         |  15%  |
| LUMI  |    ZONE SWIPE       |  SON  |
| NOSITÉ|  (et contrôles)     |       |
```

- **Bande gauche (15% de la largeur)** : réglage luminosité (swipe vertical)
- **Bande droite (15% de la largeur)** : réglage volume (swipe vertical)
- **Zone centrale (70%)** : swipe pour changer de vidéo + contrôles de lecture

### Transition visuelle du swipe

Animation identique à TikTok :
- La vidéo actuelle glisse vers le haut/bas.
- La nouvelle vidéo arrive depuis le bas/haut.
- Durée de l'animation : ~300ms, courbe de décélération.
- La vidéo précédente est mise en pause et libérée de la mémoire après la transition.
- La nouvelle vidéo commence à jouer dès qu'elle est visible à plus de 50%.

---

## 6. Interface utilisateur — Style Netflix

### Mode plein écran

L'application fonctionne **toujours en plein écran** (immersive sticky mode). Les barres système sont masquées. Les contrôles de l'application apparaissent/disparaissent par tap au centre de l'écran.

### Layout général

```
┌─────────────────────────────────────────────────────┐
│  [←]  Nom du fichier vidéo.mp4                      │  ← BARRE SUPÉRIEURE
│                                                     │
│                                                     │
│ ▲                                                ▲  │
│ │                                                │  │
│ L     [⟲10s]    [▶/❚❚]    [10s⟳]                V  │  ← CONTRÔLES CENTRAUX
│ U                                                O  │
│ M                                                L  │
│ │                                                │  │
│ ▼                                                ▼  │
│                                                     │
│  00:12:34 ════════════●══════════════ 01:45:22      │  ← BARRE DE PROGRESSION
│                                                     │
│  [1x]    [⚙]    [⛶]    [🔄]                        │  ← BARRE D'OUTILS
└─────────────────────────────────────────────────────┘
```

### 6.1 Barre supérieure

| Élément | Comportement |
|---|---|
| Bouton retour `[←]` | Ferme SwipePlayer et retourne à l'app précédente (explorateur de fichiers) via `finish()` |
| Titre | Nom du fichier vidéo en cours (sans le chemin, avec extension). Texte tronqué avec ellipsis si trop long. Police : sans-serif, blanc, taille 16sp, ombre portée pour lisibilité |

### 6.2 Contrôles centraux (zone de lecture)

Les contrôles sont **superposés** à la vidéo et apparaissent/disparaissent avec un fade in/out de 200ms.

| Élément | Comportement |
|---|---|
| **Retour 10s** | Icône circulaire avec "10" inscrit. Tap = recule de 10 secondes. Animation de rotation vers la gauche au tap. |
| **Play/Pause** | Grande icône centrale (48dp). Bascule lecture/pause. Icône : ▶ (play) ou ❚❚ (pause). |
| **Avance 10s** | Icône circulaire avec "10" inscrit. Tap = avance de 10 secondes. Animation de rotation vers la droite au tap. |

**Double tap rapide** (comme MX Player / Netflix) :
- Double tap côté gauche de la zone centrale → recule 10s
- Double tap côté droit de la zone centrale → avance 10s
- Afficher un feedback visuel (cercles concentriques + texte "-10s" ou "+10s")

### 6.3 Réglage luminosité (bande gauche, 15%)

- **Geste** : swipe vertical sur la bande gauche de l'écran.
- **Swipe vers le haut** = augmenter la luminosité.
- **Swipe vers le bas** = diminuer la luminosité.
- **Feedback visuel** : icône luminosité (soleil) + barre verticale semi-transparente affichée pendant le geste.
- **Plage** : luminosité de l'écran de 0% à 100%.
- **Méthode** : modifier `WindowManager.LayoutParams.screenBrightness` (0.0f à 1.0f).
- Le réglage n'affecte que SwipePlayer, pas la luminosité système.

### 6.4 Réglage volume (bande droite, 15%)

- **Geste** : swipe vertical sur la bande droite de l'écran.
- **Swipe vers le haut** = augmenter le volume.
- **Swipe vers le bas** = diminuer le volume.
- **Feedback visuel** : icône volume (haut-parleur) + barre verticale semi-transparente affichée pendant le geste.
- **Plage** : volume média de 0 à max stream volume.
- **Méthode** : `AudioManager.setStreamVolume(STREAM_MUSIC, ...)`.

### 6.5 Barre de progression (style Netflix)

- **Position** : en bas de l'écran, au-dessus de la barre d'outils.
- **Composants** :
  - Temps écoulé à gauche (format `HH:MM:SS` ou `MM:SS` si < 1h)
  - Barre de progression (seekbar) au centre, remplie à gauche de la position, buffer visible en gris plus clair
  - Durée totale à droite (même format)
- **Interaction** :
  - Tap sur la barre → seek à la position
  - Drag du curseur → seek en temps réel avec preview du timecode
  - Pendant le drag, afficher le timecode au-dessus du curseur
- **Style** : couleur de la barre = rouge Netflix (#E50914), curseur = rond blanc, fond = gris 40% opacité

### 6.6 Barre d'outils (sous la progression)

Quatre icônes alignées à gauche, espacées de 32dp :

| Icône | Fonction | Comportement |
|---|---|---|
| **Vitesse** `[1x]` | Vitesse de lecture | Tap → menu popup : 0.25x, 0.5x, 0.75x, 1x, 1.25x, 1.5x, 1.75x, 2x, 3x, 4x. L'icône affiche la vitesse actuelle. La vitesse sélectionnée est appliquée via `player.setPlaybackSpeed()`. |
| **Réglages** `[⚙]` | Panneau de réglages | Tap → bottom sheet avec : sélection piste audio, sélection sous-titres, sélection décodeur (HW+ par défaut). |
| **Format** `[⛶]` | Mode d'affichage vidéo | Tap → cycle entre les modes : **Adapter** (fit, barres noires) → **Remplir** (crop pour remplir l'écran) → **Étirer** (stretch, ignore le ratio) → **100%** (taille native, pixel perfect). Le mode actuel est indiqué par l'icône et un toast. Mode par défaut : Adapter. |
| **Orientation** `[🔄]` | Verrouillage orientation | Tap → cycle : **Auto** (suit le capteur) → **Paysage** (forcé) → **Portrait** (forcé). Le mode actuel est indiqué par l'icône. Mode par défaut : Auto. |

---

## 7. Gestes tactiles — Récapitulatif complet

| Zone | Geste | Action |
|---|---|---|
| Centre | Tap simple | Afficher / masquer les contrôles (toggle). Auto-hide après 4 secondes d'inactivité. |
| Centre | Double tap gauche | Reculer de 10 secondes |
| Centre | Double tap droit | Avancer de 10 secondes |
| Centre | Swipe vertical vers le haut | Vidéo suivante (aléatoire ou historique) |
| Centre | Swipe vertical vers le bas | Vidéo précédente (historique) |
| Centre | Pinch (deux doigts) | Zoom/dézoom de la vidéo (de 1x à 4x). Geste continu et fluide. Le zoom se fait autour du point central du pinch. |
| Bande gauche | Swipe vertical | Réglage luminosité |
| Bande droite | Swipe vertical | Réglage volume |
| Barre de progression | Tap / drag | Seek dans la vidéo |

### Priorité et désambiguïsation des gestes

1. Le **pinch** (deux doigts) a la priorité absolue sur tout autre geste.
2. Les gestes **swipe latéral** (bandes gauche/droite) sont détectés dès que le point de départ du toucher est dans la zone correspondante.
3. Le **swipe vertical central** nécessite un déplacement minimum de 80dp et une vélocité minimum pour être déclenché (pour éviter les faux positifs pendant le scroll de la seekbar ou les taps).
4. Le **double tap** est détecté avec un délai de 200ms après le premier tap. Si pas de second tap → tap simple.
5. Les contrôles visibles consomment les taps (boutons play, seek, etc.) avant la détection du tap simple pour hide.

---

## 8. Gestion du cycle de vie et performance

### Pré-chargement des vidéos

Pour garantir la fluidité du swipe, pré-initialiser un second ExoPlayer pour la vidéo adjacente :

```
Page actuelle (N) : ExoPlayer actif, en lecture
Page N+1 (prochaine probable) : ExoPlayer prêt, première frame décodée, en pause
Page N-1 (précédente) : libéré de la mémoire
```

### Gestion mémoire

- Maximum 2 instances ExoPlayer simultanées.
- Libérer l'instance non adjacente immédiatement après le swipe.
- Utiliser `player.setVideoSurfaceView(null)` avant release pour éviter les fuites.

### Gestion des interruptions

| Événement | Comportement |
|---|---|
| Appel téléphonique entrant | Pause automatique, reprise au retour |
| Débranchement écouteurs | Pause automatique (AudioManager.ACTION_AUDIO_BECOMING_NOISY) |
| App en arrière-plan | Pause automatique |
| Retour au premier plan | Reprise automatique si était en lecture |
| Écran éteint | Pause, libération partielle des ressources |
| Rotation de l'écran | Géré par `configChanges` dans le manifest, pas de recréation d'activité |

### Audio Focus

Demander l'audio focus au démarrage. Réagir aux changements :
- `AUDIOFOCUS_LOSS` → pause
- `AUDIOFOCUS_LOSS_TRANSIENT` → pause, reprise au gain
- `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` → baisser le volume à 30%
- `AUDIOFOCUS_GAIN` → reprise

---

## 9. Thème et style visuel

### Couleurs

| Usage | Couleur | Hex |
|---|---|---|
| Fond des contrôles | Noir semi-transparent | `#80000000` |
| Barre de progression (remplie) | Rouge Netflix | `#E50914` |
| Barre de progression (buffer) | Gris clair | `#80FFFFFF` |
| Barre de progression (fond) | Gris foncé | `#40FFFFFF` |
| Texte principal | Blanc | `#FFFFFF` |
| Texte secondaire | Gris clair | `#B3FFFFFF` |
| Icônes | Blanc | `#FFFFFF` |
| Indicateur luminosité/volume | Jaune/Bleu doux | `#FFC107` / `#2196F3` |

### Typographie

- Police : système par défaut (Roboto)
- Titre : 16sp, medium weight
- Timecodes : 14sp, regular, monospace
- Labels (vitesse, etc.) : 12sp

### Animations

| Animation | Durée | Courbe |
|---|---|---|
| Apparition/disparition contrôles | 200ms | FastOutSlowIn |
| Transition swipe entre vidéos | 300ms | DecelerateInterpolator |
| Feedback double tap (+/-10s) | 500ms | Fade in 100ms + fade out 400ms |
| Changement de luminosité/volume bar | 100ms | Linear |

---

## 10. Structure du projet

```
app/
├── src/main/
│   ├── java/com/swipeplayer/
│   │   ├── SwipePlayerApp.kt                  // Application class (Hilt)
│   │   ├── di/
│   │   │   └── AppModule.kt                   // Hilt modules
│   │   ├── data/
│   │   │   ├── VideoRepository.kt             // Listing et accès fichiers vidéo
│   │   │   ├── VideoFile.kt                   // Data class (uri, name, path, duration)
│   │   │   └── PlaybackHistory.kt             // Gestion historique navigation
│   │   ├── player/
│   │   │   ├── VideoPlayerManager.kt          // Création/gestion instances ExoPlayer
│   │   │   ├── PlayerConfig.kt                // Config décodeur HW+, buffers
│   │   │   └── AudioFocusManager.kt           // Gestion audio focus
│   │   ├── ui/
│   │   │   ├── PlayerActivity.kt              // Activity unique, gère l'intent
│   │   │   ├── PlayerViewModel.kt             // ViewModel principal
│   │   │   ├── screen/
│   │   │   │   └── PlayerScreen.kt            // Composable racine (VerticalPager)
│   │   │   ├── components/
│   │   │   │   ├── VideoSurface.kt            // Surface de rendu vidéo
│   │   │   │   ├── ControlsOverlay.kt         // Overlay des contrôles
│   │   │   │   ├── TopBar.kt                  // Barre titre + retour
│   │   │   │   ├── CenterControls.kt          // Play/pause, ±10s
│   │   │   │   ├── ProgressBar.kt             // Seekbar + timecodes
│   │   │   │   ├── ToolBar.kt                 // Vitesse, réglages, format, orientation
│   │   │   │   ├── BrightnessControl.kt       // Slider vertical luminosité
│   │   │   │   ├── VolumeControl.kt           // Slider vertical volume
│   │   │   │   ├── DoubleTapFeedback.kt       // Animation ±10s
│   │   │   │   ├── SpeedSelector.kt           // Popup vitesse
│   │   │   │   └── SettingsSheet.kt           // Bottom sheet réglages
│   │   │   └── gesture/
│   │   │       ├── GestureHandler.kt          // Détection et routage des gestes
│   │   │       ├── SwipeDetector.kt           // Logique swipe vertical
│   │   │       └── PinchZoomHandler.kt        // Logique pinch-to-zoom
│   │   └── util/
│   │       ├── FileUtils.kt                   // Extensions fichiers vidéo
│   │       ├── TimeFormat.kt                  // Formatage timecodes
│   │       └── OrientationManager.kt          // Gestion rotation écran
│   ├── res/
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   ├── colors.xml
│   │   │   └── themes.xml                     // Thème fullscreen immersif
│   │   └── drawable/                          // Icônes vectorielles
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro
```

---

## 11. Cas limites et comportements spéciaux

| Situation | Comportement attendu |
|---|---|
| Répertoire avec une seule vidéo | Swipe désactivé (rebond visuel élastique sans changement). |
| Fichier vidéo corrompu ou non lisible (HW+) | Afficher un toast "Impossible de lire cette vidéo (codec non supporté)", passer automatiquement à la suivante après 2 secondes. |
| Fichier supprimé entre-temps | Toast d'erreur, retirer de la playlist et passer à la suivante. |
| URI content:// sans accès au répertoire | Lire uniquement le fichier reçu, désactiver le swipe, afficher un toast expliquant la limitation. |
| Vidéo très longue (>4h) | Aucune limitation, afficher HH:MM:SS. |
| Vidéo verticale en mode paysage | Respecter le mode d'affichage choisi (adapter = letterboxing, remplir = crop). |
| Toutes les vidéos du répertoire ont été vues | Réinitialiser le pool de vidéos "non vues" (sauf la vidéo actuelle) et continuer la sélection aléatoire. |

---

## 12. Permissions requises

```xml
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<!-- Fallback pour API < 33 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<!-- Empêcher la mise en veille pendant la lecture -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## 13. Critères de qualité et performance

| Métrique | Cible |
|---|---|
| Temps de lancement (intent → première frame) | < 500ms |
| Temps de transition swipe (dernière frame → première frame nouvelle vidéo) | < 300ms |
| Frame drops pendant le swipe | 0 |
| Utilisation mémoire (lecture normale) | < 150 MB |
| Utilisation mémoire (transition swipe, 2 players) | < 250 MB |
| Latence seek (tap sur barre de progression) | < 200ms |
| Consommation batterie | Comparable à MX Player |

---

## 14. Pour le développeur IA — Notes d'implémentation

1. **Ne PAS utiliser `AndroidView` pour le player vidéo dans Compose** si possible. Préférer `SurfaceView` wrappé dans `AndroidView` uniquement pour le rendu vidéo — le reste de l'UI doit être en Compose pur pour la fluidité des animations de swipe.

2. **Le `VerticalPager` doit utiliser des clés stables** pour éviter les recompositions inutiles. Chaque page est identifiée par l'index dans l'historique.

3. **Le swipe et les gestes de luminosité/volume doivent être gérés dans un seul `pointerInput` modifier** avec une logique de routage basée sur la position X du premier pointeur. Cela évite les conflits de gestes.

4. **Le pinch-to-zoom doit modifier un `graphicsLayer` avec `scaleX`/`scaleY`** sur la surface vidéo uniquement, pas sur l'overlay des contrôles.

5. **Pour le mode HW+** : si le décodeur matériel échoue, ne PAS fallback sur le logiciel. Afficher un message d'erreur clair.

6. **Les contrôles doivent utiliser `AnimatedVisibility`** avec `fadeIn`/`fadeOut` pour les transitions, et un `LaunchedEffect` avec un timer de 4 secondes pour l'auto-hide.

7. **Tester impérativement** avec des vidéos 4K HEVC, des fichiers MKV avec sous-titres multiples, et des vidéos verticales filmées au téléphone.
