package com.kairos.app.domain.focus

import com.kairos.app.data.model.Task

/**
 * Sélectionne « LA tâche à faire maintenant » indépendamment du rangement en quadrants.
 *
 * Priorité : les **importantes** d'abord, puis l'**échéance la plus proche**, puis l'**urgence**
 * manuelle, puis l'ordre dans le quadrant. Les tâches archivées (terminées ou retirées) sont exclues.
 */
object FocusSelector {

    fun rank(tasks: List<Task>): List<Task> =
        tasks.asSequence()
            .filter { it.isActive }
            .sortedWith(
                compareByDescending<Task> { it.important }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.urgent }
                    .thenBy { it.position }
                    .thenByDescending { it.createdAt },
            )
            .toList()

    fun next(tasks: List<Task>): Task? = rank(tasks).firstOrNull()
}
