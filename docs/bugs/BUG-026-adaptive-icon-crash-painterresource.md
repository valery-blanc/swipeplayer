# BUG-026 — Crash au lancement : painterResource() sur icône adaptive

**Status:** FIXED
**Sévérité:** Critique (crash au lancement)
**Découvert lors de:** FEAT-011 (bandeau header écran d'accueil)
**Session:** 2026-03-16

---

## Symptôme

L'app crash immédiatement au lancement après l'implémentation de FEAT-011.

```
java.lang.IllegalArgumentException: Only VectorDrawables and rasterized asset types
are supported ex. PNG, JPG, WEBP
    at androidx.compose.ui.res.PainterResources_androidKt.painterResource
    at com.swipeplayer.ui.home.screen.HomeScreenKt.HomeTopBar(HomeScreen.kt:153)
```

## Cause racine

`HomeTopBar()` utilisait `painterResource(R.mipmap.ic_launcher)` pour afficher
l'icône de l'application. Sur API 26+, `mipmap-anydpi/ic_launcher.xml` est une
**icône adaptive** (XML `<adaptive-icon>`) qui surcharge les PNG des autres densités.

`painterResource()` de Compose ne sait traiter que les VectorDrawable ou les formats
raster (PNG/JPG/WEBP). Il ne peut pas rendre un `<adaptive-icon>`.

## Fix appliqué

Remplacer `painterResource()` par un rendu bitmap via `PackageManager` :

```kotlin
val context = LocalContext.current
val appIconBitmap = remember {
    val drawable = context.packageManager.getApplicationIcon(context.packageName)
    Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { bmp ->
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
    }
}
Image(bitmap = appIconBitmap.asImageBitmap(), ...)
```

`getApplicationIcon()` renvoie un `Drawable` que le système Android sait rendre
(y compris les adaptive icons), puis on le dessine sur un `Bitmap` avant de
passer à Compose.

## Fichier modifié

- `app/src/main/java/com/swipeplayer/ui/home/screen/HomeScreen.kt`

## Règle à retenir

**Ne jamais utiliser `painterResource(R.mipmap.ic_launcher)` dans Compose.**
Toujours utiliser `PackageManager.getApplicationIcon()` + rendu Bitmap
pour afficher l'icône de l'app elle-même.
