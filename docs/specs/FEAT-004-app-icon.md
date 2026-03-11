# FEAT-004 — Icône originale pour l'application

## Contexte

L'application utilise l'icône générique Android (robot vert). Elle doit avoir
une icône reconnaissable qui représente sa fonction.

## Design

- **Fond** : rouge #E50914 (couleur de marque de l'app, déjà utilisée pour
  la progress bar)
- **Avant-plan** : blanc
  - Triangle "lecture" (►) centré et large — symbole universel de lecture vidéo
  - Flèche chevron pointant vers le haut (∧) au-dessus du triangle
  - Flèche chevron pointant vers le bas (∨) en dessous du triangle
  → représente le swipe vertical qui est la fonctionnalité centrale de l'app

## Format technique

Icône adaptative Android (API 26+) :
- `res/drawable/ic_launcher_background.xml` — fond rouge uni
- `res/drawable/ic_launcher_foreground.xml` — vecteur play + chevrons (blanc)
- `res/mipmap-anydpi/ic_launcher.xml` — référence inchangée
- `res/mipmap-anydpi/ic_launcher_round.xml` — référence inchangée

Les .webp existants dans mipmap-* restent pour la compatibilité API < 26.
