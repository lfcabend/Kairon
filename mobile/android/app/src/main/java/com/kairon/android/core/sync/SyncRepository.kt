package com.kairon.android.core.sync

import com.kairon.android.client.api.SyncControllerApi
import com.kairon.android.core.data.TodoDao
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the `/sync` call and fans upserts/tombstones out to each feature's
 * Room DAO (M11 §6.3) — this slice only requests `types=todo` (D3's "ask for
 * what you currently consume"); M11.5/M11.6 widen the set with no backend
 * change needed.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val syncApi: SyncControllerApi,
    private val cursorStore: SyncCursorStore,
    private val todoDao: TodoDao,
) {

    suspend fun sync(): Result<Unit> = runCatching {
        val since = cursorStore.read()
        val response = syncApi.sync(since?.let { OffsetDateTime.ofInstant(it, java.time.ZoneOffset.UTC) }, "todo")
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            error("Sync failed: HTTP ${response.code()}")
        }
        body.todos?.let { changes ->
            changes.upserted?.let { upserted ->
                todoDao.upsertAll(upserted.map { it.toEntity() })
            }
            changes.deletedIds?.let { deletedIds ->
                if (deletedIds.isNotEmpty()) {
                    todoDao.deleteByIds(deletedIds)
                }
            }
        }
        body.since?.toInstant()?.let { cursorStore.write(it) }
    }
}
