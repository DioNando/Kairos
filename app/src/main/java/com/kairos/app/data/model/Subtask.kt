package com.kairos.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Élément de checklist rattaché à une [Task]. La suppression de la tâche parente supprime ses
 * sous-tâches (FK `ON DELETE CASCADE`). [position] fixe l'ordre d'affichage dans l'éditeur.
 */
@Entity(
    tableName = "subtasks",
    indices = [Index("taskId")],
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Subtask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)
