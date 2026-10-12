package com.kairon.android.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Mirrors [com.kairon.android.client.model.ProjectTaskView] field-for-field,
 * minus `deletedAt` (M11.5 D8).
 */
@Entity(tableName = "project_task")
data class ProjectTaskEntity(
    @PrimaryKey val id: UUID,
    val projectId: UUID,
    val projectName: String,
    val projectColor: String,
    val parentTaskId: UUID?,
    val name: String,
    val description: String?,
    val status: String,
    val isMilestone: Boolean,
    val plannedStart: LocalDate?,
    val plannedEnd: LocalDate?,
    val estimateHours: BigDecimal?,
    val actualHours: BigDecimal?,
    val progressPercent: Int,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
)
