package com.kairos.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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

/** Formate une échéance en date + heure localisées (locale système courante). */
@Composable
fun formatDue(dueAt: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(dueAt))
}
