package com.kairos.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Session de travail réservée à une [Task] (time-blocking). Une tâche peut en avoir plusieurs : un gros
 * travail se répartit sur différents créneaux. La suppression de la tâche supprime ses sessions.
 */
@Entity(
    tableName = "time_blocks",
    indices = [Index("taskId"), Index("start")],
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TimeBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    /** Début (epoch millis). */
    val start: Long,
    val minutes: Int,
    /** Null = à faire ; renseignée = session terminée. */
    val doneAt: Long? = null,
) {
    val end: Long get() = start + minutes * 60_000L

    val isDone: Boolean get() = doneAt != null
}
