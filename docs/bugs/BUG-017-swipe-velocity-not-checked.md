# BUG-017 — Vélocité minimum du swipe non vérifiée

**Statut** : FIXED
**Sévérité** : MAJEUR
**Découvert via** : Code review 2026-03-12 (CR-008)

## Symptôme

Un glissement très lent de 80dp déclenche le changement de vidéo.
Le geste involontaire est trop facile à déclencher.

## Cause racine

`SwipeDetector.detect()` avait le paramètre `velocityY` annoté
`@Suppress("UNUSED_PARAMETER")`. La vérification n'était pas implémentée.

## Fix appliqué

Implémentation de la vérification : `|velocityY| >= SWIPE_MIN_VELOCITY_DP * density`.
`SWIPE_MIN_VELOCITY_DP = 200f` est défini dans `PlayerConfig`.

## Section spec impactée

swipeplayer-specs.md §7 (Règles de priorité : swipe ≥ 80dp + vélocité minimum)
