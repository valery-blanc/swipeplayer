# BUG-005 — "Codec non supporté" : vidéo ignorée au lieu d'utiliser le décodeur logiciel

**Statut :** FIXED
**Priorité :** P1 — des vidéos lisibles sont ignorées sans raison

---

## Symptôme

Certaines vidéos affichent le toast "Codec non supporté, passage à la vidéo suivante"
et sont automatiquement ignorées après 2 secondes, même si l'appareil possède un
décodeur logiciel capable de les lire.

## Étapes de reproduction

1. Avoir dans le répertoire une vidéo dont le codec n'est pas supporté par le décodeur
   matériel (ex. VP9 profil 2, HEVC 10-bit, AV1 sur certains appareils)
2. Ouvrir l'app et naviguer jusqu'à cette vidéo
3. → Toast "Codec non supporté" + saut automatique vers la suivante

## Analyse de la cause racine

`PlayerConfig.renderersFactory()` configure ExoPlayer en mode **HW+ strict** :

```kotlin
DefaultRenderersFactory(context)
    .setEnableDecoderFallback(false)   // interdit tout fallback
    .setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF)
```

`setEnableDecoderFallback(false)` empêche ExoPlayer d'essayer un autre décodeur
(matériel ou logiciel) quand le décodeur prioritaire échoue. La chaîne de décodage
s'arrête immédiatement à la première erreur d'initialisation du décodeur matériel,
ce qui déclenche `PlaybackException.ERROR_CODE_DECODER_INIT_FAILED` →
`onCodecFailure` callback → toast + skip.

La spec originale imposait le mode HW+ pour des raisons de performance, mais
en pratique cela rend inutilisables des vidéos parfaitement lisibles via le
décodeur logiciel intégré à Android (MediaCodec software path).

## Fichiers impactés

- `player/PlayerConfig.kt:91-94` — `renderersFactory()`
- `player/VideoPlayerManager.kt` — utilise `PlayerConfig.renderersFactory()`

## Section spec impactée

**ExoPlayer — HW+ Mode** (spec à réviser) :
> "Hardware-only decoding, no software fallback. Use `setEnableDecoderFallback(false)`"

Cette contrainte de spec est trop restrictive. La correction modifie le comportement
par rapport à la spec — la spec doit être mise à jour en conséquence.

## Correction

Activer le fallback logiciel dans `PlayerConfig.renderersFactory()` :

```kotlin
DefaultRenderersFactory(context)
    .setEnableDecoderFallback(true)    // fallback HW→SW si HW échoue
    .setExtensionRendererMode(EXTENSION_RENDERER_MODE_OFF)
```

Avec `setEnableDecoderFallback(true)`, ExoPlayer essaie automatiquement le
décodeur logiciel (ou un autre décodeur matériel) quand le décodeur prioritaire
échoue. Si le fallback réussit, aucun callback d'erreur n'est déclenché — la vidéo
joue normalement. Le toast et le skip restent actifs uniquement si AUCUN décodeur
(HW ou SW) ne peut lire le fichier.
