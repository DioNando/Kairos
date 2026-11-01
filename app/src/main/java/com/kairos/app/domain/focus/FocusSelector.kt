package com.kairos.app.domain.focus

import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task

/**
 * Sélectionne « LA tâche à faire maintenant » indépendamment du rangement en quadrants.
 *
 * Chaque tâche active reçoit un **score de priorité** qui mélange deux composantes, pondérées par
 * [dueWeight] (0–100, réglable) :
 * - **importance** : important (0,7) + urgent (0,3) ;
 * - **échéance** : 1 si dépassée, puis décroît avec la distance (0,5 à 24 h, ~0,1 à une semaine),
 *   0 sans échéance. C'est la plus proche entre celle de la tâche et celles de ses sous-tâches non
 *   faites ([subtaskDues]).
 *
 * À 0 seule l'importance compte, à 100 seule l'échéance. Égalités départagées par échéance la plus
 * proche, importance, puis ordre dans le quadrant. Les tâches archivées (terminées ou retirées) sont
 * exclues.
 */
object FocusSelector {

    /** Pondération par défaut : une échéance très proche peut passer devant un quadrant supérieur. */
    const val DEFAULT_DUE_WEIGHT = 30

    private const val HOUR = 60L * 60 * 1000

    /**
     * Poids en deçà duquel l'échéance ne peut plus faire passer une tâche devant un quadrant supérieur
     * (écart d'importance minimal 0,3 : `w = (1 - w) × 0,3`). En dessous, le classement ne bouge pas.
     */
    private const val CROSSOVER_WEIGHT = 3f / 13f

    /**
     * Poids réel d'un cran du curseur. Les crans 1 à [DEFAULT_DUE_WEIGHT] sont resserrés au-dessus de
     * [CROSSOVER_WEIGHT] pour que chacun change le classement ; au-delà, le cran est le poids.
     */
    private fun effectiveWeight(dueWeight: Int): Float {
        val notch = dueWeight.coerceIn(0, 100)
        val weight = notch / 100f
        if (notch == 0 || notch >= DEFAULT_DUE_WEIGHT) return weight
        val default = DEFAULT_DUE_WEIGHT / 100f
        return CROSSOVER_WEIGHT + (default - CROSSOVER_WEIGHT) * notch / DEFAULT_DUE_WEIGHT
    }

    private fun importance(task: Task): Float =
        (if (task.important) 0.7f else 0f) + (if (task.urgent) 0.3f else 0f)

    /** Échéance la plus proche entre celle de la tâche et [subtaskDue]. */
    private fun effectiveDue(task: Task, subtaskDue: Long?): Long? =
        listOfNotNull(task.dueAt, subtaskDue).minOrNull()

    /** Échéance la plus proche des sous-tâches non faites, par id de tâche. */
    fun subtaskDues(subtasks: List<Subtask>): Map<Long, Long> =
        subtasks.filter { !it.done && it.dueAt != null }
            .groupBy { it.taskId }
            .mapValues { (_, pending) -> pending.minOf { it.dueAt!! } }

    /** Score de priorité d'une tâche dans [0, 1]. */
    fun score(
        task: Task,
        dueWeight: Int = DEFAULT_DUE_WEIGHT,
        now: Long = System.currentTimeMillis(),
        subtaskDue: Long? = null,
    ): Float {
        val w = effectiveWeight(dueWeight)
        val due = effectiveDue(task, subtaskDue)?.let { dueAt ->
            val hours = (dueAt - now).toFloat() / HOUR
            if (hours <= 0f) 1f else 1f / (1f + hours / 24f)
        } ?: 0f
        return (1f - w) * importance(task) + w * due
    }

    /** Comparateur « plus prioritaire d'abord ». */
    fun comparator(
        dueWeight: Int = DEFAULT_DUE_WEIGHT,
        now: Long = System.currentTimeMillis(),
        subtaskDues: Map<Long, Long> = emptyMap(),
    ): Comparator<Task> =
        compareByDescending<Task> { score(it, dueWeight, now, subtaskDues[it.id]) }
            .thenBy { effectiveDue(it, subtaskDues[it.id]) ?: Long.MAX_VALUE }
            // À 100, les tâches sans échéance sont à égalité : position n'a de sens que dans un quadrant.
            .thenByDescending { importance(it) }
            .thenBy { it.position }
            .thenByDescending { it.createdAt }

    fun rank(
        tasks: List<Task>,
        dueWeight: Int = DEFAULT_DUE_WEIGHT,
        now: Long = System.currentTimeMillis(),
        subtaskDues: Map<Long, Long> = emptyMap(),
    ): List<Task> = tasks.filter { it.isActive }.sortedWith(comparator(dueWeight, now, subtaskDues))
}
