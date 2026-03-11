# FEAT-005 — Lancement depuis l'icône : sélection d'une vidéo

## Contexte

Quand l'app est lancée depuis son icône (sans URI vidéo), elle affiche un écran
vide. Il faut proposer à l'utilisateur de choisir une vidéo via le sélecteur
de fichiers système.

## Comportement attendu

- Lancement depuis icône → écran "Choisir une vidéo" avec bouton
- L'utilisateur tape le bouton → sélecteur de fichiers système (ACTION_GET_CONTENT,
  video/*) s'ouvre
- L'utilisateur choisit une vidéo → lecture démarre normalement
- Le choix s'affiche à chaque ouverture depuis l'icône (pas de mémorisation)

## Spécification technique

### Détection du mode "sans vidéo"
`PlayerActivity.handleIntent()` retourne déjà tôt si `intent.data == null`.
`PlayerUiState.currentVideo == null && !isLoading` indique qu'aucune vidéo
n'est chargée.

### Écran de sélection (PlayerScreen)
Quand `currentVideo == null && !isLoading`, afficher à la place du pager :
- Fond noir
- Nom de l'app centré (grand texte blanc)
- Bouton "Choisir une vidéo" (icône dossier + texte)

### Sélecteur de fichiers
`rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` avec
type `video/*`. Quand l'URI est retourné, appeler `viewModel.onIntentReceived(uri)`.

Les permissions (READ_MEDIA_VIDEO, MANAGE_EXTERNAL_STORAGE) sont déjà gérées
par le flux existant dans PlayerActivity.handleIntent().

## Impact sur l'existant

- `PlayerScreen.kt` : condition sur currentVideo == null
- `PlayerActivity.kt` : aucun changement nécessaire (handleIntent déjà correct)
