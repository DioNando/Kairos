package com.kairos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kairos.app.data.model.TimeBlock
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeBlockDao {

    @Query("SELECT * FROM time_blocks ORDER BY start ASC")
    fun observeAll(): Flow<List<TimeBlock>>

    @Query("SELECT * FROM time_blocks ORDER BY start ASC")
    suspend fun getAll(): List<TimeBlock>

    @Query("SELECT * FROM time_blocks WHERE id = :id")
    suspend fun getById(id: Long): TimeBlock?

    @Query("SELECT * FROM time_blocks WHERE taskId = :taskId")
    suspend fun forTask(taskId: Long): List<TimeBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: TimeBlock): Long

    @Update
    suspend fun update(block: TimeBlock)

    @Query("DELETE FROM time_blocks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM time_blocks WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("DELETE FROM time_blocks WHERE taskId = :taskId AND doneAt IS NULL")
    suspend fun deleteOpenForTask(taskId: Long)

    @Query("DELETE FROM time_blocks")
    suspend fun clear()
}
