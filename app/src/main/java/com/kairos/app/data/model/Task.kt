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
    /** Null = active ; renseignée = terminée/archivée. */
    val completedAt: Long? = null,
    /** Récurrence (ordinal de [Recurrence]) : 0 = aucune. À la complétion, régénère l'occurrence. */
    @ColumnInfo(defaultValue = "0") val recurrence: Int = 0,
    /** Temps de concentration cumulé (secondes) via les sessions Pomodoro liées. */
    @ColumnInfo(defaultValue = "0") val focusSeconds: Int = 0,
) {
    val quadrant: Quadrant get() = Quadrant.of(important, urgent)
    val isCompleted: Boolean get() = completedAt != null
    val recurrenceType: Recurrence get() = Recurrence.of(recurrence)
}
