package com.kairos.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kairos.app.data.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** Toutes les tâches, actives puis terminées, triées par position dans leur quadrant. */
    @Query("SELECT * FROM tasks ORDER BY position ASC, createdAt DESC")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAll(): List<Task>

    /** Plus grande position dans un quadrant donné (pour ajouter en fin). */
    @Query("SELECT COALESCE(MAX(position), -1) FROM tasks WHERE important = :important AND urgent = :urgent")
    suspend fun maxPosition(important: Boolean, urgent: Boolean): Int

    /** Tâches **actives** d'un quadrant (ni terminées, ni retirées), triées par position. */
    @Query(
        "SELECT * FROM tasks WHERE important = :important AND urgent = :urgent " +
            "AND completedAt IS NULL AND removedAt IS NULL " +
            "ORDER BY position ASC, createdAt DESC",
    )
    suspend fun activeInQuadrant(important: Boolean, urgent: Boolean): List<Task>

    @Query("UPDATE tasks SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    /** Cumule des secondes de concentration (Pomodoro lié) sur une tâche. */
    @Query("UPDATE tasks SET focusSeconds = focusSeconds + :seconds WHERE id = :id")
    suspend fun addFocusSeconds(id: Long, seconds: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Vide l'archive : supprime toutes les tâches terminées **ou** retirées. */
    @Query("DELETE FROM tasks WHERE completedAt IS NOT NULL OR removedAt IS NOT NULL")
    suspend fun clearArchived()

    /** Purge les tâches archivées (terminées ou retirées) entrées dans l'archive avant [cutoff]. */
    @Query(
        "DELETE FROM tasks WHERE (completedAt IS NOT NULL AND completedAt < :cutoff) " +
            "OR (removedAt IS NOT NULL AND removedAt < :cutoff)",
    )
    suspend fun purgeArchivedBefore(cutoff: Long)

    /**
     * Nettoyage unique (migration de comportement) : supprime les occurrences **récurrentes
     * terminées** archivées par l'ancien modèle (complétion = spawn + archive). Le nouveau modèle
     * fait avancer la récurrence sur place, donc une telle ligne est forcément un cycle périmé —
     * la restaurer recréerait un doublon.
     */
    @Query("DELETE FROM tasks WHERE completedAt IS NOT NULL AND recurrence != 0")
    suspend fun purgeLegacyRecurringCompleted()

    @Query("DELETE FROM tasks")
    suspend fun clear()
}
