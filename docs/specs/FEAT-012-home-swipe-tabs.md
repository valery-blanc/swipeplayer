# FEAT-012 — Swipe gauche/droite entre onglets dans l'ecran d'accueil

## Statut : IN PROGRESS

## Contexte

La navigation entre onglets (Collections, Videos, Parcourir) se fait
uniquement par tap sur la NavigationBar. Ajouter le swipe gauche/droite
comme mode de navigation supplementaire.

## Comportement attendu

- Swipe gauche -> onglet suivant (Collections -> Videos -> Parcourir)
- Swipe droite -> onglet precedent (Parcourir -> Videos -> Collections)
- NavigationBar reste synchronisee avec la page courante
- Tap sur un item NavigationBar anime le pager vers la page correspondante
- Exception : dans l'onglet Parcourir, si l'utilisateur est en sous-dossier,
  le swipe est desactive (le swipe horizontal est pris par la navigation
  de retour dans le browser). En pratique, on laisse le swipe actif partout
  car le FileBrowserScreen utilise BackHandler et non le swipe horizontal.

## Spec technique

### HorizontalPager
- `rememberPagerState(initialPage = 0, pageCount = { 3 })`
- Ordre des pages : 0=Collections, 1=Videos, 2=Parcourir
- `HorizontalPager` wrapping les 3 ecrans

### Synchronisation
- `LaunchedEffect(pagerState.currentPage)` -> `viewModel.onTabSelected(HomeTab.values()[page])`
- Quand NavigationBar est tappee -> `scope.launch { pagerState.animateScrollToPage(tabIndex) }`
- La NavigationBar utilise `pagerState.currentPage` (pas `uiState.activeTab`) pour l'indicateur
  de selection afin d'avoir un feedback visuel instantane pendant le swipe.

## Impact
- `HomeScreen.kt` : remplacer le `when(uiState.activeTab)` par `HorizontalPager`
- Ajout import `compose.foundation.pager`
