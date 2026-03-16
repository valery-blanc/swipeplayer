# FEAT-010 — Ecran d'accueil : Videos, Collections, Parcourir

## Statut : DONE

## Contexte

Quand l'app est lancee depuis son icone (sans URI video), afficher un ecran
d'accueil complet avec trois modes de navigation :
- **Collections** (onglet par defaut) : dossiers contenant des videos
- **Videos** : toutes les videos du stockage
- **Parcourir** : navigateur de fichiers interne

Remplace FEAT-005 (simple bouton "Choisir une video").

## Comportement attendu

### Navigation vers le lecteur
Quelle que soit la page depuis laquelle l'utilisateur choisit une video,
le lecteur s'ouvre sur cette video avec les swipes haut/bas actifs pour
toutes les autres videos du meme dossier.
Si des autorisations sont necessaires, les demander avant d'afficher le contenu.

### Onglet Collections (defaut)
- Liste les dossiers contenant au moins une video (scan MediaStore)
- Chaque item : nom du dossier, nombre de videos, thumbnail de la premiere video
- Tap sur un dossier : affiche les videos du dossier (sous-ecran)
- Dans la sous-liste : tap sur une video -> ouvre le lecteur avec swipe sur
  tout le dossier

### Onglet Videos
- Liste toutes les videos du stockage (scan MediaStore, tri naturel par nom)
- Chaque item : thumbnail, nom du fichier, duree, dossier parent
- Tap -> ouvre le lecteur avec swipe sur toutes les videos du meme dossier

### Bouton Parcourir
- Troisieme element dans la barre de navigation (icone dossier)
- Ouvre un navigateur de fichiers interne (pas le picker systeme)
- Affiche les dossiers et les videos. Navigation en avant/arriere dans l'arbre
- Tap sur un dossier : entre dans ce dossier
- Tap sur une video : ouvre le lecteur (swipe sur les videos du dossier courant)
- Bouton retour : remonte d'un niveau. Depuis la racine : retour aux onglets.
- Racine du browser : /storage/emulated/0 (ou la racine accessible)

## Spec technique

### Architecture

Nouvelle activite `HomeActivity` (LAUNCHER) distincte de `PlayerActivity` (VIEW).

```
ui/home/
  HomeActivity.kt          @AndroidEntryPoint, LAUNCHER intent filter
  HomeViewModel.kt         @HiltViewModel, scan MediaStore
  HomeUiState.kt           data classes : HomeTab, CollectionItem, VideoItem, BrowseState
  screen/
    HomeScreen.kt          NavigationBar (Collections, Videos, Parcourir)
    CollectionsScreen.kt   liste des collections + sous-ecran videos d'un dossier
    VideosScreen.kt        liste plate de toutes les videos
    FileBrowserScreen.kt   navigateur interne avec back-stack
  components/
    VideoListItem.kt       item video (thumbnail + metadata)
    CollectionListItem.kt  item collection (thumbnail + nb videos)
```

### HomeViewModel

Scan MediaStore a l'initialisation (via `viewModelScope`) :
- `loadCollections()` : requete MediaStore.Video.Media, regroupement par
  `BUCKET_DISPLAY_NAME` / `BUCKET_ID`, tri naturel sur le nom du bucket
- `loadAllVideos()` : requete MediaStore.Video.Media, tri naturel sur le nom
- `browseDirectory(path)` : lister fichiers/dossiers via File API
  (fallback : MediaStore si permission File non disponible)
- `navigateUp()` : pop le back-stack du browser
- `selectVideo(videoFile)` : emit l'URI vers HomeActivity qui lance PlayerActivity

### Permissions

Avant tout scan : verifier READ_MEDIA_VIDEO (API >= 33) /
READ_EXTERNAL_STORAGE (API < 33). Demander si manquant.
Pour le browser : MANAGE_EXTERNAL_STORAGE prefere (demander si absent avec
explication) ; fallback : MediaStore si refuse.

### Lancement du lecteur

```kotlin
val intent = Intent(Intent.ACTION_VIEW, videoUri).apply {
    setClass(context, PlayerActivity::class.java)
}
startActivity(intent)
```

PlayerActivity.handleIntent() gere deja le listing du dossier -> swipe actif.

### AndroidManifest

- Ajouter `<activity android:name=".ui.home.HomeActivity">` avec intent-filter
  MAIN + LAUNCHER
- Retirer le filtre MAIN + LAUNCHER de `PlayerActivity`
  (PlayerActivity garde uniquement les filtres VIEW)

### PlayerScreen

Retirer l'ecran "Choisir une video" de FEAT-005 (currentVideo == null) :
HomeActivity le remplace. Quand PlayerActivity demarre sans URI valide,
elle se ferme immediatement (finish()).

## Impact sur l'existant

| Fichier | Changement |
|---------|-----------|
| `AndroidManifest.xml` | +HomeActivity LAUNCHER, -PlayerActivity LAUNCHER |
| `PlayerActivity.kt` | Retirer le chemin "sans URI" (finish() si intent.data == null) |
| `PlayerScreen.kt` | Retirer le bloc FEAT-005 (currentVideo == null picker) |
| `VideoRepository.kt` | +scanAllVideos(), +scanCollections() |

## Definition de "Collection"

Un dossier = une collection si et seulement si il contient au moins une video
directement (non recursif). Tri alphabetique naturel sur le nom du dossier.

## Thumbnail

Utiliser `MediaStore.Video.Thumbnails` ou
`ContentResolver.loadThumbnail()` (API 29+) pour les thumbnails.
Afficher un placeholder (icone video) si le thumbnail echoue.

## Design

- Theme sombre (fond #121212, texte blanc)
- NavigationBar en bas avec 3 items : Collections (icone film), Videos (icone
  play), Parcourir (icone dossier)
- Collections est l'onglet actif par defaut au lancement
- Meme charte couleur que le lecteur (#E50914 pour les accents)
