# BUG-016 — ContentUriNoAccess émis pour toutes playlists à 1 vidéo

**Statut** : FIXED
**Sévérité** : MAJEUR
**Découvert via** : Code review 2026-03-12 (CR-005)

## Symptôme

L'état `PlayerError.ContentUriNoAccess` est émis même quand l'utilisateur
ouvre légitimement un répertoire qui contient une seule vidéo.

## Cause racine

La condition `singleFileMode = playlist.size == 1 || (playlist.size == 1 && ...)`
se réduit à `playlist.size == 1`, donc l'erreur s'affiche pour TOUT répertoire
à 1 vidéo, pas seulement les content:// sans accès répertoire.

## Fix appliqué

Détection du mode fallback via `playlist.first().uri == uri` : si l'URI
retournée par le repository est identique à l'URI d'entrée, c'est le fallback
buildFallbackVideoFile (aucun listing n'a fonctionné).

## Section spec impactée

swipeplayer-specs.md §12 (Cas limites : content:// sans accès répertoire)
