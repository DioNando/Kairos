package com.kairos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kairos.app.data.model.Subtask
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtaskDao {

    /** Toutes les sous-tâches (toutes tâches confondues), pour dériver les compteurs. */
    @Query("SELECT * FROM subtasks ORDER BY position ASC")
    fun observeAll(): Flow<List<Subtask>>

    @Query("SELECT * FROM subtasks ORDER BY id ASC")
    suspend fun getAll(): List<Subtask>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    suspend fun forTask(taskId: Long): List<Subtask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subtask: Subtask): Long

    @Update
    suspend fun update(subtask: Subtask)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("DELETE FROM subtasks")
    suspend fun clear()
}
