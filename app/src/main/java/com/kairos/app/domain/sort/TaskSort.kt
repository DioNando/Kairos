package com.kairos.app.domain.sort

import com.kairos.app.data.model.AppSettings
import com.kairos.app.data.model.Task
import com.kairos.app.domain.focus.FocusSelector
import java.text.Collator

/**
 * Tri des tâches **dans un quadrant** (réglage `AppSettings.quadrantSort`). La liste reçue est déjà
 * dans l'ordre manuel (position) : le tri manuel la renvoie telle quelle, les autres la réordonnent
 * en gardant l'ordre manuel comme dernier départage.
 */
object TaskSort {

    fun apply(tasks: List<Task>, sort: Int, focusDueWeight: Int, now: Long): List<Task> = when (sort) {
        AppSettings.SORT_DUE -> tasks.sortedWith(compareBy(nullsLast()) { it.dueAt })
        AppSettings.SORT_CREATED -> tasks.sortedByDescending { it.createdAt }
        AppSettings.SORT_PRIORITY -> tasks.sortedWith(FocusSelector.comparator(focusDueWeight, now))
        AppSettings.SORT_ALPHA -> {
            // Collator : ordre alphabétique naturel (accents et casse ignorés : « école » avant « Zèbre »).
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            tasks.sortedWith { a, b -> collator.compare(a.title, b.title) }
        }
        else -> tasks
    }
}
