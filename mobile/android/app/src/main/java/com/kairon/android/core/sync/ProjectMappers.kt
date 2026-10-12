package com.kairon.android.core.sync

import com.kairon.android.client.model.ProjectResponse
import com.kairon.android.client.model.ProjectView
import com.kairon.android.core.data.ProjectEntity

// Same `!!` contract-assumption posture as TodoMappers.kt — see that file's
// own comment for the rationale.

fun ProjectView.toEntity(): ProjectEntity = ProjectEntity(
    id = id!!,
    categoryId = categoryId,
    name = name!!,
    description = description,
    status = status!!,
    size = propertySize,
    priorityRank = priorityRank!!,
    categoryRank = categoryRank!!,
    color = color!!,
    startDate = startDate,
    endDate = endDate,
    actualStart = actualStart,
    actualEnd = actualEnd,
    createdAt = createdAt!!.toInstant(),
    updatedAt = updatedAt!!.toInstant(),
    version = version!!,
)

fun ProjectResponse.toEntity(): ProjectEntity = ProjectEntity(
    id = id!!,
    categoryId = categoryId,
    name = name!!,
    description = description,
    status = status!!,
    size = propertySize,
    priorityRank = priorityRank!!,
    categoryRank = categoryRank!!,
    color = color!!,
    startDate = startDate,
    endDate = endDate,
    actualStart = actualStart,
    actualEnd = actualEnd,
    createdAt = createdAt!!.toInstant(),
    updatedAt = updatedAt!!.toInstant(),
    version = version!!,
)
