package com.kairos.app.ui.matrix

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task

/**
 * Issue d'un glisser-déposer terminé : soit un changement de quadrant, soit un réordonnancement.
 * [beforeId] = tâche **devant laquelle** insérer (null = en fin de quadrant). On passe une ancre
 * plutôt qu'un index : un index calculé sur la liste **affichée** (filtrée par la recherche ou
 * l'étiquette) ne correspond pas à l'index dans la liste complète du quadrant.
 */
sealed interface DragResult {
    data class Move(val task: Task, val target: Quadrant, val beforeId: Long?) : DragResult
    data class Reorder(val task: Task, val beforeId: Long?) : DragResult
}

/** Point de dépôt valide : insérer devant [beforeId] (null = en fin du quadrant). */
data class DropSlot(val beforeId: Long?)

/**
 * Zone défilable candidate à l'auto-défilement : [bounds] = viewport (repère racine), [state] = de
 * quoi la faire défiler. [quadrant] non null (liste d'un quadrant en grille) : la zone ne défile que
 * si ce quadrant est survolé — sinon le bas d'une cellule défilerait quand on survole l'en-tête de
 * la cellule du dessous.
 */
data class AutoScrollArea(val bounds: Rect, val state: ScrollableState, val quadrant: Quadrant? = null)

/**
 * Aperçu du dépôt, qui pilote le **décalage animé** des lignes : la tâche [taskId] quitte [source]
 * (son trou s'y referme) et s'insère dans [target] devant [beforeId] (les lignes s'y écartent).
 * [target] null = elle resterait à sa place (aucun décalage, seul son emplacement reste vide).
 * [rowHeight] = hauteur de la ligne saisie (px), sans l'espacement entre lignes.
 */
data class DropPreview(
    val taskId: Long,
    val source: Quadrant,
    val target: Quadrant?,
    val beforeId: Long?,
    val rowHeight: Float,
)

/**
 * État partagé du glisser-déposer de la matrice : quelle tâche est saisie, où se trouve le doigt
 * (coordonnées racine), les rectangles des 4 quadrants, l'ordre et les positions des lignes.
 *
 * Le geste **déplace** une tâche vers un autre quadrant (à l'endroit du dépôt), ou la **réordonne**
 * dans le sien ; il double le menu accessible « Déplacer vers… » (drag inaccessible pour TalkBack),
 * il ne le remplace pas.
 *
 * Toutes les méthodes du geste prennent l'id de la tâche : seule la ligne qui a **démarré** le drag
 * le pilote (un second doigt en appui long sur une autre ligne est ignoré au lieu de détourner le
 * drag en cours).
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

    /** Point d'insertion survolé (null = lâcher ici ne change rien). */
    var dropSlot by mutableStateOf<DropSlot?>(null)
        private set

    /** Aperçu du dépôt (drag en cours, ou dépôt pas encore reflété par la base), ou null. */
    var preview by mutableStateOf<DropPreview?>(null)
        private set

    /**
     * Après un dépôt : ordre affiché des quadrants touchés **au moment du dépôt**. Tant qu'une cellule
     * affiche encore cet ordre, la base n'a pas émis le nouvel ordre → on garde l'aperçu (lignes
     * décalées, trou ouvert) pour qu'il n'y ait ni retour en arrière ni saut entre le lâcher et la
     * mise à jour. Dès que l'ordre change, la mise en page réelle prend le relais **sans animation**.
     */
    private var settleOrders by mutableStateOf<Map<Quadrant, List<Long>>?>(null)

    /** Dépôt en attente de la base : les cellules coupent l'animation de placement (voir [settleOrders]). */
    val isSettling: Boolean get() = settleOrders != null

    /** Hauteur (px) de la ligne saisie, pour l'écart à ouvrir à l'endroit du dépôt. */
    private var rowHeight = 0f

    /** Bornes (repère racine) de chaque quadrant, publiées par les cellules. */
    val bounds: SnapshotStateMap<Quadrant, Rect> = mutableStateMapOf()

    /**
     * Bornes (repère racine, **clippées**) de chaque ligne **composée**. Une ligne retire son entrée
     * en quittant la composition (défilement d'une LazyColumn, filtre) : sinon son rectangle périmé
     * resterait à l'écran et fausserait le point d'insertion.
     */
    val itemBounds: SnapshotStateMap<Long, Rect> = mutableStateMapOf()

    /** Zones défilables (listes des quadrants en grille, colonne des lanes), pour l'auto-défilement. */
    val scrollAreas: SnapshotStateMap<Any, AutoScrollArea> = mutableStateMapOf()

    /** Ordre des ids de tâches **affichées** par quadrant, publié par les cellules. */
    val quadrantOrder: SnapshotStateMap<Quadrant, List<Long>> = mutableStateMapOf()

    /**
     * Position (repère racine) du conteneur qui **rend** l'overlay. L'overlay est dessiné dans un
     * Box décalé par le padding du Scaffold (barre du haut) : on retranche cet offset au [pointer]
     * (exprimé en repère racine) pour que la carte suive exactement le doigt. Sinon elle apparaît
     * décalée vers le bas de la hauteur de la barre.
     */
    var rootOffset by mutableStateOf(Offset.Zero)

    val isDragging: Boolean get() = dragged != null

    /**
     * false quand les quadrants sont **triés** (échéance, priorité…) : l'ordre n'est plus manuel, on
     * ne réordonne donc pas dans un quadrant et un changement de quadrant dépose en fin (le tri
     * replace ensuite la tâche).
     */
    var manualOrder by mutableStateOf(true)

    /**
     * Démarre le geste ; false si un autre drag est déjà en cours (second doigt). [rowHeight] =
     * hauteur de la ligne saisie (px).
     */
    fun start(task: Task, at: Offset, rowHeight: Float): Boolean {
        if (dragged != null) return false
        settleOrders = null
        dragged = task
        pointer = at
        this.rowHeight = rowHeight
        update(task)
        return true
    }

    /**
     * Place le doigt à [at] (repère racine, **absolu**) ; renvoie true si le quadrant survolé a
     * changé (retour haptique). Absolu plutôt que cumul de deltas : pendant l'auto-défilement la
     * ligne saisie bouge sous le doigt, et des deltas exprimés dans son repère local seraient faussés.
     */
    fun moveTo(taskId: Long, at: Offset): Boolean {
        val task = dragged?.takeIf { it.id == taskId } ?: return false
        val previous = hoverTarget
        pointer = at
        update(task)
        return hoverTarget != previous
    }

    /**
     * Boucle d'auto-défilement, à lancer pendant un drag (annulée à sa fin) : à chaque frame, fait
     * défiler la zone dont le doigt approche un bord, puis recalcule cible et point d'insertion (les
     * lignes ont bougé sous un doigt immobile). [edge] = profondeur de la zone de bord (px),
     * [maxSpeed] = vitesse au ras du bord (px/s).
     */
    suspend fun autoScroll(edge: Float, maxSpeed: Float) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val (area, velocity) = scrollTarget(edge, maxSpeed) ?: continue
            area.state.scrollBy(velocity * seconds)
            dragged?.let { update(it) }
        }
    }

    /**
     * Zone à faire défiler et vitesse signée (px/s), ou null. Vitesse quadratique selon la profondeur
     * dans la zone de bord (douce à l'entrée, maximale au ras du bord ou au-delà). Si plusieurs zones
     * conviennent, la plus petite (la plus imbriquée) l'emporte.
     */
    internal fun scrollTarget(edge: Float, maxSpeed: Float): Pair<AutoScrollArea, Float>? {
        if (dragged == null) return null
        val p = pointer
        return scrollAreas.values
            .filter { p.x >= it.bounds.left && p.x <= it.bounds.right }
            .filter { it.quadrant == null || it.quadrant == hoverTarget }
            .mapNotNull { area ->
                val r = area.bounds
                val zone = minOf(edge, r.height / 4f)
                if (zone <= 0f) return@mapNotNull null
                val depth = when {
                    p.y < r.top + zone && area.state.canScrollBackward -> -(r.top + zone - p.y) / zone
                    p.y > r.bottom - zone && area.state.canScrollForward -> (p.y - (r.bottom - zone)) / zone
                    else -> return@mapNotNull null
                }.coerceIn(-1f, 1f)
                area to maxSpeed * depth * kotlin.math.abs(depth)
            }
            .minByOrNull { it.first.bounds.height }
    }

    /** Termine le geste : déplacement vers un autre quadrant, réordonnancement, ou rien (null). */
    fun end(taskId: Long): DragResult? {
        val task = dragged?.takeIf { it.id == taskId } ?: return null
        val target = hoverTarget
        val slot = dropSlot
        val result = when {
            target == null || slot == null -> null
            target != task.quadrant -> DragResult.Move(task, target, slot.beforeId)
            else -> DragResult.Reorder(task, slot.beforeId)
        }
        reset()
        if (result != null) {
            // On fige l'aperçu jusqu'à ce que la base émette le nouvel ordre (voir settleOrders).
            preview = DropPreview(task.id, task.quadrant, target, slot?.beforeId, rowHeight)
            settleOrders = setOfNotNull(task.quadrant, target).associateWith { quadrantOrder[it].orEmpty() }
        }
        return result
    }

    fun cancel(taskId: Long) {
        if (dragged?.id == taskId) reset()
    }

    /**
     * Décalage d'une ligne, en **nombre d'emplacements** (−1, 0, +1) : −1 sous la tâche saisie dans
     * son quadrant d'origine (le trou se referme), +1 à partir du point d'insertion dans la cible
     * (l'écart s'ouvre) ; les deux se compensent entre l'ancienne et la nouvelle place. [order] =
     * ordre **affiché** par la cellule dans cette composition (voir [settleOrders]).
     */
    fun slotShift(id: Long, quadrant: Quadrant, order: List<Long>): Int {
        val p = preview ?: return 0
        if (id == p.taskId || p.target == null || !applies(quadrant, order)) return 0
        val index = order.indexOf(id)
        var shift = 0
        if (quadrant == p.source && index > order.indexOf(p.taskId)) shift--
        if (quadrant == p.target) {
            val before = p.beforeId?.let { order.indexOf(it) } ?: -1
            if (before >= 0 && index >= before) shift++
        }
        return shift
    }

    /** La ligne de la tâche saisie est masquée (l'overlay la représente ; son emplacement reste vide). */
    fun isHidden(id: Long, quadrant: Quadrant, order: List<Long>): Boolean =
        preview?.taskId == id && applies(quadrant, order)

    /**
     * Hauteur (px, sans espacement) d'un emplacement vide à ajouter en fin de [quadrant] quand la
     * tâche y arrive d'un autre quadrant : la liste grandit d'une ligne, comme après le dépôt (sinon la
     * dernière ligne, poussée vers le bas, déborderait de la cellule).
     */
    fun trailingGap(quadrant: Quadrant, order: List<Long>): Float {
        val p = preview ?: return 0f
        return if (p.target == quadrant && p.source != quadrant && applies(quadrant, order)) p.rowHeight else 0f
    }

    /** Appelé par une cellule après publication de son ordre : lève l'aperçu une fois la base à jour. */
    fun onOrderPublished() {
        val settle = settleOrders ?: return
        if (settle.all { (quadrant, frozen) -> quadrantOrder[quadrant] != frozen }) clearPreview()
    }

    /** Lève l'aperçu figé (filet de sécurité si la base n'émet jamais d'ordre différent). */
    fun clearPreview() {
        if (dragged != null) return
        preview = null
        settleOrders = null
    }

    /** Pendant le drag : toujours ; après le dépôt : tant que la cellule affiche l'ordre figé. */
    private fun applies(quadrant: Quadrant, order: List<Long>): Boolean {
        val frozen = settleOrders?.get(quadrant) ?: return true
        return order == frozen
    }

    private fun reset() {
        dragged = null
        hoverTarget = null
        dropSlot = null
        preview = null
        settleOrders = null
    }

    private fun update(task: Task) {
        val target = bounds.entries.firstOrNull { it.value.contains(pointer) }?.key
        val slot = target?.let { slotIn(task, it) }
        hoverTarget = target
        dropSlot = slot
        preview = DropPreview(task.id, task.quadrant, target?.takeIf { slot != null }, slot?.beforeId, rowHeight)
    }

    /**
     * Point d'insertion d'après la hauteur du doigt, calculé sur les seules lignes **visibles** : on
     * insère juste après la dernière ligne visible dont le centre est au-dessus du doigt (donc devant
     * la tâche qui la suit dans l'ordre affiché), ou devant la première ligne visible sinon.
     */
    private fun slotIn(task: Task, target: Quadrant): DropSlot? {
        if (!manualOrder) return if (target == task.quadrant) null else DropSlot(null)
        val order = quadrantOrder[target].orEmpty()
        val others = order.filter { it != task.id }
        val visible = visibleOthers(task, target)
        val beforeId = if (visible.isEmpty()) {
            null
        } else {
            val above = visible.lastOrNull { itemBounds.getValue(it).center.y < pointer.y }
            if (above == null) visible.first() else others.getOrNull(others.indexOf(above) + 1)
        }
        if (target == task.quadrant) {
            // Aucune autre ligne visible, ou lâcher là où elle est déjà (devant la même voisine) :
            // pas de dépôt.
            if (visible.isEmpty()) return null
            val index = order.indexOf(task.id)
            if (index >= 0 && order.getOrNull(index + 1) == beforeId) return null
        }
        return DropSlot(beforeId)
    }

    private fun visibleOthers(task: Task, quadrant: Quadrant): List<Long> =
        quadrantOrder[quadrant].orEmpty().filter { it != task.id && visibleRect(it) != null }

    private fun visibleRect(id: Long): Rect? = itemBounds[id]?.takeIf { !it.isEmpty }
}
