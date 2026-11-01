package com.kairos.app.domain

import com.kairos.app.data.model.Task
import com.kairos.app.data.model.TimeBlock
import com.kairos.app.domain.planning.DayPlan
import com.kairos.app.domain.planning.Interval
import com.kairos.app.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DayPlanTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    /** Minuit, multiple exact du quart d'heure. */
    private val day = 1_000_000L * 15 * minute

    private fun at(h: Int, m: Int = 0) = day + h * hour + m * minute

    private val window = Interval(at(8), at(20))

    private fun task(id: Long, important: Boolean = true, urgent: Boolean = true, estimate: Int = 0) =
        Task(id = id, title = "t$id", important = important, urgent = urgent, estimateMinutes = estimate)

    private fun block(id: Long, taskId: Long, start: Long, minutes: Int = 30, doneAt: Long? = null) =
        TimeBlock(id = id, taskId = taskId, start = start, minutes = minutes, doneAt = doneAt)

    // --- Temps libre --------------------------------------------------------------------------------

    @Test
    fun freeSlots_emptyDay_isTheWholeWindow() {
        assertEquals(listOf(window), DayPlan.freeSlots(window, Long.MIN_VALUE, emptyList()))
    }

    @Test
    fun freeSlots_todayStartsAtTheNextQuarterHour() {
        assertEquals(at(12, 45), DayPlan.freeSlots(window, at(12, 31), emptyList()).single().start)
    }

    @Test
    fun freeSlots_mergesOverlappingOccupations_andSplitsAroundThem() {
        val occupied = listOf(
            Interval(at(14), at(15)),
            Interval(at(9), at(10)),
            Interval(at(9, 30), at(11)),
            Interval(at(19), at(21)),
        )
        val expected = listOf(
            Interval(at(8), at(9)),
            Interval(at(11), at(14)),
            Interval(at(15), at(19)),
        )
        assertEquals(expected, DayPlan.freeSlots(window, Long.MIN_VALUE, occupied))
    }

    @Test
    fun freeSlots_ignoresGapsTooShortToBook_andEndsOnTheQuarterHour() {
        val occupied = listOf(Interval(at(8), at(9, 5)), Interval(at(9, 10), at(10)))
        assertEquals(at(10), DayPlan.freeSlots(window, Long.MIN_VALUE, occupied).first().start)
    }

    @Test
    fun freeSlots_afterTheWindow_isEmpty() {
        assertTrue(DayPlan.freeSlots(window, at(21), emptyList()).isEmpty())
    }

    @Test
    fun firstFit_skipsSlotsTooShort_andHonorsAfter() {
        val slots = listOf(Interval(at(9), at(9, 30)), Interval(at(13), at(16)))
        assertEquals(at(13), DayPlan.firstFit(slots, 60))
        assertEquals(at(14, 15), DayPlan.firstFit(slots, 60, after = at(14, 10)))
        assertNull(DayPlan.firstFit(slots, 240))
    }

    // --- Sessions ---------------------------------------------------------------------------------

    @Test
    fun remaining_withoutEstimate_oneSessionIsEnough() {
        assertEquals(Task.DEFAULT_BLOCK_MINUTES, DayPlan.remainingMinutes(task(1), emptyList()))
        assertEquals(0, DayPlan.remainingMinutes(task(1), listOf(block(1, 1, at(9)))))
    }

    @Test
    fun remaining_withEstimate_countsEverySession() {
        val blocks = listOf(block(1, 1, at(9), 60), block(2, 1, at(14), 30, doneAt = at(15)))
        assertEquals(90, DayPlan.remainingMinutes(task(1, estimate = 180), blocks))
        assertEquals(0, DayPlan.remainingMinutes(task(1, estimate = 60), blocks))
    }

    @Test
    fun finishesTask_onlyWhenNothingIsLeft() {
        val first = block(1, 1, at(9), 60, doneAt = at(10))
        val second = block(2, 1, at(14), 60)
        // Une autre session reste à faire.
        assertFalse(DayPlan.finishesTask(task(1), listOf(first.copy(doneAt = null), second), 1))
        // Dernière session mais l'estimation n'est pas couverte.
        assertFalse(DayPlan.finishesTask(task(1, estimate = 180), listOf(first, second), 2))
        // Dernière session, estimation couverte ou absente.
        assertTrue(DayPlan.finishesTask(task(1, estimate = 120), listOf(first, second), 2))
        assertTrue(DayPlan.finishesTask(task(1), listOf(first, second), 2))
    }

    @Test
    fun missed_keepsOnlyPastOpenSessionsOfActiveTasks_oldestFirst() {
        val tasks = mapOf(
            1L to task(1),
            2L to task(2).copy(completedAt = at(9)),
            3L to task(3).copy(removedAt = at(9)),
        )
        val blocks = listOf(
            block(1, 1, at(11)),
            block(2, 1, at(9)),
            block(3, 1, at(10), doneAt = at(10)),
            block(4, 1, at(13)),
            block(5, 2, at(9)),
            block(6, 3, at(9)),
            block(7, 99, at(9)),
        )
        assertEquals(listOf(2L, 1L), DayPlan.missed(blocks, tasks, at(12)).map { it.id })
    }

    @Test
    fun missed_aSessionEndingNowIsMissed_butNotOneInProgress() {
        val blocks = listOf(block(1, 1, at(11), 60), block(2, 1, at(11, 30), 60))
        assertEquals(listOf(1L), DayPlan.missed(blocks, mapOf(1L to task(1)), at(12)).map { it.id })
    }

    @Test
    fun nextBlocks_picksTheEarliestOpenSessionNotYetOver() {
        val blocks = listOf(block(1, 1, at(8)), block(2, 1, at(15)), block(3, 1, at(13)), block(4, 2, at(14), doneAt = at(9)))
        assertEquals(mapOf(1L to 3L), DayPlan.nextBlocks(blocks, at(12)).mapValues { it.value.id })
    }

    @Test
    fun conflicts_detectsSessionsAndEvents_butNotAdjacentOnes() {
        val blocks = listOf(
            block(1, 1, at(11), 30),
            block(2, 2, at(11, 15), 15),
            block(3, 3, at(14), 60),
            block(4, 4, at(16), 30),
            block(5, 5, at(16, 30), 30),
        )
        val events = listOf(Interval(at(14, 30), at(15, 30)))
        assertEquals(setOf(1L, 2L, 3L), DayPlan.conflicts(blocks, events))
    }

    // --- Remplissage --------------------------------------------------------------------------------

    private fun propose(ranked: List<Task>, occupied: List<Interval> = emptyList(), from: Long = at(12, 30)) =
        DayPlan.propose(ranked, window, from, occupied) { DayPlan.remainingMinutes(it, emptyList()) }

    @Test
    fun propose_followsPriority_withoutOverlap_andStopsAtTheLimit() {
        val placements = propose((1L..6L).map { task(it) }, listOf(Interval(at(13), at(14))))
        assertEquals(listOf(1L, 2L, 3L, 4L), placements.map { it.task.id })
        assertEquals(listOf(at(12, 30), at(14), at(14, 30), at(15)), placements.map { it.start })
    }

    @Test
    fun propose_neverSuggestsEliminateTasks() {
        val eliminate = task(1, important = false, urgent = false)
        assertEquals(listOf(2L), propose(listOf(eliminate, task(2))).map { it.task.id })
    }

    @Test
    fun propose_splitsALongTaskOverTheLargestSlot() {
        val placement = propose(listOf(task(1, estimate = 240)), listOf(Interval(at(14), at(20)))).single()
        assertEquals(at(12, 30), placement.start)
        assertEquals(90, placement.minutes)
    }

    @Test
    fun propose_skipsATaskWhenNoSlotIsWorthASplit() {
        val occupied = listOf(Interval(at(8), at(19, 45)))
        assertTrue(propose(listOf(task(1, estimate = 240)), occupied).isEmpty())
    }

    // --- Dépôt sur la frise -------------------------------------------------------------------------

    @Test
    fun snapDrop_snapsToTheNearestQuarter() {
        assertEquals(at(9, 15), DayPlan.snapDrop(at(9, 12), 30, at(8), at(20)))
        assertEquals(at(9), DayPlan.snapDrop(at(9, 7), 30, at(8), at(20)))
    }

    @Test
    fun snapDrop_refusesThePast_andKeepsTheSessionInsideTheDay() {
        assertEquals(at(12, 45), DayPlan.snapDrop(at(8), 30, at(12, 31), at(20)))
        assertEquals(at(19), DayPlan.snapDrop(at(19, 50), 60, at(8), at(20)))
    }

    @Test
    fun snapDrop_whenTheDayIsOver_hasNoPlaceLeft() {
        assertNull(DayPlan.snapDrop(at(19), 60, at(19, 30), at(20)))
    }

    @Test
    fun formatDuration_coversEveryShape() {
        assertEquals("45 min", formatDuration(45))
        assertEquals("2 h", formatDuration(120))
        assertEquals("1 h 30", formatDuration(90))
        assertEquals("1 h 05", formatDuration(65))
    }
}
