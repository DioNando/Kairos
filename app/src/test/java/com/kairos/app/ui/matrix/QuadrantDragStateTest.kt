package com.kairos.app.ui.matrix

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.kairos.app.data.model.Quadrant
import com.kairos.app.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class QuadrantDragStateTest {

    private fun task(id: Long, quadrant: Quadrant) =
        Task(id = id, title = "t$id", important = quadrant.important, urgent = quadrant.urgent)

    /** Quadrant DO (x 0..100, y 0..500) avec des lignes de 40 px espacées de 50 px, à partir de y = 0. */
    private fun state(vararg visibleIds: Long, order: List<Long> = visibleIds.toList()) =
        QuadrantDragState().apply {
            bounds[Quadrant.DO] = Rect(0f, 0f, 100f, 500f)
            bounds[Quadrant.SCHEDULE] = Rect(110f, 0f, 210f, 500f)
            quadrantOrder[Quadrant.DO] = order
            visibleIds.forEachIndexed { i, id -> itemBounds[id] = Rect(0f, i * 50f, 100f, i * 50f + 40f) }
        }

    /** Déplacement relatif du doigt (l'état reçoit des positions absolues). */
    private fun QuadrantDragState.move(taskId: Long, delta: Offset) = moveTo(taskId, pointer + delta)

    private fun QuadrantDragState.shifts(quadrant: Quadrant) =
        quadrantOrder.getValue(quadrant).associateWith { slotShift(it, quadrant, quadrantOrder.getValue(quadrant)) }

    @Test
    fun preview_reorderDown_closesHoleAndOpensGap() {
        val drag = state(1, 2, 3, 4)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        drag.move(1, Offset(0f, 110f)) // devant 4
        // 2 et 3 remontent combler le trou ; 4 ne bouge pas (fermeture + ouverture se compensent).
        assertEquals(mapOf(1L to 0, 2L to -1, 3L to -1, 4L to 0), drag.shifts(Quadrant.DO))
        assertEquals(true, drag.isHidden(1, Quadrant.DO, listOf(1L, 2L, 3L, 4L)))
    }

    @Test
    fun preview_inPlace_hasNoShift() {
        val drag = state(1, 2, 3)
        drag.start(task(2, Quadrant.DO), Offset(50f, 70f), rowHeight = 40f)
        assertEquals(mapOf(1L to 0, 2L to 0, 3L to 0), drag.shifts(Quadrant.DO))
    }

    @Test
    fun preview_crossQuadrant_opensGapInTargetAndGrowsList() {
        val drag = state(1, 2)
        drag.quadrantOrder[Quadrant.SCHEDULE] = listOf(7L, 8L)
        drag.itemBounds[7] = Rect(110f, 0f, 210f, 40f)
        drag.itemBounds[8] = Rect(110f, 50f, 210f, 90f)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        drag.move(1, Offset(100f, 40f)) // devant 8
        assertEquals(mapOf(1L to 0, 2L to -1), drag.shifts(Quadrant.DO))
        assertEquals(mapOf(7L to 0, 8L to 1), drag.shifts(Quadrant.SCHEDULE))
        assertEquals(40f, drag.trailingGap(Quadrant.SCHEDULE, listOf(7L, 8L)))
        assertEquals(0f, drag.trailingGap(Quadrant.DO, listOf(1L, 2L)))
    }

    @Test
    fun drop_keepsPreviewUntilNewOrderIsPublished() {
        val drag = state(1, 2, 3)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        drag.move(1, Offset(0f, 200f)) // en fin
        drag.end(1)
        // Base pas encore à jour : l'aperçu reste affiché tel quel.
        assertEquals(true, drag.isSettling)
        assertEquals(-1, drag.slotShift(2, Quadrant.DO, listOf(1L, 2L, 3L)))
        // Composition avec le nouvel ordre : plus aucun décalage, avant même la publication.
        assertEquals(0, drag.slotShift(2, Quadrant.DO, listOf(2L, 3L, 1L)))
        assertFalse(drag.isHidden(1, Quadrant.DO, listOf(2L, 3L, 1L)))
        drag.quadrantOrder[Quadrant.DO] = listOf(2L, 3L, 1L)
        drag.onOrderPublished()
        assertFalse(drag.isSettling)
        assertNull(drag.preview)
    }

    @Test
    fun cancelledOrNoOpDrop_clearsPreviewImmediately() {
        val drag = state(1, 2)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        assertNull(drag.end(1))
        assertFalse(drag.isSettling)
        assertNull(drag.preview)
    }

    @Test
    fun autoScroll_nearBottomEdge_scrollsHoveredList() {
        val drag = state(1, 2)
        val list = ScrollState(initial = 100)
        drag.scrollAreas[Quadrant.DO] = AutoScrollArea(Rect(0f, 100f, 100f, 400f), list, Quadrant.DO)
        drag.start(task(1, Quadrant.DO), Offset(50f, 200f), rowHeight = 40f)
        assertNull(drag.scrollTarget(edge = 50f, maxSpeed = 1000f)) // loin des bords
        drag.moveTo(1, Offset(50f, 400f)) // au ras du bord bas
        assertEquals(1000f, drag.scrollTarget(edge = 50f, maxSpeed = 1000f)?.second)
        drag.moveTo(1, Offset(50f, 75f)) // au-dessus du viewport (en-tête de la cellule)
        assertEquals(-1000f, drag.scrollTarget(edge = 50f, maxSpeed = 1000f)?.second)
        drag.moveTo(1, Offset(150f, 400f)) // autre quadrant survolé : cette liste ne défile pas
        assertNull(drag.scrollTarget(edge = 50f, maxSpeed = 1000f))
    }

    @Test
    fun reorder_downwards_anchorsBeforeFollowingTask() {
        val drag = state(1, 2, 3, 4)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        drag.move(1, Offset(0f, 110f)) // y = 130 : sous le centre de 3 (120), au-dessus de 4 (170)
        assertEquals(DragResult.Reorder(task(1, Quadrant.DO), beforeId = 4), drag.end(1))
    }

    @Test
    fun reorder_toEnd_hasNullAnchor() {
        val drag = state(1, 2, 3)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        drag.move(1, Offset(0f, 200f))
        assertEquals(DragResult.Reorder(task(1, Quadrant.DO), beforeId = null), drag.end(1))
    }

    @Test
    fun dropInPlace_isNoOp() {
        val drag = state(1, 2, 3)
        drag.start(task(2, Quadrant.DO), Offset(50f, 70f), rowHeight = 40f)
        drag.move(2, Offset(0f, 5f))
        assertNull(drag.end(2))
    }

    @Test
    fun filteredList_anchorsOnTaskIdNotIndex() {
        // Liste filtrée (ids non contigus) : l'ancre est un id, valable dans la liste complète du
        // quadrant, et non un index calculé sur la liste affichée.
        val drag = state(10, 20, 30)
        drag.start(task(30, Quadrant.DO), Offset(50f, 120f), rowHeight = 40f)
        drag.move(30, Offset(0f, -110f)) // y = 10 : au-dessus du centre de 10
        assertEquals(DragResult.Reorder(task(30, Quadrant.DO), beforeId = 10), drag.end(30))
    }

    @Test
    fun scrolledOutRows_areIgnored() {
        // 1..5 dans l'ordre, seules 3 et 4 composées (LazyColumn défilée) : 5 n'a plus de bornes.
        val drag = QuadrantDragState().apply {
            bounds[Quadrant.DO] = Rect(0f, 0f, 100f, 500f)
            quadrantOrder[Quadrant.DO] = listOf(1L, 2L, 3L, 4L, 5L)
            itemBounds[3] = Rect(0f, 0f, 100f, 40f)
            itemBounds[4] = Rect(0f, 50f, 100f, 90f)
        }
        drag.start(task(3, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        drag.move(3, Offset(0f, 60f)) // y = 80 : sous le centre de 4 → devant 5 (hors écran)
        assertEquals(DragResult.Reorder(task(3, Quadrant.DO), beforeId = 5), drag.end(3))
    }

    @Test
    fun moveToOtherQuadrant_keepsDropPosition() {
        val drag = state(1, 2)
        drag.quadrantOrder[Quadrant.SCHEDULE] = listOf(7L, 8L)
        drag.itemBounds[7] = Rect(110f, 0f, 210f, 40f)
        drag.itemBounds[8] = Rect(110f, 50f, 210f, 90f)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        assertEquals(true, drag.move(1, Offset(100f, 40f))) // (150, 60) : entre 7 et 8
        assertEquals(DragResult.Move(task(1, Quadrant.DO), Quadrant.SCHEDULE, beforeId = 8), drag.end(1))
    }

    @Test
    fun secondPointer_cannotHijackDrag() {
        val drag = state(1, 2, 3)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        assertFalse(drag.start(task(2, Quadrant.DO), Offset(50f, 70f), rowHeight = 40f))
        assertFalse(drag.move(2, Offset(500f, 0f)))
        assertNull(drag.end(2))
        drag.cancel(2)
        assertEquals(1L, drag.dragged?.id)
    }

    @Test
    fun dropOutsideQuadrants_isNoOp() {
        val drag = state(1, 2)
        drag.start(task(1, Quadrant.DO), Offset(50f, 20f), rowHeight = 40f)
        drag.move(1, Offset(0f, 1000f))
        assertNull(drag.end(1))
    }
}
