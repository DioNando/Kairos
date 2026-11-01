package com.kairos.app.domain.recurrence

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Récurrence d'une tâche. À la complétion, une tâche récurrente **avance sur place** : elle reste
 * active sur le board, son échéance passe au pas suivant (via [next]) — elle ne va pas à l'archive.
 */
enum class Recurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    /**
     * Première occurrence de la série partant de [fromMillis] (epoch ms) située après [after] : au
     * moins un pas de récurrence, plus si des occurrences ont été manquées. `null` pour [NONE].
     * L'heure du jour est conservée (arithmétique calendaire, robuste aux mois de longueurs variées).
     */
    fun next(fromMillis: Long, after: Long = fromMillis, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val unit = when (this) {
            NONE -> return null
            DAILY -> ChronoUnit.DAYS
            WEEKLY -> ChronoUnit.WEEKS
            MONTHLY -> ChronoUnit.MONTHS
        }
        val base = Instant.ofEpochMilli(fromMillis).atZone(zone)
        val limit = Instant.ofEpochMilli(after).atZone(zone)
        var steps = maxOf(1L, unit.between(base, limit))
        // Toujours compté depuis la base : un 31 ne dérive pas au 28 après un passage par février.
        while (!base.plus(steps, unit).isAfter(limit)) steps++
        return base.plus(steps, unit).toInstant().toEpochMilli()
    }

    companion object {
        fun of(ordinal: Int): Recurrence = entries.getOrElse(ordinal) { NONE }
    }
}
