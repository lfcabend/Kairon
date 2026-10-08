package com.kairon.android.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Mirrors [com.kairon.android.client.model.TodoItemView]/`TodoItemResponse`
 * field-for-field, minus `deletedAt` — tombstones never reach the client as
 * full rows (M11 §6.3). Room is the single source of truth the UI observes;
 * the network response is never rendered directly.
 */
@Entity(tableName = "todo_item")
data class TodoItemEntity(
    @PrimaryKey val id: UUID,
    val day: LocalDate,
    val title: String,
    val notes: String?,
    val status: String,
    val priority: Int,
    val position: Int,
    val estimateMinutes: Int?,
    val sourceProjectTaskId: UUID?,
    val rolledOverFromId: UUID?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)
