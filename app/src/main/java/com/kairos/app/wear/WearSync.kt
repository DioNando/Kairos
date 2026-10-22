package com.kairos.app.wear

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task
import com.kairos.app.domain.focus.FocusSelector
import com.kairos.app.ui.theme.AccentPalette

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
    fun pushFocus(context: Context, tasks: List<Task>, labels: Map<Long, Label>) {
        val ranked = FocusSelector.rank(tasks).take(FOCUS_SIZE)
        val request = PutDataMapRequest.create(FOCUS_PATH).apply {
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
}
