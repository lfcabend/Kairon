package com.kairon.android.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface TodoDao {

    @Query("SELECT * FROM todo_item WHERE day = :day ORDER BY position ASC, createdAt ASC")
    fun observeForDay(day: LocalDate): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_item WHERE day = :day ORDER BY position ASC, createdAt ASC")
    suspend fun listForDay(day: LocalDate): List<TodoItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TodoItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TodoItemEntity>)

    @Query("DELETE FROM todo_item WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM todo_item WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<UUID>)

    @Delete
    suspend fun delete(item: TodoItemEntity)
}
