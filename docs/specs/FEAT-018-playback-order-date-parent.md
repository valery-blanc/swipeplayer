# FEAT-018 — Ordres de lecture : Par date + Aléatoire répertoire parent

**Statut** : DONE

## Contexte
Deux nouveaux modes de lecture en plus de RANDOM et ALPHABETICAL.

## Comportement

### BY_DATE — Lecture par ordre de date de modification
- Les vidéos sont jouées dans l'ordre décroissant de date de modification (de la plus récente à la plus ancienne), en cycle.
- `VideoFile.lastModified` : timestamp en ms, rempli depuis `MediaStore.DATE_MODIFIED` (×1000) ou `File.lastModified()`.

### PARENT_RANDOM — Aléatoire depuis le répertoire parent
- Pool étendu : toutes les vidéos des répertoires frères du répertoire courant (même niveau hiérarchique).
- **Les répertoires cachés sont inclus** (nom commençant par `.` ou contenant un fichier `.nomedia`).
- Exemple : si on est dans `/Films/Séries/S01/`, le pool inclut les vidéos de S01, S02, S03, etc.
- Le pool est chargé de façon asynchrone au changement de mode.
- Si le répertoire parent ne peut pas être résolu (URI SAF sans chemin), dégradation silencieuse vers RANDOM.

## Spec technique
- `VideoFile` : ajout de `lastModified: Long = -1L`.
- `VideoRepository` : projection MediaStore étendue (`DATE_MODIFIED`), nouvelle méthode `listSiblingDirectoryVideos(uri)`.
- `PlaybackOrder` enum : `BY_DATE`, `PARENT_RANDOM`.
- `PlaybackHistory` : `siblingPlaylist`, `setSiblingPlaylist()`, `pickByDate()`, `pickFromSiblings()`.
- `PlayerViewModel` : stocke `intentUri`, charge les frères en async sur `PARENT_RANDOM`.
- `SettingsSheet` : 2 nouvelles lignes dans la section "Ordre de lecture".
- `VideoStateStore` : compatible (enum sauvegardé par nom, nouveaux noms lus par `valueOf` avec fallback RANDOM).

## Impact
- `VideoFile.kt`, `VideoRepository.kt`, `PlayerUiState.kt`, `PlaybackHistory.kt`,
  `PlayerViewModel.kt`, `SettingsSheet.kt`
