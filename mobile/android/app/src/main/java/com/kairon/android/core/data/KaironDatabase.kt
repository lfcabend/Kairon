package com.kairon.android.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [TodoItemEntity::class], version = 1, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class KaironDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}
