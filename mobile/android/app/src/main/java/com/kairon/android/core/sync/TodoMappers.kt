package com.kairon.android.core.sync

import com.kairon.android.client.model.TodoItemResponse
import com.kairon.android.client.model.TodoItemView
import com.kairon.android.core.data.TodoItemEntity

// The generated client types mark every field nullable (the backend's response
// DTOs carry no Bean Validation annotations for springdoc to read a `required`
// list from — see docs/milestones/M11-android-foundation.md). The core fields
// below are never actually absent in a well-formed response, so a `!!` here is
// a contract assumption, not a bug: a genuinely malformed response should fail
// loudly during sync rather than silently drop the row.

fun TodoItemView.toEntity(): TodoItemEntity = TodoItemEntity(
    id = id!!,
    day = day!!,
    title = title!!,
    notes = notes,
    status = status!!,
    priority = priority!!,
    position = position!!,
    estimateMinutes = estimateMinutes,
    sourceProjectTaskId = sourceProjectTaskId,
    rolledOverFromId = rolledOverFromId,
    completedAt = completedAt?.toInstant(),
    createdAt = createdAt!!.toInstant(),
    updatedAt = updatedAt!!.toInstant(),
    version = version!!,
)

fun TodoItemResponse.toEntity(): TodoItemEntity = TodoItemEntity(
    id = id!!,
    day = day!!,
    title = title!!,
    notes = notes,
    status = status!!,
    priority = priority!!,
    position = position!!,
    estimateMinutes = estimateMinutes,
    sourceProjectTaskId = sourceProjectTaskId,
    rolledOverFromId = rolledOverFromId,
    completedAt = completedAt?.toInstant(),
    createdAt = createdAt!!.toInstant(),
    updatedAt = updatedAt!!.toInstant(),
    version = version!!,
)
