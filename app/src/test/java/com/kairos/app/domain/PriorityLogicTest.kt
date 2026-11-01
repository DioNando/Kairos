package com.kairos.app.domain

import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task
import com.kairos.app.domain.focus.FocusSelector
import com.kairos.app.domain.recurrence.Recurrence
import com.kairos.app.ui.components.defaultDueTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class PriorityLogicTest {

    private val hour = 3_600_000L
    private val now = 1_000_000_000_000L
    private val utc = ZoneOffset.UTC

    private fun doTask(id: Long = 1, position: Int = 0, dueIn: Long? = null) =
        Task(id = id, title = "faire", important = true, urgent = true, position = position, dueAt = dueIn?.let { now + it * hour })

    private fun schedule(id: Long = 2, position: Int = 0, dueIn: Long? = null) =
        Task(id = id, title = "planifier", important = true, position = position, dueAt = dueIn?.let { now + it * hour })

    private fun delegate(id: Long = 3, position: Int = 0, dueIn: Long? = null) =
        Task(id = id, title = "déléguer", urgent = true, position = position, dueAt = dueIn?.let { now + it * hour })

    private fun eliminate(id: Long = 4, position: Int = 0, dueIn: Long? = null) =
        Task(id = id, title = "supprimer", position = position, dueAt = dueIn?.let { now + it * hour })

    private fun epoch(date: String, time: String = "09:00") =
        LocalDateTime.parse("${date}T$time").toInstant(utc).toEpochMilli()

    // --- Score et classement ---------------------------------------------------------------------

    @Test
    fun defaultWeight_quadrantCrossingsHappenAtTheExpectedHours() {
        // À 30, Planifier passe devant Faire sans date à ~10,3 h, Déléguer devant Planifier à ~1,7 h.
        val cases = listOf(
            Triple(schedule(dueIn = 11), doTask(), 1L),
            Triple(schedule(dueIn = 10), doTask(), 2L),
            Triple(delegate(dueIn = 2), schedule(), 2L),
            Triple(delegate(dueIn = 1), schedule(), 3L),
            Triple(eliminate(dueIn = 11), delegate(), 3L),
            Triple(eliminate(dueIn = 10), delegate(), 4L),
        )
        cases.forEach { (a, b, expected) ->
            assertEquals("${a.title} vs ${b.title}", expected, FocusSelector.rank(listOf(a, b), 30, now).first().id)
        }
    }

    @Test
    fun defaultWeight_neverLetsADelegateTaskPassADoTask() {
        val overdue = delegate(dueIn = -1000)
        assertEquals(1L, FocusSelector.rank(listOf(overdue, doTask()), 30, now).first().id)
    }

    @Test
    fun overdueTasks_oldestFirst_withinAQuadrant() {
        val recent = schedule(id = 1, dueIn = -1)
        val old = schedule(id = 2, dueIn = -720)
        assertEquals(listOf(2L, 1L), FocusSelector.rank(listOf(recent, old), 30, now).map { it.id })
    }

    @Test
    fun fullDueWeight_keepsQuadrantOrderForTasksWithoutDueDate() {
        val tasks = listOf(eliminate(position = 0), delegate(position = 2), schedule(position = 1), doTask(position = 3))
        assertEquals(listOf(1L, 2L, 3L, 4L), FocusSelector.rank(tasks, 100, now).map { it.id })
    }

    @Test
    fun sameQuadrantWithoutDueDate_keepsManualOrder() {
        val tasks = listOf(schedule(id = 1, position = 2), schedule(id = 2, position = 0), schedule(id = 3, position = 1))
        listOf(0, 30, 100).forEach { weight ->
            assertEquals(listOf(2L, 3L, 1L), FocusSelector.rank(tasks, weight, now).map { it.id })
        }
    }

    @Test
    fun archivedTasks_areExcluded() {
        val done = doTask(id = 1).copy(completedAt = now)
        val removed = doTask(id = 2).copy(removedAt = now)
        assertEquals(listOf(3L), FocusSelector.rank(listOf(done, removed, schedule(id = 3)), 30, now).map { it.id })
    }

    @Test
    fun everySliderNotch_changesTheRanking() {
        // Sur chaque paire de crans voisins, au moins une échéance doit départager autrement.
        val horizons = generateSequence(1.0) { it * 1.05 }.takeWhile { it < 200_000 }.map { it.toLong() }.distinct().toList()
        fun topIds(weight: Int) = horizons.flatMap { dueIn ->
            listOf(
                FocusSelector.rank(listOf(schedule(dueIn = dueIn), doTask()), weight, now).first().id,
                FocusSelector.rank(listOf(delegate(dueIn = dueIn), schedule()), weight, now).first().id,
            )
        }
        (0..95 step 5).forEach { notch ->
            assertTrue("crans $notch et ${notch + 5}", topIds(notch) != topIds(notch + 5))
        }
    }

    // --- Échéance des sous-tâches ------------------------------------------------------------------

    @Test
    fun subtaskDues_keepTheNearestPendingOne() {
        val dues = FocusSelector.subtaskDues(
            listOf(
                Subtask(taskId = 1, title = "a", dueAt = 50),
                Subtask(taskId = 1, title = "b", dueAt = 20),
                Subtask(taskId = 1, title = "faite", dueAt = 10, done = true),
                Subtask(taskId = 2, title = "sans date"),
                Subtask(taskId = 3, title = "faite", dueAt = 10, done = true),
            ),
        )
        assertEquals(mapOf(1L to 20L), dues)
    }

    @Test
    fun nearSubtaskDue_liftsItsTask_butNotALaterOne() {
        val withSubtask = schedule(id = 1, dueIn = 200)
        val other = schedule(id = 2, dueIn = 100)
        val tasks = listOf(other, withSubtask)
        assertEquals(2L, FocusSelector.rank(tasks, 30, now).first().id)
        assertEquals(1L, FocusSelector.rank(tasks, 30, now, mapOf(1L to now + hour)).first().id)
        assertEquals(2L, FocusSelector.rank(tasks, 30, now, mapOf(1L to now + 500 * hour)).first().id)
    }

    // --- Récurrence --------------------------------------------------------------------------------

    @Test
    fun recurrence_missedOccurrences_jumpToTheFirstFutureOne() {
        val due = epoch("2026-10-01")
        val completed = epoch("2026-10-06", "12:00")
        assertEquals(epoch("2026-10-07"), Recurrence.DAILY.next(due, completed, utc))
        assertEquals(epoch("2026-10-08"), Recurrence.WEEKLY.next(due, completed, utc))
        assertEquals(epoch("2026-11-01"), Recurrence.MONTHLY.next(due, completed, utc))
    }

    @Test
    fun recurrence_completedBeforeDue_advancesOneStep() {
        val due = epoch("2026-10-10")
        val completed = epoch("2026-10-09")
        assertEquals(epoch("2026-10-11"), Recurrence.DAILY.next(due, completed, utc))
        assertEquals(epoch("2026-11-10"), Recurrence.MONTHLY.next(due, completed, utc))
        assertEquals(epoch("2026-10-11"), Recurrence.DAILY.next(due, zone = utc))
    }

    @Test
    fun recurrence_completedExactlyAtAnOccurrence_goesPastIt() {
        val due = epoch("2026-10-01")
        assertEquals(epoch("2026-10-04"), Recurrence.DAILY.next(due, epoch("2026-10-03"), utc))
    }

    @Test
    fun recurrence_endOfMonth_doesNotDriftOnCatchUp() {
        val due = epoch("2026-01-31")
        assertEquals(epoch("2026-02-28"), Recurrence.MONTHLY.next(due, due, utc))
        assertEquals(epoch("2026-03-31"), Recurrence.MONTHLY.next(due, epoch("2026-03-30"), utc))
    }

    @Test
    fun recurrence_none_hasNoNextOccurrence() {
        assertNull(Recurrence.NONE.next(now, now, utc))
    }

    // --- Réglages et saisie ------------------------------------------------------------------------

    @Test
    fun legacyPrioritySort_becomesDueSort() {
        assertEquals(AppSettings.SORT_DUE, AppSettings.normalizeSort(3))
        listOf(AppSettings.SORT_MANUAL, AppSettings.SORT_DUE, AppSettings.SORT_CREATED, AppSettings.SORT_ALPHA).forEach {
            assertEquals(it, AppSettings.normalizeSort(it))
        }
        assertEquals(AppSettings.SORT_ALPHA, AppSettings.normalizeSort(99))
    }

    @Test
    fun defaultDueTime_neverProposesAPastTimeToday() {
        val today = LocalDate.of(2026, 10, 31)
        fun at(time: String) = LocalDateTime.of(today, LocalTime.parse(time))
        assertEquals(LocalTime.of(9, 0), defaultDueTime(today, at("08:30")))
        assertEquals(LocalTime.of(16, 0), defaultDueTime(today, at("15:20")))
        assertEquals(LocalTime.of(23, 59), defaultDueTime(today, at("23:10")))
        assertEquals(LocalTime.of(9, 0), defaultDueTime(today.plusDays(1), at("15:20")))
    }
}
