package com.kairon.android.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ProjectTaskDao {

    @Query("SELECT * FROM project_task WHERE projectId = :projectId ORDER BY position ASC, createdAt ASC")
    fun observeForProject(projectId: UUID): Flow<List<ProjectTaskEntity>>

    @Query("SELECT * FROM project_task WHERE projectId = :projectId ORDER BY position ASC, createdAt ASC")
    suspend fun listForProject(projectId: UUID): List<ProjectTaskEntity>

    @Query("SELECT * FROM project_task WHERE id = :id")
    suspend fun findById(id: UUID): ProjectTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ProjectTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<ProjectTaskEntity>)

    @Query("DELETE FROM project_task WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM project_task WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<UUID>)

    @Query("DELETE FROM project_task")
    suspend fun clearAll()
}
