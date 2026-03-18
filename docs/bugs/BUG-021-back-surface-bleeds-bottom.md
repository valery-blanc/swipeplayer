# BUG-021 — Back surface bleeds into view at the bottom (format mismatch)

## Statut
`FIXED`

## Symptôme
Quand deux vidéos consécutives ont des formats différents (ex. 16:9 et 4:3),
un morceau de la vidéo précédente ou suivante reste visible au bas de l'écran
pendant la lecture de la vidéo courante.

## Cause racine
`configuration.screenHeightDp.dp.toPx()` exclut la hauteur des barres système
(status bar, nav bar) sur Android en mode fullscreen edge-to-edge. La surface
arrière était positionnée à `translationY = screenHeightPx`, mais le Box
`fillMaxSize()` s'étend jusqu'au bord physique de l'écran — plus loin que
`screenHeightPx`. Une bande de la surface arrière (typiquement 80–100 px)
restait donc visible en bas.

## Fix appliqué
`PlayerScreen.kt` : remplacement de `configuration.screenHeightDp.dp.toPx()`
par une mesure réelle via `onSizeChanged`. Les variables `screenWidthPx` et
`screenHeightPx` sont désormais des `mutableStateOf(0f)` mis à jour à la
première pose du layout.

```kotlin
var screenWidthPx by remember { mutableStateOf(0f) }
var screenHeightPx by remember { mutableStateOf(0f) }

Box(
    modifier = modifier
        .fillMaxSize()
        .onSizeChanged { size ->
            screenWidthPx = size.width.toFloat()
            screenHeightPx = size.height.toFloat()
        }
        ...
)
```

## Fichiers modifiés
- `app/src/main/java/com/swipeplayer/ui/screen/PlayerScreen.kt`
