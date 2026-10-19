package com.kairon.android.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    /**
     * A plain [PreferenceDataStoreFactory]-built store rather than the
     * `by preferencesDataStore(...)` delegate [com.kairon.android.core.sync.SyncCursorStore]
     * uses — that delegate is a `Context` extension property and can't be
     * swapped out in [NotificationPreferences]' own constructor for a test.
     * Providing the `DataStore<Preferences>` itself through Hilt here keeps
     * production wiring just as simple while letting the test inject its
     * own store over a temp file instead.
     */
    @Provides
    @Singleton
    fun notificationPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("notification_prefs") })
}
