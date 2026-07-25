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
- **Recherche & filtre** : une barre de recherche (titre / note) et un **filtre par étiquette**
  transversal, dépliés depuis l'icône loupe de la matrice, pour retrouver une tâche parmi beaucoup.
- **Glisser-déposer** d'une tâche entre quadrants (appui long), avec **réordonnancement fin dans un
  quadrant**, **doublé** d'un menu accessible « Déplacer vers… » (indispensable pour TalkBack).
- **Swipe** sur une ligne : **droite = terminer**, **gauche = retirer** — dans les deux cas la tâche
  part à l'**archive** (rien n'est perdu, on la restaure depuis là) ; plus de suppression dure ni de
  snackbar « Annuler » depuis la matrice. Terminer et retirer sont aussi dans le menu ⋮ de chaque tâche.
- **Ajout rapide** sans friction (titre + quadrant, clavier ouvert d'emblée) et **éditeur complet**
  (note, échéance date/heure, étiquette, **récurrence**, **sous-tâches / checklist**).
- **Échéances non intrusives** : une échéance proche fait **pulser** la tâche et **propose** de la
  passer en urgent — elle ne déplace jamais la tâche de force.
- **Tâches récurrentes** (quotidienne / hebdo / mensuelle) : à la complétion, la tâche **avance sur
  place** (échéance suivante, sous-tâches décochées) et reste sur le board — une seule occurrence
  vivante, pas d'accumulation dans l'archive.
- **Rappels d'échéance** (WorkManager) : notification à l'approche de l'échéance des tâches qui en
  ont une, avec **délai d'anticipation réglable** (à l'heure / 5 / 15 / 30 / 60 min avant) ; un appui
  sur la notification **ouvre la tâche**. Resynchronisés automatiquement à chaque changement.
- **Widget d'écran d'accueil** (Glance) : matrice 2×2 avec le **nombre de tâches par quadrant**
  (icônes, indépendant de la langue), rafraîchi automatiquement ; un appui ouvre l'app.
- **Mode Focus « Et maintenant ? »** : sort **LA** tâche prioritaire, plus un aperçu « À suivre ».
- **Étiquettes** transversales (catégories) avec couleur, indépendantes des quadrants.
- **Archive** : toutes les tâches sorties du board — **terminées** (✓) ou **retirées** (🗄),
  distinguées par un badge — y sont rangées (restaurables, ou **supprimables définitivement** :
  c'est le **seul** endroit où une suppression est irréversible), avec **vidage** d'un geste et
  **purge automatique** après N jours (réglable), pour garder une matrice propre.
- **Pomodoro** intégré : minuteur focus/pause ancré sur l'horloge, **persistant** (survit au
  redémarrage), **notification** de premier plan avec Pause/Reprendre/Réinitialiser, alerte de fin
  de cycle. Durées et cadence réglables ; style « cercle liquide » ou **anneau ondulé**. Une session
  peut être **liée à une tâche** pour lui **créditer le temps** de concentration.
- **Onboarding** au premier lancement (explication de la matrice d'Eisenhower).
- **Thème** clair/sombre/système, **Material You** (Android 12+), **4 palettes** de quadrants + une
  **palette personnalisée** (choix libre des 4 couleurs).
- **Assistant IA** (optionnel) : trois actions dans l'éditeur — **reformuler** toute la tâche d'un
  coup (titre, note **et** sous-tâches, avec « Rétablir l'original »), **suggérer le classement**
  (axes Important / Urgent → quadrant, ajustable), et **découper en sous-tâches** (checklist d'étapes
  concrètes). Via Gemini ou OpenRouter (repli automatique de l'un sur l'autre). Clé de repli possible
  via `.env` ; sinon clé saisie dans les réglages, stockée **chiffrée** sur l'appareil (jamais dans
  les sauvegardes). Fonctions masquées tant qu'aucune clé n'existe.
- **Enregistrement automatique** de l'éditeur : les modifications sont sauvegardées à la sortie
  (pas de bouton « Enregistrer » à oublier) ; une tâche créée sans titre n'est pas conservée.
- **Sauvegarde locale** : export / import JSON de toutes les tâches, sous-tâches, étiquettes et réglages.
- **Material 3 Expressive** : formes qui **morphent** (pastilles de quadrant, cards de classement),
  **ButtonGroup connecté** (phases Pomodoro), indicateurs **ondulés**, mouvement à ressorts.
- **Bilingue** français / anglais — **langue choisie dans l'app** (Système / Français / English,
  appliquée à chaud) ou suivant le téléphone —, accessibilité (contentDescription) et animations de finition.

## Architecture

Application **100 % locale**, sans backend, calquée (en allégé) sur l'archi de WorkSync :

- **UI** : Jetpack Compose + Material 3 **Expressive** (MotionScheme).
- **Navigation** : Navigation-Compose (matrice, focus, étiquettes, réglages, pomodoro, archive).
- **État** : `KairosViewModel` (tâches + étiquettes) et `PomodoroEngine` à portée application.
- **Données** : **Room** (`Task`, `Label`, `Subtask` — schéma v3 + migrations) + **DataStore**
  (réglages et session Pomodoro).
- **DI** : conteneur manuel dans `KairosApp` (pas de framework), `ViewModelFactory`.
- **Pomodoro** : moteur ancré sur un instant de fin absolu + `PomodoroSessionStore` (persistance) +
  `PomodoroService` (premier plan) + `PomodoroNotifications`.

### Structure

```
com.kairos.app
├── data/            model (Task, Label, Subtask, Quadrant, AppSettings, LlmProvider) · local (Room) · remote (LLM : Gemini/OpenRouter) · reminder (Scheduler/Worker/Notifications, WorkManager) · repository (dont SecureCredentialStore) · PomodoroSessionStore
├── domain/          backup (export/import JSON) · focus (FocusSelector) · recurrence (Recurrence) · ai (NoteEnhancer : reformuler/classer/découper)
├── pomodoro/        PomodoroService · PomodoroNotifications
├── widget/          KairosWidget · KairosWidgetReceiver (Glance)
└── ui/              matrix (dont recherche/filtre) · editor · focus · labels · archive · settings · pomodoro · onboarding · guide · theme · components · navigation
```

### Build

```bash
./gradlew assembleDebug
```

- `minSdk 26`, `compileSdk 37`, Kotlin 2.2, AGP 9, Compose BOM 2026.05 + Material 3 1.5 (Expressive).
- **Clés IA (facultatives)** : pour un repli automatique sans saisie dans l'app, renseigner
  `GEMINI_API_KEY` / `OPENROUTER_API_KEY` dans un fichier `.env` à la racine (gitignoré) ou
  `gemini.api.key` / `openrouter.api.key` dans `local.properties`. Sans clé, la reformulation reste
  simplement masquée. _(La clé de build est incluse en clair dans l'APK : à réserver aux builds privés.)_
- Sur Windows, si la sortie Gradle streamée fait planter le terminal, rediriger vers un fichier :
  `Start-Process cmd '/c <chemin absolu>\gradlew.bat assembleDebug --console=plain' -RedirectStandardOutput out.txt`.

---

## Évolutions possibles (feuille de route)

> Déjà livré : matrice + swim lanes, drag & réordonnancement intra-quadrant, **swipe → archive**
> (terminer / retirer, restauration depuis l'archive), **recherche & filtre par étiquette**,
> ajout rapide + éditeur complet, récurrence (avance sur place), sous-tâches,
> échéances non intrusives, **rappels d'échéance (WorkManager)**, **widget d'accueil (Glance)**,
> mode Focus, étiquettes, archive (vidage + purge auto), Pomodoro (persistant, lié à une tâche),
> onboarding, guide in-app, palettes (dont personnalisée), Material You, sauvegarde JSON, i18n FR/EN,
> **assistant IA (reformuler / classer / découper)**, finition M3 Expressive.

*Liste **non exhaustive** de pistes envisageables — ni engagement ni ordre de priorité, juste un
espace à idées pour faire mûrir l'app.*

### Rappels & temps
- [x] **Notifications d'échéance** (WorkManager) : rappel avant l'heure due (délai d'anticipation
  réglable). _À venir : report « snooze » depuis la notification._
- [ ] **Rappels récurrents** et **plages horaires** (ne pas notifier la nuit / le week-end).
- [ ] **Estimation de durée** par tâche + **budget de temps** quotidien.
- [ ] **Time-blocking** : glisser une tâche dans un créneau d'agenda.
- [ ] **Intégration Google Agenda / iCal** (lecture des créneaux occupés).

### Statistiques & motivation
- [ ] **Tableau de bord** : focus par jour, tâches terminées, répartition par quadrant.
- [ ] **Tendances** hebdo/mensuelles (part du temps « Faire » vs « Planifier »).
- [ ] **Séries (streaks)** et petits **objectifs** (ex. « vider Éliminer chaque semaine »).
- [ ] **Bilan de fin de journée / semaine** (ce qui a avancé, ce qui glisse).

### Recherche & organisation
- [x] **Recherche & filtre** par mot-clé (titre / note) et par étiquette. _À venir : filtre par
  échéance / quadrant._
- [ ] **Tri configurable** dans un quadrant (échéance, création, priorité, alphabétique).
- [ ] **Réglage de la priorité du mode Focus** (pondérer échéance vs importance).
- [ ] **Sélection multiple** et **actions en lot** (déplacer / étiqueter / supprimer plusieurs tâches).
- [ ] **Étiquettes avancées** : groupes, filtres enregistrés, étiquette « épinglée ».
- [ ] **Sous-tâches enrichies** : progression, échéance propre, promotion en tâche.
- [ ] **Notes enrichies** : cases à cocher, liens, pièces jointes légères.

### Intégrations & plateforme
- [x] **Widget** (Glance) : compteurs par quadrant (icônes). _À venir : tâche du moment, bouton Pomodoro._
- [ ] **Tuiles Quick Settings** et **App Shortcuts** (ajout rapide, démarrer un Pomodoro).
- [ ] **Raccourci d'ajout** depuis le partage Android (« Partager vers Kairós »).
- [ ] **Saisie vocale** et **Assistant / App Actions** (« ajoute … à Faire »).
- [ ] **Wear OS** : consulter la tâche du moment, cocher, lancer un Pomodoro.
- [ ] **Sauvegarde cloud / synchro multi-appareils** (optionnelle, chiffrée de bout en bout).
- [ ] **Export CSV / Markdown / .ics** en plus du JSON ; **import** depuis d'autres apps de tâches.

### Personnalisation & confort
- [ ] **Thèmes & polices** supplémentaires, **densité** d'affichage réglable.
- [ ] **Icônes / emojis** par étiquette ou par tâche.
- [ ] **Mode « une main »** renforcé (actions rapprochées du pouce).
- [ ] **Gestes personnalisables** (que fait le swipe gauche/droite).
- [ ] **Verrouillage** de l'app (biométrie) pour les tâches sensibles.

### Assistance (IA, optionnelle)
- [x] **Reformulation / mise au propre** du titre, de la note et des sous-tâches en un geste
  (Gemini / OpenRouter, clés chiffrées, repli `.env`).
- [x] **Classement suggéré** : proposer les axes Important / Urgent (donc le quadrant) depuis le titre / la note.
- [x] **Découpage en sous-tâches** suggéré pour une grande tâche.
- [ ] **Résumé** d'une note longue.

### Collaboration (plus tard)
- [ ] **Partage d'une tâche ou d'une liste** (la « délégation » réellement déléguée).
- [ ] **Espaces partagés** (couple / famille / équipe) avec attribution.

> #### 💡 Piste étudiée : espace partagé couple/famille (Supabase)
>
> Direction retenue en discussion (non engagée) : un **espace commun** où chaque membre voit et
> édite la même matrice. **Offline-first** (Room reste le cache, l'app marche sans réseau) et
> **opt-in** (l'app reste 100 % locale par défaut — le cloud ne trahit pas l'usage solo).
>
> **Backend** : Supabase (Postgres + Auth + Row Level Security + Realtime). Auth par **magic link**
> (pas de mot de passe). La clé `anon` est publique (RLS protège) ; ne jamais embarquer `service_role`.
>
> **Modèle de données à introduire** :
> - `user` (id Supabase Auth), `workspace`, `workspace_member` (rôle) ;
> - `Task`/`Label`/`Subtask` rattachées à un `workspace_id` ;
> - **clés en UUID** générées côté client (remplace les `Long autoGenerate` — sinon collisions entre
>   appareils ; migration Room + refonte des FK = le point technique le plus lourd) ;
> - colonnes de synchro : `updatedAt`, `deleted` (**soft-delete**, sinon une tâche supprimée
>   « ressuscite » au prochain pull), `lastSyncedAt` ;
> - **RLS** Postgres : un membre ne lit/écrit que les données des workspaces dont il fait partie.
>
> **Synchro** : moteur push (local→cloud) / pull (cloud→local) offline-first, file d'attente des
> mutations hors-ligne, résolution de conflits **last-write-wins** via `updatedAt` (le merge par champ
> serait overkill ici). SDK `supabase-kt` (communauté) ou REST via l'okhttp déjà présent + JWT.
>
> **Découpage** (3 temps, pas un sprint) : **1** socle (Auth + schéma + RLS + migration UUID +
> login/logout) → **2** synchro offline-first (push/pull, soft-delete, LWW, indicateur de synchro) →
> **3** partage (workspaces, invitation par lien/email, rôles, UI) ; **Realtime** en finition.
>
> **À assumer** : les tâches quittent l'appareil → à annoncer clairement (vie privée), et un écran de
> connexion/compte apparaît (le cloud reste optionnel).

---

*Projet personnel — application dédiée, distincte de WorkSync dont elle réutilise la charte
graphique et certains composants (thème, champs tonals, Pomodoro).*
