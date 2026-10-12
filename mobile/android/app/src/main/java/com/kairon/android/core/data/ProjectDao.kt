package com.kairon.android.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ProjectDao {

    @Query("SELECT * FROM project WHERE id = :id")
    fun observeById(id: UUID): Flow<ProjectEntity?>

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun findById(id: UUID): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(projects: List<ProjectEntity>)

    @Query("DELETE FROM project WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM project WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<UUID>)

    @Query("DELETE FROM project")
    suspend fun clearAll()
}
