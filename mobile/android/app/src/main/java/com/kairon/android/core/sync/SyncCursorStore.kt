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
 *
 * M11.9 adds a second, independent cursor keyed by [SyncScope] for the same
 * reason: `NotificationCheckWorker`'s own narrower [SyncScope.NOTIFICATIONS]
 * sync must never advance the [SyncScope.FULL] cursor the UI's own sync
 * relies on — doing so would falsely claim "we have the complete state of
 * `journal`/`project` up to this point" for types that call never requested,
 * permanently hiding any change to those types that happened in the gap.
 * The two scopes' cursors advance fully independently, each redundantly
 * re-syncing `todo`/`projectTask` on its own schedule — harmless, since
 * upserts/tombstones are idempotent.
 */
@Singleton
class SyncCursorStore @Inject constructor(@ApplicationContext private val context: Context) {

    private fun sinceKey(scope: SyncScope) = stringPreferencesKey(
        when (scope) {
            SyncScope.FULL -> "todo_since"
            SyncScope.NOTIFICATIONS -> "notification_since"
        },
    )

    private fun syncedTypesKey(scope: SyncScope) = stringPreferencesKey(
        when (scope) {
            SyncScope.FULL -> "synced_types"
            SyncScope.NOTIFICATIONS -> "notification_synced_types"
        },
    )

    suspend fun read(scope: SyncScope = SyncScope.FULL): Instant? =
        context.syncDataStore.data.first()[sinceKey(scope)]?.let(Instant::parse)

    suspend fun write(since: Instant, scope: SyncScope = SyncScope.FULL) {
        context.syncDataStore.edit { it[sinceKey(scope)] = since.toString() }
    }

    suspend fun readSyncedTypes(scope: SyncScope = SyncScope.FULL): Set<String> =
        context.syncDataStore.data.first()[syncedTypesKey(scope)]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    suspend fun writeSyncedTypes(types: Set<String>, scope: SyncScope = SyncScope.FULL) {
        context.syncDataStore.edit { it[syncedTypesKey(scope)] = types.joinToString(",") }
    }

    suspend fun clear() {
        context.syncDataStore.edit { prefs ->
            SyncScope.entries.forEach { scope ->
                prefs.remove(sinceKey(scope))
                prefs.remove(syncedTypesKey(scope))
            }
        }
    }
}
