package com.kairon.android.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Mirrors [com.kairon.android.client.model.JournalEntryView] field-for-field,
 * minus `deletedAt` — tombstones never reach the client as full rows (M11
 * §6.3, extended to journal for M11.5).
 */
@Entity(tableName = "journal_entry")
data class JournalEntryEntity(
    @PrimaryKey val id: UUID,
    val day: LocalDate,
    val position: Int,
    val title: String?,
    val content: String,
    val mood: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)
