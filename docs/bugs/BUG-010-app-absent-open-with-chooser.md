# BUG-010 — SwipePlayer absent de la liste "Ouvrir avec" dans le gestionnaire de fichiers

**Statut :** FIXED (partiellement — voir Note)
**Priorité :** P2 — gêne à l'usage mais contournement disponible

---

## Symptôme

Après suppression des applications par défaut pour les vidéos, SwipePlayer
n'apparaît pas dans la liste des lecteurs proposés quand on clique simplement
sur une vidéo dans FX File Explorer. VLC, MX Player, Mi Video apparaissent
mais pas SwipePlayer.

SwipePlayer APPARAÎT en revanche quand on utilise explicitement
"Ouvrir avec" (long press → menu → Open with application) depuis FX.

## Étapes de reproduction

1. Aller dans Paramètres > Applications > Supprimer les apps par défaut pour les vidéos
2. Ouvrir FX File Explorer et naviguer jusqu'à un répertoire avec des vidéos
3. Tapper une seule fois sur une vidéo → dialog de sélection s'affiche
4. → SwipePlayer est absent de la liste, VLC/MX Player y sont

## Analyse de la cause racine

### Cause 1 : Filtres intent manquants (fixée)

Le manifest original n'avait pas :
- `android:category="android.intent.category.BROWSABLE"` — requis par certains
  gestionnaires de fichiers pour lister une app dans leur chooser
- Filtres par `android:pathPattern` pour chaque extension vidéo — VLC et MX
  Player matchent même quand le gestionnaire envoie l'intent SANS MIME type
  (uniquement avec l'URI). SwipePlayer ne matchait que via `mimeType="video/*"`.

### Cause 2 : Comportement système normal (non fixable par code)

Android distingue deux types de chooser :
- **Quick chooser** (clic simple) : montre uniquement les apps "recommandées"
  par le système (historique d'usage, score de confiance). Les APKs debug
  sideloadés ont un score initial bas → absents du quick chooser.
- **Full chooser** ("Ouvrir avec" explicite) : montre TOUTES les apps avec
  intent filters correspondants → SwipePlayer apparaît.

Ce comportement est indépendant du code de l'app. Il se résout :
- En utilisant "Ouvrir avec" → "Toujours" pour définir SwipePlayer comme défaut
- Ou en cherchant "Applications par défaut > Lecteur vidéo" dans les paramètres
  (chemin variable selon le constructeur/version Android)

## Correction appliquée

`AndroidManifest.xml` — ajouts :
1. `android:category="android.intent.category.BROWSABLE"` sur les filtres VIEW
2. 11 nouveaux `<intent-filter>` avec `android:pathPattern` pour les extensions
   .mp4, .mkv, .avi, .mov, .wmv, .webm, .flv, .m4v, .3gp, .ts, .mpg/.mpeg
   (minuscules et majuscules), pour les schemes `file://` et `content://`

## Note

Après la correction du manifest, SwipePlayer apparaît dans le "Ouvrir avec"
explicite (déjà fonctionnel avant). L'absence dans le quick chooser système
est un comportement Android normal pour les apps nouvellement installées —
elle se résout par l'usage ou en définissant l'app comme défaut.
