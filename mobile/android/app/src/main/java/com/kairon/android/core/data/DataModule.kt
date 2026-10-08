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
        Room.databaseBuilder(context, KaironDatabase::class.java, "kairon.db").build()

    @Provides
    @Singleton
    fun todoDao(database: KaironDatabase): TodoDao = database.todoDao()
}
