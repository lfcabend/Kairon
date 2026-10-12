package com.kairon.android.core.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): KaironDatabase =
        Room.databaseBuilder(context, KaironDatabase::class.java, "kairon.db")
            // No real user data exists outside dev/test devices yet (M11.5 D8)
            // — a destructive wipe-and-recreate is simpler than a real
            // migration path this app has no installed base to justify.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun todoDao(database: KaironDatabase): TodoDao = database.todoDao()

    @Provides
    @Singleton
    fun journalDao(database: KaironDatabase): JournalDao = database.journalDao()

    @Provides
    @Singleton
    fun projectDao(database: KaironDatabase): ProjectDao = database.projectDao()

    @Provides
    @Singleton
    fun projectTaskDao(database: KaironDatabase): ProjectTaskDao = database.projectTaskDao()
}
