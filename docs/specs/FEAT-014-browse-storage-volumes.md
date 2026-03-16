# FEAT-014 — Ecran Parcourir : volumes de stockage + fichiers cachés

**Status:** IN PROGRESS
**Priorité:** Normale
**Session:** 2026-03-16

---

## Contexte

L'écran "Parcourir" ne navigue que dans le stockage interne principal.
Impossible d'accéder à la carte SD ou aux autres volumes montés.
Il n'y a pas non plus de moyen d'afficher ou masquer les fichiers/dossiers cachés.

---

## Comportement demandé

1. **Sélecteur de volume** (en haut de l'écran Parcourir) :
   - Remplace l'affichage du chemin actuel
   - Menu déroulant (DropdownMenu) listant tous les volumes disponibles :
     - Stockage interne (toujours présent)
     - Carte SD / USB (si montés)
   - La sélection d'un volume réinitialise la navigation à la racine de ce volume

2. **Entrée ".."** :
   - Déjà implémentée, conservée telle quelle

3. **Menu "Fichiers cachés"** (icône en haut à droite) :
   - Bouton icône (VisibilityOff / Visibility) sur la même ligne que le sélecteur de volume
   - Bascule l'affichage des fichiers/dossiers dont le nom commence par "."
   - État persisté uniquement en mémoire (reset à chaque lancement)

---

## Spec technique

### StorageVolumeInfo (data/VideoFile.kt)
```kotlin
data class StorageVolumeInfo(
    val path: File,
    val name: String,
    val isRemovable: Boolean,
)
```

### VideoRepository.browseDirectory()
- Ajouter param `showHiddenFiles: Boolean = false`
- Si `false` : filtrer les dirs et fichiers dont `name.startsWith(".")`

### HomeUiState
- `+val showHiddenFiles: Boolean = false`
- `+val storageVolumes: List<StorageVolumeInfo> = emptyList()`

### HomeViewModel
- `loadStorageVolumes()` : via `StorageManager.getStorageVolumes()` (API 30+)
  ou `Environment.getExternalStorageDirectory()` + `context.getExternalFilesDirs(null)` (API 26-29)
- `onVolumeSelected(volume: StorageVolumeInfo)` : reset browseBackStack, navigate to volume.path
- `onToggleHiddenFiles()` : toggle `showHiddenFiles`, rebrowse le répertoire courant

### FileBrowserScreen
- Header fixe (hors LazyColumn) :
  - Gauche : TextButton avec nom du volume actuel + icône ChevronDown → DropdownMenu
  - Droite : IconButton avec icône Visibility/VisibilityOff
- Retire l'item "chemin" du début de la liste

---

## Impact sur l'existant

- `browseDirectory()` : signature change (nouveau param optionnel avec défaut)
- `HomeScreen.kt` : pass-through des nouveaux params vers FileBrowserScreen
- Aucun impact sur les autres écrans (Collections, Videos, Player)
