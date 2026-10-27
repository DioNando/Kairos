package com.kairos.app.wear

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task
import com.kairos.app.domain.focus.FocusSelector
import com.kairos.app.domain.sort.TaskSort
import com.kairos.app.ui.pomodoro.PomodoroState
import com.kairos.app.ui.theme.AccentPalette
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pousse l'état utile vers la montre (Wearable Data Layer). La montre n'a pas accès à la base Room du
 * téléphone : elle reçoit des `DataItem` compacts et les persiste localement. Trois flux :
 * - `/kairos/counts` : les 4 compteurs de quadrants (alimente la **tuile**).
 * - `/kairos/focus` : la file « À faire en priorité » (tâche prio + à suivre, avec l'étiquette de
 *   chaque tâche : nom + couleur), pour l'**app montre**.
 * - `/kairos/palette` : les 4 couleurs de quadrants (ARGB), pour accorder montre et app.
 *
 * Aucun horodatage volontairement → un `DataItem` n'est resynchronisé que si son contenu change
 * réellement (pas de synchro inutile).
 */
object WearSync {
    private const val COUNTS_PATH = "/kairos/counts"
    const val KEY_DO = "do"
    const val KEY_SCHEDULE = "schedule"
    const val KEY_DELEGATE = "delegate"
    const val KEY_ELIMINATE = "eliminate"

    private const val FOCUS_PATH = "/kairos/focus"
    const val KEY_TITLES = "titles"
    const val KEY_QUADRANTS = "quadrants"
    const val KEY_LABEL_NAMES = "labelNames"
    const val KEY_LABEL_COLORS = "labelColors"

    private const val PALETTE_PATH = "/kairos/palette"

    /** Ids des tâches (tableau parallèle aux titres) : la montre renvoie l'id pour « Terminer ». */
    const val KEY_IDS = "ids"

    private const val QUADRANTS_PATH = "/kairos/quadrants"
    private const val PLANNING_PATH = "/kairos/planning"
    const val KEY_STARTS = "starts"
    const val KEY_ENDS = "ends"

    private const val POMODORO_PATH = "/kairos/pomodoro"
    const val KEY_ACTIVE = "active"
    const val KEY_RUNNING = "running"
    const val KEY_PHASE = "phase"
    const val KEY_END_AT = "endAt"
    const val KEY_REMAINING = "remaining"
    const val KEY_TOTAL = "total"

    /** Tâches poussées par quadrant pour la navigation sur la montre (au-delà : « +N »). */
    private const val QUADRANT_SIZE = 30

    /** Fenêtre de planning poussée (jours à partir d'aujourd'hui) : la montre filtre le jour courant. */
    private const val PLANNING_DAYS = 7L

    /** Nombre de tâches de la file de focus poussées (1 prioritaire + 3 « à suivre »). */
    private const val FOCUS_SIZE = 4

    /** Recompte les tâches actives par quadrant et publie le `DataItem` (envoi urgent). */
    fun pushCounts(context: Context, tasks: List<Task>) {
        val active = tasks.filter { it.isActive }
        val request = PutDataMapRequest.create(COUNTS_PATH).apply {
            dataMap.putInt(KEY_DO, active.count { it.quadrant == Quadrant.DO })
            dataMap.putInt(KEY_SCHEDULE, active.count { it.quadrant == Quadrant.SCHEDULE })
            dataMap.putInt(KEY_DELEGATE, active.count { it.quadrant == Quadrant.DELEGATE })
            dataMap.putInt(KEY_ELIMINATE, active.count { it.quadrant == Quadrant.ELIMINATE })
        }.asPutDataRequest().setUrgent()
        // Fire-and-forget : la tâche Play Services s'exécute en asynchrone ; l'échec (pas de montre
        // appairée / Play Services indisponible) est sans conséquence.
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
    }

    /**
     * Publie la file « À faire en priorité » : le même classement que l'écran Focus du téléphone
     * ([FocusSelector]), réduit aux [FOCUS_SIZE] premières tâches. Titre + quadrant (ordinal) +
     * étiquette (nom et couleur hex) en tableaux parallèles — la montre affiche la prioritaire + un
     * aperçu « À suivre », avec la pastille d'étiquette quand la tâche en a une. [labels] résout
     * `Task.labelId` ; chaîne vide quand la tâche n'a pas d'étiquette.
     */
    fun pushFocus(context: Context, tasks: List<Task>, labels: Map<Long, Label>, dueWeight: Int) {
        val ranked = FocusSelector.rank(tasks, dueWeight).take(FOCUS_SIZE)
        val request = PutDataMapRequest.create(FOCUS_PATH).apply {
            dataMap.putLongArray(KEY_IDS, ranked.map { it.id }.toLongArray())
            dataMap.putStringArray(KEY_TITLES, ranked.map { it.title }.toTypedArray())
            // DataMap n'a pas de int[] : les ordinaux de quadrant passent en ArrayList<Integer>.
            dataMap.putIntegerArrayList(KEY_QUADRANTS, ArrayList(ranked.map { it.quadrant.ordinal }))
            dataMap.putStringArray(
                KEY_LABEL_NAMES,
                ranked.map { task -> task.labelId?.let { labels[it]?.name } ?: "" }.toTypedArray(),
            )
            dataMap.putStringArray(
                KEY_LABEL_COLORS,
                ranked.map { task -> task.labelId?.let { labels[it]?.colorHex } ?: "" }.toTypedArray(),
            )
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
    }

    /**
     * Publie la palette de quadrants effective (presets ou personnalisée, résolue par
     * `AppSettings.toAccentPalette()`) en 4 entiers ARGB → la montre et sa tuile adoptent la même
     * couleur que l'app lorsqu'on change de palette dans les réglages.
     */
    fun pushPalette(context: Context, palette: AccentPalette) {
        val request = PutDataMapRequest.create(PALETTE_PATH).apply {
            dataMap.putInt(KEY_DO, palette.doColor.toArgb())
            dataMap.putInt(KEY_SCHEDULE, palette.scheduleColor.toArgb())
            dataMap.putInt(KEY_DELEGATE, palette.delegateColor.toArgb())
            dataMap.putInt(KEY_ELIMINATE, palette.eliminateColor.toArgb())
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
    }

    /**
     * Publie les tâches actives des 4 quadrants (ordre = tri des quadrants choisi dans l'app), pour la
     * navigation par quadrant sur la montre. Tableaux parallèles : id, titre, quadrant, couleur
     * d'étiquette ; plafonnés à [QUADRANT_SIZE] par quadrant.
     */
    fun pushQuadrants(context: Context, tasks: List<Task>, labels: Map<Long, Label>, sort: Int, dueWeight: Int) {
        val now = System.currentTimeMillis()
        val active = tasks.filter { it.isActive }
        val items = Quadrant.entries.flatMap { q ->
            TaskSort.apply(active.filter { it.quadrant == q }, sort, dueWeight, now).take(QUADRANT_SIZE)
        }
        val request = PutDataMapRequest.create(QUADRANTS_PATH).apply {
            dataMap.putLongArray(KEY_IDS, items.map { it.id }.toLongArray())
            dataMap.putStringArray(KEY_TITLES, items.map { it.title }.toTypedArray())
            dataMap.putIntegerArrayList(KEY_QUADRANTS, ArrayList(items.map { it.quadrant.ordinal }))
            dataMap.putStringArray(
                KEY_LABEL_COLORS,
                items.map { task -> task.labelId?.let { labels[it]?.colorHex } ?: "" }.toTypedArray(),
            )
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
    }

    /**
     * Publie les créneaux planifiés (time-blocking) des [PLANNING_DAYS] prochains jours, triés : la
     * montre en tire « Aujourd'hui » et la tuile « Prochain créneau » (filtrage local, l'heure avance).
     */
    fun pushPlanning(context: Context, tasks: List<Task>) {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.now(zone).plusDays(PLANNING_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val blocks = tasks
            .filter { it.isActive && it.blockStart != null && it.blockStart in from until to }
            .sortedBy { it.blockStart }
        val request = PutDataMapRequest.create(PLANNING_PATH).apply {
            dataMap.putLongArray(KEY_IDS, blocks.map { it.id }.toLongArray())
            dataMap.putStringArray(KEY_TITLES, blocks.map { it.title }.toTypedArray())
            dataMap.putIntegerArrayList(KEY_QUADRANTS, ArrayList(blocks.map { it.quadrant.ordinal }))
            dataMap.putLongArray(KEY_STARTS, blocks.map { it.blockStart!! }.toLongArray())
            dataMap.putLongArray(KEY_ENDS, blocks.map { it.blockEnd!! }.toLongArray())
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
    }

    /**
     * Publie l'état du Pomodoro. En marche, on envoie l'**heure de fin** ([KEY_END_AT]) : la montre
     * décompte seule, sans un envoi par seconde. En pause, le temps restant figé.
     */
    fun pushPomodoro(context: Context, state: PomodoroState) {
        val request = PutDataMapRequest.create(POMODORO_PATH).apply {
            dataMap.putBoolean(KEY_ACTIVE, state.active)
            dataMap.putBoolean(KEY_RUNNING, state.isRunning)
            dataMap.putInt(KEY_PHASE, state.phase.ordinal)
            dataMap.putLong(KEY_END_AT, System.currentTimeMillis() + state.remainingSeconds * 1000L)
            dataMap.putInt(KEY_REMAINING, state.remainingSeconds)
            dataMap.putInt(KEY_TOTAL, state.totalSeconds)
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
    }
}
