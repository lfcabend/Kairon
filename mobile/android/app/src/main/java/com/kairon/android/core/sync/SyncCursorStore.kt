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
 *
 * Also tracks which types have ever been fully synced on this device
 * ([readSyncedTypes]/[writeSyncedTypes]) — a single shared `since` cursor
 * only makes sense for a *fixed* set of requested types. M11.5 widened that
 * set (`todo` → `todo,journal,project,projectTask`) and discovered the hard
 * way that a device which already had `todo`-only syncs running would keep
 * its already-advanced cursor across the app update, silently excluding
 * every pre-existing journal/project/task row older than that cursor from
 * ever syncing down — `changedSince` only returns rows touched *after* it.
 * [SyncRepository] uses the two together to force one full resync
 * (`since` treated as null) whenever the requested set grows to cover a
 * type this device has never synced before.
 */
@Singleton
class SyncCursorStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val sinceKey = stringPreferencesKey("todo_since")
    private val syncedTypesKey = stringPreferencesKey("synced_types")

    suspend fun read(): Instant? =
        context.syncDataStore.data.first()[sinceKey]?.let(Instant::parse)

    suspend fun write(since: Instant) {
        context.syncDataStore.edit { it[sinceKey] = since.toString() }
    }

    suspend fun readSyncedTypes(): Set<String> =
        context.syncDataStore.data.first()[syncedTypesKey]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    suspend fun writeSyncedTypes(types: Set<String>) {
        context.syncDataStore.edit { it[syncedTypesKey] = types.joinToString(",") }
    }

    suspend fun clear() {
        context.syncDataStore.edit {
            it.remove(sinceKey)
            it.remove(syncedTypesKey)
        }
    }
}
