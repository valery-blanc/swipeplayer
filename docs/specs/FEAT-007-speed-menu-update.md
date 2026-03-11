# FEAT-007 — Mise à jour du menu des vitesses de lecture

## Statut : DONE

## Contexte

Le menu des vitesses actuel propose : 0.25x, 0.5x, 0.75x, 1x, 1.25x, 1.5x, 1.75x, 2x, 3x, 4x.

L'utilisateur souhaite :
- **Ajouter** 0.33x (vitesse lente utile pour certains contenus)
- **Supprimer** 1.25x et 1.75x (valeurs intermédiaires jugées inutiles)

## Nouveau menu des vitesses

| Ordre | Valeur |
|---|---|
| 1 | 0.25x |
| 2 | 0.33x (nouveau) |
| 3 | 0.5x |
| 4 | 0.75x |
| 5 | 1x (défaut) |
| 6 | 1.5x |
| 7 | 2x |
| 8 | 3x |
| 9 | 4x |

**Total : 9 vitesses** (contre 10 précédemment).

## Comportement

- La vitesse active est mise en évidence dans le menu.
- Si la vitesse courante est 1.25x ou 1.75x (persistée d'une session précédente),
  revenir silencieusement à 1x.
- `player.setPlaybackParameters(PlaybackParameters(speed))` inchangé.

## Fichiers à modifier

- `app/src/main/java/com/swipeplayer/ui/components/SpeedSelector.kt`
  — mettre à jour la liste `SPEED_OPTIONS`

## Impact sur les specs

- `swipeplayer-specs.md` §6.6 — tableau des vitesses à mettre à jour
