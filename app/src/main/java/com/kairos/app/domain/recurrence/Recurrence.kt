package com.kairos.app.domain.recurrence

import java.time.Instant
import java.time.ZoneId

/**
 * Récurrence d'une tâche. Une tâche récurrente **régénère** une nouvelle occurrence à sa complétion
 * (la tâche terminée part à l'archive, une copie active est recréée avec l'échéance avancée).
 */
enum class Recurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    /**
     * Avance une date ([fromMillis], epoch ms) d'un pas de récurrence, ou `null` pour [NONE].
     * L'heure du jour est conservée (arithmétique calendaire, robuste aux mois de longueurs variées).
     */
    fun next(fromMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val base = Instant.ofEpochMilli(fromMillis).atZone(zone)
        val advanced = when (this) {
            NONE -> return null
            DAILY -> base.plusDays(1)
            WEEKLY -> base.plusWeeks(1)
            MONTHLY -> base.plusMonths(1)
        }
        return advanced.toInstant().toEpochMilli()
    }

    companion object {
        fun of(ordinal: Int): Recurrence = entries.getOrElse(ordinal) { NONE }
    }
}
