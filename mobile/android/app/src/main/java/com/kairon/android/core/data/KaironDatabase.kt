package com.kairon.android.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TodoItemEntity::class, JournalEntryEntity::class, ProjectEntity::class, ProjectTaskEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class KaironDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun journalDao(): JournalDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectTaskDao(): ProjectTaskDao
}
