package com.kairon.android.core.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "kairon_sync")

/**
 * The `/sync` cursor (M11 §6.3) — a plain `since` instant, not sensitive, so
 * a plain `DataStore` key rather than the secure store [com.kairon.android.core.auth.TokenStore] uses.
 */
@Singleton
class SyncCursorStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val key = stringPreferencesKey("todo_since")

    suspend fun read(): Instant? =
        context.syncDataStore.data.first()[key]?.let(Instant::parse)

    suspend fun write(since: Instant) {
        context.syncDataStore.edit { it[key] = since.toString() }
    }

    suspend fun clear() {
        context.syncDataStore.edit { it.remove(key) }
    }
}
