# BUG-020 — UX agressive : MANAGE_EXTERNAL_STORAGE demandé systématiquement

**Statut** : FIXED
**Sévérité** : MAJEUR
**Découvert via** : Code review 2026-03-12 (CR-011)

## Symptôme

À chaque ouverture d'une vidéo sur Android 11+, l'app envoie l'utilisateur
vers l'écran Paramètres pour MANAGE_EXTERNAL_STORAGE, même si MediaStore/SAF
peuvent déjà lister le répertoire.

## Cause racine

`handleIntent()` demandait MANAGE_EXTERNAL_STORAGE de manière proactive,
avant même d'essayer MediaStore ou SAF.

## Fix appliqué

Suppression du bloc proactif MANAGE_EXTERNAL_STORAGE dans `handleIntent()`.
La permission reste dans le manifest et peut être accordée manuellement par
l'utilisateur. VideoRepository utilise ses stratégies de fallback normalement.
Si toutes échouent, le mode single-file s'applique.

## Section spec impactée

swipeplayer-specs.md §13 (Permissions : MANAGE_EXTERNAL_STORAGE)
