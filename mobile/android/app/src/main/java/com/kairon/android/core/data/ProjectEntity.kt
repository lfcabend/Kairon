package com.kairon.android.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Mirrors [com.kairon.android.client.model.ProjectView] field-for-field,
 * minus `deletedAt` (M11.5 D8). Note the backend's `size` field maps to
 * `propertySize` on the generated client (`size` collides with a Kotlin data
 * class synthesis internal) — kept as plain `size` here since Room entities
 * aren't generated code and don't share that constraint.
 */
@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey val id: UUID,
    val categoryId: UUID?,
    val name: String,
    val description: String?,
    val status: String,
    val size: String?,
    val priorityRank: Int,
    val categoryRank: Int,
    val color: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val actualStart: LocalDate?,
    val actualEnd: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)
