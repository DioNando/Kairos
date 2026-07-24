package com.kairos.app.ui.matrix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task

/** Issue d'un glisser-déposer terminé : soit un changement de quadrant, soit un réordonnancement. */
sealed interface DragResult {
    data class Move(val task: Task, val target: Quadrant) : DragResult
    data class Reorder(val task: Task, val targetIndex: Int) : DragResult
}

/**
 * État partagé du glisser-déposer de la matrice : quelle tâche est saisie, où se trouve le doigt
 * (coordonnées racine), les rectangles des 4 quadrants, l'ordre et les positions des lignes.
 *
 * Le geste **déplace** une tâche vers un autre quadrant, ou la **réordonne** dans le sien ; il double
 * le menu accessible « Déplacer vers… » (drag inaccessible pour TalkBack), il ne le remplace pas.
 */
class QuadrantDragState {
    /** Tâche en cours de glissement (null = aucun drag). */
    var dragged by mutableStateOf<Task?>(null)
        private set

    /** Position absolue du doigt dans le repère racine. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** Quadrant survolé (cible de dépôt), recalculé à chaque déplacement. */
    var hoverTarget by mutableStateOf<Quadrant?>(null)
        private set

    /** Bornes (repère racine) de chaque quadrant, publiées par les cellules. */
    val bounds: SnapshotStateMap<Quadrant, Rect> = mutableStateMapOf()

    /** Bornes (repère racine) de chaque ligne de tâche, pour calculer l'index de réordonnancement. */
    val itemBounds: SnapshotStateMap<Long, Rect> = mutableStateMapOf()

    /** Ordre des ids de tâches actives par quadrant, publié par les cellules. */
    val quadrantOrder: SnapshotStateMap<Quadrant, List<Long>> = mutableStateMapOf()

    /**
     * Position (repère racine) du conteneur qui **rend** l'overlay. L'overlay est dessiné dans un
     * Box décalé par le padding du Scaffold (barre du haut) : on retranche cet offset au [pointer]
     * (exprimé en repère racine) pour que la carte suive exactement le doigt. Sinon elle apparaît
     * décalée vers le bas de la hauteur de la barre.
     */
    var rootOffset by mutableStateOf(Offset.Zero)

    val isDragging: Boolean get() = dragged != null

    fun start(task: Task, at: Offset) {
        dragged = task
        pointer = at
        hoverTarget = task.quadrant
    }

    fun move(delta: Offset) {
        pointer += delta
        hoverTarget = bounds.entries.firstOrNull { it.value.contains(pointer) }?.key
    }

    /** Termine le geste : déplacement vers un autre quadrant, réordonnancement, ou rien (null). */
    fun end(): DragResult? {
        val task = dragged
        val target = hoverTarget
        dragged = null
        hoverTarget = null
        if (task == null || target == null) return null
        if (target != task.quadrant) return DragResult.Move(task, target)

        // Même quadrant : calcule l'index d'insertion d'après la hauteur du doigt.
        val order = quadrantOrder[target] ?: return null
        val current = order.indexOf(task.id)
        val others = order.filter { it != task.id }
        val index = others.count { id ->
            val rect = itemBounds[id]
            rect != null && (rect.top + rect.height / 2f) < pointer.y
        }
        if (index == current) return null
        return DragResult.Reorder(task, index)
    }

    fun cancel() {
        dragged = null
        hoverTarget = null
    }
}
