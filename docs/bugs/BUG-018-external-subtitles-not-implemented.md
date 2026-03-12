# BUG-018 — Sous-titres externes non implémentés (TASK-032 incomplet)

**Statut** : FIXED
**Sévérité** : MAJEUR
**Découvert via** : Code review 2026-03-12 (CR-009)

## Symptôme

Les fichiers .srt/.ass/.ssa dans le même répertoire que la vidéo ne sont pas
détectés ni proposés dans le menu Réglages.

## Cause racine

TASK-032 était marqué [x] mais seuls les sous-titres intégrés (player.currentTracks)
étaient gérés. Aucun code de détection des fichiers externes.

## Fix appliqué

- `VideoFile.kt` : ajout de `data class SubtitleFile(uri, mimeType, name)`.
- `VideoRepository` : ajout de `findExternalSubtitles(video)` — scanne le
  répertoire pour les fichiers .srt/.ass/.ssa avec le même nom de base.
- `VideoPlayerManager.preparePlayer()` : accepte `subtitleConfigurations`
  et les passe au `MediaItem.Builder`.
- `PlayerViewModel.startPlayback()` : appelle `findExternalSubtitles` et
  les passe à `preparePlayer`. `triggerPeekNext()` les inclut aussi dans le preload.

## Section spec impactée

swipeplayer-specs.md §4 (Sous-titres), §10 (Structure projet)
