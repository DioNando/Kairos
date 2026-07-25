package com.kairos.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** État d'échéance d'une tâche, pour la pulsation et la suggestion « passer en urgent ». */
enum class DueStatus { NONE, UPCOMING, SOON, OVERDUE }

/**
 * Calcule l'état d'échéance. [thresholdHours] = fenêtre en deçà de laquelle l'échéance est « proche »
 * (SOON). Une tâche terminée ou sans échéance renvoie [DueStatus.NONE].
 */
fun dueStatus(dueAt: Long?, now: Long, thresholdHours: Int, completed: Boolean): DueStatus {
    if (completed || dueAt == null) return DueStatus.NONE
    if (dueAt < now) return DueStatus.OVERDUE
    if (thresholdHours > 0 && dueAt - now <= thresholdHours * 3_600_000L) return DueStatus.SOON
    return DueStatus.UPCOMING
}

/**
 * Formate une échéance **compacte** : « 30 juil. · 09:00 » (l'année n'apparaît que si elle diffère de
 * l'année courante). Bien plus lisible qu'une date longue dans une carte étroite.
 */
@Composable
fun formatDue(dueAt: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(dueAt).atZone(zone)
    val sameYear = date.year == LocalDate.now(zone).year
    val pattern = if (sameYear) "d MMM · HH:mm" else "d MMM yyyy · HH:mm"
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}
