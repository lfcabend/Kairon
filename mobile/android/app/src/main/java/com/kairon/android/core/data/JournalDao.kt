package com.kairon.android.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entry WHERE day = :day ORDER BY position ASC, createdAt ASC")
    fun observeForDay(day: LocalDate): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entry WHERE day = :day ORDER BY position ASC, createdAt ASC")
    suspend fun listForDay(day: LocalDate): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entry WHERE id = :id")
    suspend fun findById(id: UUID): JournalEntryEntity?

    /** Backs D5's client-side calendar markers — no network call. */
    @Query("SELECT DISTINCT day FROM journal_entry WHERE day BETWEEN :from AND :to ORDER BY day")
    suspend fun findEntryDays(from: LocalDate, to: LocalDate): List<LocalDate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<JournalEntryEntity>)

    @Query("DELETE FROM journal_entry WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM journal_entry WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<UUID>)

    @Query("DELETE FROM journal_entry")
    suspend fun clearAll()
}
