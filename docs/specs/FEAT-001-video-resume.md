# FEAT-001 — Persistance de l'état vidéo (position, zoom, format)

## Statut : DONE

## Contexte

Quand l'utilisateur reprend une vidéo déjà vue, elle repart du début avec le zoom
et le format par défaut. L'expérience attendue est que l'app mémorise où on s'était
arrêté et avec quels réglages visuels.

## Comportement attendu

### Lecture initiale (aucune donnée persistée)
- Position : 0 (début)
- Zoom : 1x
- Format : ADAPT (défaut)

### Reprise d'une vidéo déjà vue
- Position : dernière position enregistrée (en secondes)
- Zoom : dernier zoom enregistré
- Format : dernier format enregistré

### Sauvegarde
- Déclenché à : pause, swipe vers une autre vidéo, mise en arrière-plan, fermeture
- Clé : **nom de fichier seul** (sans chemin ni URI, pour survivre aux déplacements)
- Si position < 5s ou > durée-5s : sauvegarder 0 (considéré comme "vu en entier" → recommencer)

### Zoom illimité
- Retirer la limite supérieure MAX_ZOOM_SCALE (actuellement 4x)
- Permettre un zoom indéfini (pratiquement : clamp à 50x pour éviter les problèmes de rendu)

## Spécification technique

### Stockage
- `SharedPreferences` (nom : "swipeplayer_video_prefs")
- Format clé : `"state_${filename}"` où `filename = videoFile.name`
- Valeur : JSON compact `{"pos":1234,"zoom":2.5,"fmt":"FILL"}`

### Nouveau fichier : `data/VideoStateStore.kt`
- `@Singleton`, `@Inject constructor`
- `fun save(filename: String, positionMs: Long, zoom: Float, displayMode: DisplayMode)`
- `fun load(filename: String): VideoState?`
- `data class VideoState(val positionMs: Long, val zoom: Float, val displayMode: DisplayMode)`

### Intégration ViewModel
- Au démarrage de chaque vidéo : `videoStateStore.load(video.name)` → appliquer
- À la sauvegarde : `videoStateStore.save(name, positionMs, zoom, displayMode)`

## Impact sur l'existant

- `PlayerConfig.kt` : `MAX_ZOOM_SCALE` → 50f (ou retirer le clamp supérieur)
- `PlayerViewModel.kt` : injection de `VideoStateStore`, logique load/save
- `PlayerUiState.kt` : aucun changement (zoom et displayMode déjà dans le state)
- `AppModule.kt` : provider `VideoStateStore`
