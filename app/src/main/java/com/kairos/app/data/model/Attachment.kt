package com.kairos.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pièce jointe légère d'une tâche. Le fichier est **copié** dans le stockage privé de l'app
 * ([fileName], relatif au dossier des pièces jointes) : il reste lisible même si l'original est
 * déplacé ou supprimé. La suppression de la tâche supprime la ligne (FK `ON DELETE CASCADE`) ; les
 * fichiers orphelins sont balayés au démarrage.
 */
@Entity(
    tableName = "attachments",
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
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val displayName: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long = 0,
)
