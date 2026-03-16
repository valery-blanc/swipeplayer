# FEAT-011 — Bandeau header dans l'ecran d'accueil

## Statut : IN PROGRESS

## Contexte

L'ecran d'accueil (HomeActivity) n'a pas de bandeau en haut.
Ajouter un bandeau avec l'icone de l'app et le nom "SwipePlayer".

## Comportement attendu

- TopAppBar en haut du Scaffold dans HomeScreen
- Icone de l'app (ic_launcher) a gauche
- Texte "SwipePlayer" a cote de l'icone
- Fond #1A1A1A (meme couleur que la NavigationBar)
- Texte blanc, taille 20sp, FontWeight.Bold

## Spec technique

### Modification
- `ui/home/screen/HomeScreen.kt` : ajouter `topBar = { HomeTopBar() }` au Scaffold

### HomeTopBar composable (prive dans HomeScreen.kt)
```kotlin
TopAppBar(
    title = { Row { Image(ic_launcher); Text("SwipePlayer") } },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
)
```
Icone : `painterResource(R.mipmap.ic_launcher)`, 32dp, clip RoundedCornerShape(8.dp)

## Impact
- `HomeScreen.kt` uniquement
