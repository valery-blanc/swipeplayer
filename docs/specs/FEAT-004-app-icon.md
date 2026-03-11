# FEAT-004 — Icône originale pour l'application

## Statut : DONE

## Contexte

L'application utilisait l'icône générique Android (robot vert). L'utilisateur a
fourni un fichier PNG (`swipePlayer_icon.png`, 1024×1024) contenant le design
final de l'icône.

## Design de l'icône fournie

- **Forme** : rectangle arrondi avec coins adoucis
- **Fond** : dégradé du bleu marine foncé (`#0B2F57`) en haut-gauche vers le
  violet foncé (`#201C4E`) en bas-gauche
- **Contenu** : rectangle blanc arrondi centré simulant un écran vidéo, avec un
  triangle "lecture" coloré (cyan `#00C2C7`) entouré d'une aura dégradée
  (magenta → jaune)

## Implémentation technique

### Icônes legacy (lanceurs API < 26)

Générées par script Python (Pillow) depuis le PNG source, dans chaque dossier
`mipmap-*/` :

| Dossier | Taille |
|---|---|
| `mipmap-mdpi` | 48×48 px |
| `mipmap-hdpi` | 72×72 px |
| `mipmap-xhdpi` | 96×96 px |
| `mipmap-xxhdpi` | 144×144 px |
| `mipmap-xxxhdpi` | 192×192 px |

Fichiers : `ic_launcher.png` (carré) + `ic_launcher_round.png` (découpe circulaire).

Les anciens `.webp` ont été supprimés.

### Icône adaptative (API 26+)

- `mipmap-anydpi/ic_launcher.xml` — structure inchangée (foreground + background)
- **Background** : `drawable/ic_launcher_background.xml` → couleur unie `#0B2F57`
  (bleu marine du coin supérieur gauche de l'icône, pour que la couleur se
  prolonge sous la forme du launcher)
- **Foreground** : `drawable/ic_launcher_foreground.xml` (ancien vecteur)
  **supprimé** et remplacé par des PNG densité-spécifiques dans `drawable-*/` :

| Dossier | Taille (canvas 108dp) |
|---|---|
| `drawable-mdpi` | 108×108 px |
| `drawable-hdpi` | 162×162 px |
| `drawable-xhdpi` | 216×216 px |
| `drawable-xxhdpi` | 324×324 px |
| `drawable-xxxhdpi` | 432×432 px |

L'icône est mise à l'échelle à 90% du canvas adaptatif (centré), laissant une
marge de 5% de chaque côté — les bords de l'icône restent dans la safe zone
(66dp sur 108dp) quelle que soit la forme imposée par le launcher.

## Fichier source

`swipePlayer_icon.png` — 1024×1024 px, à conserver à la racine du projet.
