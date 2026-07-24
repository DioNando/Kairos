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

    /** Tâches **actives** d'un quadrant, triées par position (base du réordonnancement). */
    @Query(
        "SELECT * FROM tasks WHERE important = :important AND urgent = :urgent AND completedAt IS NULL " +
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

    /** Vide l'archive : supprime toutes les tâches terminées. */
    @Query("DELETE FROM tasks WHERE completedAt IS NOT NULL")
    suspend fun clearCompleted()

    /** Purge les tâches terminées avant [cutoff] (epoch ms). */
    @Query("DELETE FROM tasks WHERE completedAt IS NOT NULL AND completedAt < :cutoff")
    suspend fun purgeCompletedBefore(cutoff: Long)

    @Query("DELETE FROM tasks")
    suspend fun clear()
}
