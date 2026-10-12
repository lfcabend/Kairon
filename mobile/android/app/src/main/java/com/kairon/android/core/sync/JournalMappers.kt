package com.kairon.android.core.sync

import com.kairon.android.client.model.JournalEntryResponse
import com.kairon.android.client.model.JournalEntryView
import com.kairon.android.core.data.JournalEntryEntity

// Same `!!` contract-assumption posture as TodoMappers.kt — see that file's
// own comment for the rationale.

fun JournalEntryView.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id!!,
    day = day!!,
    position = position!!,
    title = title,
    content = content!!,
    mood = mood,
    createdAt = createdAt!!.toInstant(),
    updatedAt = updatedAt!!.toInstant(),
    version = version!!,
)

fun JournalEntryResponse.toEntity(): JournalEntryEntity = JournalEntryEntity(
    id = id!!,
    day = day!!,
    position = position!!,
    title = title,
    content = content!!,
    mood = mood,
    createdAt = createdAt!!.toInstant(),
    updatedAt = updatedAt!!.toInstant(),
    version = version!!,
)
