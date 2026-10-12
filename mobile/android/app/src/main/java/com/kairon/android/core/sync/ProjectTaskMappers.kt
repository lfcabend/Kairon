package com.kairon.android.core.sync

import com.kairon.android.client.model.ProjectTaskResponse
import com.kairon.android.client.model.ProjectTaskView
import com.kairon.android.core.data.ProjectTaskEntity

// Same `!!` contract-assumption posture as TodoMappers.kt — see that file's
// own comment for the rationale.

fun ProjectTaskView.toEntity(): ProjectTaskEntity = ProjectTaskEntity(
    id = id!!,
    projectId = projectId!!,
    projectName = projectName!!,
    projectColor = projectColor!!,
    parentTaskId = parentTaskId,
    name = name!!,
    description = description,
    status = status!!,
    isMilestone = isMilestone!!,
    plannedStart = plannedStart,
    plannedEnd = plannedEnd,
    estimateHours = estimateHours,
    actualHours = actualHours,
    progressPercent = progressPercent!!,
    position = position!!,
    createdAt = createdAt!!.toInstant(),
    updatedAt = updatedAt!!.toInstant(),
    version = version!!,
)

/**
 * Unlike [ProjectTaskView] (the sync/list shape), a single-task write
 * response carries no denormalized `projectName`/`projectColor` — the caller
 * already has both from the project it's editing a task within, so they're
 * threaded through here rather than re-fetched.
 */
fun ProjectTaskResponse.toEntity(projectName: String, projectColor: String): ProjectTaskEntity = ProjectTaskEntity(
    id = id!!,
    projectId = projectId!!,
    projectName = projectName,
    projectColor = projectColor,
    parentTaskId = parentTaskId,
    name = name!!,
    description = description,
    status = status!!,
    isMilestone = isMilestone!!,
    plannedStart = plannedStart,
    plannedEnd = plannedEnd,
    estimateHours = estimateHours,
    actualHours = actualHours,
    progressPercent = progressPercent!!,
    position = position!!,
    createdAt = createdAt!!.toInstant(),
    updatedAt = updatedAt!!.toInstant(),
    version = version!!,
)
