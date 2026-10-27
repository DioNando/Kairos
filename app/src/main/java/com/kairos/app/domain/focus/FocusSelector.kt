package com.kairos.app.domain.focus

import com.kairos.app.data.model.Task

/**
 * Sélectionne « LA tâche à faire maintenant » indépendamment du rangement en quadrants.
 *
 * Chaque tâche active reçoit un **score de priorité** qui mélange deux composantes, pondérées par
 * [dueWeight] (0–100, réglable) :
 * - **importance** : important (0,7) + urgent (0,3) ;
 * - **échéance** : 1 si dépassée, puis décroît avec la distance (0,5 à 24 h, ~0,1 à une semaine),
 *   0 sans échéance.
 *
 * À 0 seule l'importance compte, à 100 seule l'échéance. Égalités départagées par échéance la plus
 * proche, puis ordre dans le quadrant. Les tâches archivées (terminées ou retirées) sont exclues.
 */
object FocusSelector {

    /** Pondération par défaut : l'importance domine, l'échéance départage. */
    const val DEFAULT_DUE_WEIGHT = 30

    private const val HOUR = 60L * 60 * 1000

    /** Score de priorité d'une tâche dans [0, 1]. */
    fun score(task: Task, dueWeight: Int = DEFAULT_DUE_WEIGHT, now: Long = System.currentTimeMillis()): Float {
        val w = dueWeight.coerceIn(0, 100) / 100f
        val importance = (if (task.important) 0.7f else 0f) + (if (task.urgent) 0.3f else 0f)
        val due = task.dueAt?.let { dueAt ->
            val hours = (dueAt - now).toFloat() / HOUR
            if (hours <= 0f) 1f else 1f / (1f + hours / 24f)
        } ?: 0f
        return (1f - w) * importance + w * due
    }

    /** Comparateur « plus prioritaire d'abord » (réutilisé pour le tri « Priorité » des quadrants). */
    fun comparator(dueWeight: Int = DEFAULT_DUE_WEIGHT, now: Long = System.currentTimeMillis()): Comparator<Task> =
        compareByDescending<Task> { score(it, dueWeight, now) }
            .thenBy { it.dueAt ?: Long.MAX_VALUE }
            .thenBy { it.position }
            .thenByDescending { it.createdAt }

    fun rank(
        tasks: List<Task>,
        dueWeight: Int = DEFAULT_DUE_WEIGHT,
        now: Long = System.currentTimeMillis(),
    ): List<Task> = tasks.filter { it.isActive }.sortedWith(comparator(dueWeight, now))

    fun next(tasks: List<Task>, dueWeight: Int = DEFAULT_DUE_WEIGHT): Task? = rank(tasks, dueWeight).firstOrNull()
}
