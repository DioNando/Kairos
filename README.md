# Kairós

**Kairós** est une application Android **autonome** de gestion des priorités, fondée sur la
**matrice d'Eisenhower** (Urgent × Important). Elle aide à trier ce qui compte vraiment de ce qui
n'est qu'urgent, puis à passer à l'action — une tâche à la fois.

> *Kairós* (καιρός) : en grec ancien, le « moment opportun », l'instant où il faut agir. C'est
> l'esprit de l'app : la bonne tâche, au bon moment.

---

## Concept

Chaque tâche se range dans l'un des **4 quadrants**, chacun associé à un **verbe d'action** :

| Quadrant | Axes | Verbe | Sens |
|---|---|---|---|
| **Faire** | urgent + important | *Do* | À traiter tout de suite |
| **Planifier** | important, pas urgent | *Schedule* | À programmer, le cœur du travail de fond |
| **Déléguer** | urgent, pas important | *Delegate* | À confier à quelqu'un d'autre |
| **Supprimer** | ni l'un ni l'autre | *Eliminate* | À abandonner sans culpabilité |

Le quadrant **n'est pas stocké** : il se **déduit** des deux axes `important` × `urgent` d'une tâche.
Les déplacer, c'est reclasser la tâche.

## Fonctionnalités

- **Matrice 2×2** (ou **vue en listes / swim lanes**, réglable) avec compteur par quadrant.
- **Glisser-déposer** d'une tâche entre quadrants (appui long), avec **réordonnancement fin dans un
  quadrant**, **doublé** d'un menu accessible « Déplacer vers… » (indispensable pour TalkBack).
- **Swipe** sur une ligne : **droite = terminer**, **gauche = supprimer**, avec **annulation**
  (snackbar « Annuler ») après complétion comme après suppression. La complétion est aussi dans le
  menu ⋮ de chaque tâche.
- **Ajout rapide** sans friction (titre + quadrant, clavier ouvert d'emblée) et **éditeur complet**
  (note, échéance date/heure, étiquette, **récurrence**, **sous-tâches / checklist**).
- **Échéances non intrusives** : une échéance proche fait **pulser** la tâche et **propose** de la
  passer en urgent — elle ne déplace jamais la tâche de force.
- **Tâches récurrentes** (quotidienne / hebdo / mensuelle) : régénèrent une occurrence à la complétion.
- **Mode Focus « Et maintenant ? »** : sort **LA** tâche prioritaire, plus un aperçu « À suivre ».
- **Étiquettes** transversales (catégories) avec couleur, indépendantes des quadrants.
- **Archive** : les tâches terminées sont rangées à part (restaurables, supprimables ou **vidées**
  d'un geste), avec **purge automatique** après N jours (réglable), pour garder une matrice propre.
- **Pomodoro** intégré : minuteur focus/pause ancré sur l'horloge, **persistant** (survit au
  redémarrage), **notification** de premier plan avec Pause/Reprendre/Réinitialiser, alerte de fin
  de cycle. Durées et cadence réglables ; style « cercle liquide » ou **anneau ondulé**. Une session
  peut être **liée à une tâche** pour lui **créditer le temps** de concentration.
- **Onboarding** au premier lancement (explication de la matrice d'Eisenhower).
- **Thème** clair/sombre/système, **Material You** (Android 12+), **4 palettes** de quadrants + une
  **palette personnalisée** (choix libre des 4 couleurs).
- **Sauvegarde locale** : export / import JSON de toutes les tâches, sous-tâches, étiquettes et réglages.
- **Material 3 Expressive** : formes qui **morphent** (pastilles de quadrant, cards de classement),
  **ButtonGroup connecté** (phases Pomodoro), indicateurs **ondulés**, mouvement à ressorts.
- **Bilingue** français / anglais, accessibilité (contentDescription) et animations de finition.

## Architecture

Application **100 % locale**, sans backend, calquée (en allégé) sur l'archi de WorkSync :

- **UI** : Jetpack Compose + Material 3 **Expressive** (MotionScheme).
- **Navigation** : Navigation-Compose (matrice, focus, étiquettes, réglages, pomodoro, archive).
- **État** : `KairosViewModel` (tâches + étiquettes) et `PomodoroEngine` à portée application.
- **Données** : **Room** (`Task`, `Label`, `Subtask` — schéma v2 + migration) + **DataStore**
  (réglages et session Pomodoro).
- **DI** : conteneur manuel dans `KairosApp` (pas de framework), `ViewModelFactory`.
- **Pomodoro** : moteur ancré sur un instant de fin absolu + `PomodoroSessionStore` (persistance) +
  `PomodoroService` (premier plan) + `PomodoroNotifications`.

### Structure

```
com.kairos.app
├── data/            model (Task, Label, Subtask, Quadrant, AppSettings) · local (Room) · repository · PomodoroSessionStore
├── domain/          backup (export/import JSON) · focus (FocusSelector) · recurrence (Recurrence)
├── pomodoro/        PomodoroService · PomodoroNotifications
└── ui/              matrix · editor · focus · labels · archive · settings · pomodoro · onboarding · theme · components · navigation
```

### Build

```bash
./gradlew assembleDebug
```

- `minSdk 26`, `compileSdk 37`, Kotlin 2.2, AGP 9, Compose BOM 2026.05 + Material 3 1.5 (Expressive).
- Sur Windows, si la sortie Gradle streamée fait planter le terminal, rediriger vers un fichier :
  `Start-Process cmd '/c <chemin absolu>\gradlew.bat assembleDebug --console=plain' -RedirectStandardOutput out.txt`.

---

## Évolutions possibles (feuille de route)

> Déjà livré : matrice + swim lanes, drag & réordonnancement intra-quadrant, swipe + undo,
> ajout rapide + éditeur complet, récurrence, sous-tâches, échéances non intrusives, mode Focus,
> étiquettes, archive (vidage + purge auto), Pomodoro (persistant, lié à une tâche), onboarding,
> palettes (dont personnalisée), Material You, sauvegarde JSON, i18n FR/EN, finition M3 Expressive.

### Rappels & temps
- [ ] **Notifications d'échéance** (WorkManager) : rappel avant l'heure due.
- [ ] **Statistiques** : focus par jour, tâches terminées, répartition par quadrant.

### Recherche & organisation
- [ ] **Recherche & filtre** par étiquette, par échéance, par quadrant.
- [ ] **Tri configurable** dans un quadrant (échéance, création, alphabétique).
- [ ] **Réglage de la priorité du mode Focus** (pondérer échéance vs importance).

### Intégrations & plateforme
- [ ] **Widgets** (Glance) : la tâche du moment, compteurs par quadrant, bouton Pomodoro.
- [ ] **Tuiles Quick Settings** et **App Shortcuts** (ajout rapide, démarrer un Pomodoro).
- [ ] **Raccourci d'ajout** depuis le partage Android (« Partager vers Kairós »).
- [ ] **Sauvegarde cloud / synchro multi-appareils** (optionnelle, chiffrée).
- [ ] **Export CSV / Markdown** en plus du JSON.
- [ ] **Mode « une main »** renforcé (actions rapprochées du pouce).

---

*Projet personnel — application dédiée, distincte de WorkSync dont elle réutilise la charte
graphique et certains composants (thème, champs tonals, Pomodoro).*
