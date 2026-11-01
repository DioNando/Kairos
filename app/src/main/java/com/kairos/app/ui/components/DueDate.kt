package com.kairos.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
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
 * Heure courante, rafraîchie chaque minute tant que l'écran est visible et dès qu'il le redevient :
 * le classement et l'état des échéances évoluent même si aucune tâche ne change.
 */
@Composable
fun rememberMinuteClock(): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(60_000L)
            }
        }
    }
    return now
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

/** Formate une durée : « 45 min », « 2 h », « 1 h 30 ». */
fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${"%02d".format(minutes % 60)}"
}

/**
 * Formate un créneau planifié : « 14:00–14:30 » s'il tombe aujourd'hui, sinon préfixé du jour
 * (« 28 oct. · 14:00–14:30 »).
 */
@Composable
fun formatBlock(start: Long, end: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val from = Instant.ofEpochMilli(start).atZone(zone)
    val to = Instant.ofEpochMilli(end).atZone(zone)
    val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    val range = "${time.format(from)}–${time.format(to)}"
    return if (from.toLocalDate() == LocalDate.now(zone)) range
    else "${DateTimeFormatter.ofPattern("d MMM", locale).format(from)} · $range"
}
