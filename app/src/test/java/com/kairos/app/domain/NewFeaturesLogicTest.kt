package com.kairos.app.domain

import com.kairos.app.data.calendar.IcalParser
import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.model.Task
import com.kairos.app.domain.focus.FocusSelector
import com.kairos.app.domain.sort.TaskSort
import com.kairos.app.ui.components.noteChecklist
import com.kairos.app.ui.components.toggleNoteCheckbox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NewFeaturesLogicTest {

    private val hour = 3_600_000L
    private val now = 1_000_000_000_000L

    @Test
    fun focusWeight_zeroRanksByImportance_hundredRanksByDue() {
        val importantNoDue = Task(id = 1, title = "a", important = true)
        val overdueMinor = Task(id = 2, title = "b", dueAt = now - hour)
        val tasks = listOf(overdueMinor, importantNoDue)
        assertEquals(1L, FocusSelector.rank(tasks, dueWeight = 0, now = now).first().id)
        assertEquals(2L, FocusSelector.rank(tasks, dueWeight = 100, now = now).first().id)
    }

    @Test
    fun taskSort_dueThenNullsLast_andAlphaIgnoresAccents() {
        val a = Task(id = 1, title = "Zèbre", dueAt = now + 2 * hour)
        val b = Task(id = 2, title = "école", dueAt = null)
        val c = Task(id = 3, title = "Banane", dueAt = now + hour)
        assertEquals(listOf(3L, 1L, 2L), TaskSort.apply(listOf(a, b, c), AppSettings.SORT_DUE, 30, now).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), TaskSort.apply(listOf(a, b, c), AppSettings.SORT_ALPHA, 30, now).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), TaskSort.apply(listOf(a, b, c), AppSettings.SORT_MANUAL, 30, now).map { it.id })
    }

    @Test
    fun noteCheckboxes_toggleAndCount() {
        val note = "Courses\n- [ ] lait\n- [x] pain"
        assertEquals(1 to 2, noteChecklist(note))
        val toggled = toggleNoteCheckbox(note, 1)
        assertEquals("Courses\n- [x] lait\n- [x] pain", toggled)
        assertEquals(note, toggleNoteCheckbox(note, 0))
        assertNull(noteChecklist("pas de case"))
    }

    @Test
    fun ical_parsesTimedAllDayAndWeeklyRecurrence() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART:20261026T090000Z
            DTEND:20261026T100000Z
            SUMMARY:Réunion\, hebdo
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20261027
            SUMMARY:Férié
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20261027T120000Z
            DTEND:20261027T130000Z
            SUMMARY:Libre
            TRANSP:TRANSPARENT
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val events = IcalParser.parse(ics)
        assertEquals(2, events.size)
        assertEquals("Réunion, hebdo", events[0].title)

        val zone = ZoneId.systemDefault()
        val from = LocalDate.of(2026, 10, 26).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(2026, 11, 9).atStartOfDay(zone).toInstant().toEpochMilli()
        // COUNT=4 sur lundi + mercredi : 26/10, 28/10, 2/11, 4/11.
        assertEquals(4, events[0].occurrences(from, to).size)
        val allDay = events[1].occurrences(from, to)
        assertEquals(1, allDay.size)
        assertTrue(allDay[0].allDay)
        assertEquals(24 * hour, allDay[0].end - allDay[0].start)
    }
}
