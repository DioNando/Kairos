package com.kairos.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kairos.app.domain.recurrence.Recurrence

/**
 * Une tâche de la matrice.
 *
 * Le quadrant n'est **pas** stocké : il se dérive des deux axes [important] × [urgent] (placement
 * sémantique, écrit à la main via le menu « Déplacer vers… » ou l'éditeur). L'échéance [dueAt] ne
 * modifie **jamais** ces axes : sa proximité est calculée à l'affichage (pulsation + suggestion).
 */
@Entity(
    tableName = "tasks",
    indices = [Index("labelId"), Index("dueAt"), Index("completedAt")],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val important: Boolean = false,
    val urgent: Boolean = false,
    /** Échéance optionnelle (epoch millis). */
    val dueAt: Long? = null,
    /** Étiquette optionnelle (catégorie). Null = sans étiquette. */
    val labelId: Long? = null,
    /** Ordre au sein d'un quadrant (drag/réordonnancement). */
    val position: Int = 0,
    val createdAt: Long = 0,
    /** Null = pas terminée ; renseignée = **terminée** (accomplie) → archive. */
    val completedAt: Long? = null,
    /**
     * Null = pas retirée ; renseignée = **retirée** (glissée hors du board sans être faite) → archive.
     * Distinct de [completedAt] : un retrait ne régénère jamais d'occurrence récurrente.
     */
    val removedAt: Long? = null,
    /** Récurrence (ordinal de [Recurrence]) : 0 = aucune. À la complétion, avance l'échéance sur place. */
    @ColumnInfo(defaultValue = "0") val recurrence: Int = 0,
    /** Temps de concentration cumulé (secondes) via les sessions Pomodoro liées. */
    @ColumnInfo(defaultValue = "0") val focusSeconds: Int = 0,
    /** Time-blocking : début du créneau d'agenda réservé à la tâche (epoch millis), null = non planifiée. */
    val blockStart: Long? = null,
    /** Durée du créneau (minutes). 0 = durée par défaut ([DEFAULT_BLOCK_MINUTES]). */
    @ColumnInfo(defaultValue = "0") val blockMinutes: Int = 0,
) {
    val quadrant: Quadrant get() = Quadrant.of(important, urgent)

    /** Terminée (accomplie). */
    val isCompleted: Boolean get() = completedAt != null

    /** Retirée (glissée hors du board sans être faite). */
    val isRemoved: Boolean get() = removedAt != null

    /** Sur le board : ni terminée, ni retirée. */
    val isActive: Boolean get() = completedAt == null && removedAt == null

    /** Dans l'archive : terminée **ou** retirée. */
    val isArchived: Boolean get() = !isActive

    /** Horodatage d'entrée dans l'archive (pour le tri), ou null si active. */
    val archivedAt: Long? get() = completedAt ?: removedAt

    val recurrenceType: Recurrence get() = Recurrence.of(recurrence)

    /** Durée effective du créneau planifié (minutes). */
    val blockDuration: Int get() = if (blockMinutes > 0) blockMinutes else DEFAULT_BLOCK_MINUTES

    /** Fin du créneau planifié (epoch millis), ou null si non planifiée. */
    val blockEnd: Long? get() = blockStart?.let { it + blockDuration * 60_000L }

    companion object {
        const val DEFAULT_BLOCK_MINUTES = 30
    }
}
