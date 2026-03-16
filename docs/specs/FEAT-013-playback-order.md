# FEAT-013 — Ordre de lecture : alphabetique ou aleatoire

## Statut : IN PROGRESS

## Contexte

Actuellement la video suivante est toujours choisie aleatoirement parmi
les videos non vues du meme dossier. Certains utilisateurs preferent
lire les videos dans l'ordre alphabetique (naturel).

## Comportement attendu

- Dans les parametres (SettingsSheet, mode lecteur video) : section
  "Ordre de lecture" avec 2 options radio :
    - "Aleatoire" (comportement actuel, defaut)
    - "Alphabetique" : prochaine video = suivante dans la liste triee
      naturellement, cyclique (apres la derniere, reprend la premiere)
- Le parametre est persistant (SharedPreferences) et s'applique a
  toutes les sessions.
- En mode alphabetique, le "swipe bas" (video precedente) remonte dans
  l'historique comme d'habitude (pas de changement).
- En mode alphabetique, si l'utilisateur est deja en train de naviguer
  en mode aleatoire (il est a mi-historique), le changement de mode
  prend effet a la prochaine video seulement.

## Spec technique

### PlaybackOrder enum (PlayerUiState.kt)
```kotlin
enum class PlaybackOrder { RANDOM, ALPHABETICAL }
```

### PlayerUiState.kt
Ajouter : `val playbackOrder: PlaybackOrder = PlaybackOrder.RANDOM`

### PlaybackHistory.kt
- Ajouter `var playbackOrder: PlaybackOrder = PlaybackOrder.RANDOM`
- Modifier `pickNext()` (renommer pickRandom en pickNext) :
  - RANDOM : comportement actuel (random depuis unseenSet)
  - ALPHABETICAL : `playlist[(indexOfCurrent + 1) % playlist.size]`
    ou `indexOfCurrent` est la position de la video courante dans playlist.

### PlayerViewModel.kt
- Injecter `VideoStateStore` (ou SharedPreferences) pour la persistance
- `onPlaybackOrderChange(order: PlaybackOrder)` :
  - update `_uiState`
  - update `history.playbackOrder`
  - re-calculer peekNext (annuler l'ancien peek et en calculer un nouveau)
  - persister dans SharedPreferences via VideoStateStore ou AppModule
- Charger la valeur au demarrage dans `onIntentReceived`

### Persistance
Utiliser `SharedPreferences` avec la cle "playback_order".
Injecter `SharedPreferences` via AppModule (deja disponible) ou
utiliser directement `context.getSharedPreferences`.

### SettingsSheet.kt
Ajouter une section "Ordre de lecture" avec 2 RadioButton :
- "Aleatoire"
- "Alphabetique"

Signature mise a jour :
```kotlin
fun SettingsSheet(
    ...
    playbackOrder: PlaybackOrder,
    onPlaybackOrderChange: (PlaybackOrder) -> Unit,
    ...
)
```

## Impact
| Fichier | Changement |
|---------|-----------|
| `PlayerUiState.kt` | +PlaybackOrder enum, +playbackOrder field |
| `PlaybackHistory.kt` | +playbackOrder field, pickNext() |
| `PlayerViewModel.kt` | +onPlaybackOrderChange(), persistance |
| `SettingsSheet.kt` | +section ordre de lecture |
| `PlayerScreen.kt` | passer playbackOrder + callback a SettingsSheet |
| `AppModule.kt` | +SharedPreferences provider si absent |
