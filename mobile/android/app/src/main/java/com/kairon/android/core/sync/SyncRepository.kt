package com.kairon.android.core.sync

import com.kairon.android.client.api.SyncControllerApi
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncRepository"

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
        AppLog.d(TAG, "sync.start", mapOf("since" to since))
        val response = syncApi.sync(since?.let { OffsetDateTime.ofInstant(it, java.time.ZoneOffset.UTC) }, "todo")
        val body = response.bodyOrThrow(TAG, "sync")
        var upsertedCount = 0
        var deletedCount = 0
        body.todos?.let { changes ->
            changes.upserted?.let { upserted ->
                upsertedCount = upserted.size
                todoDao.upsertAll(upserted.map { it.toEntity() })
            }
            changes.deletedIds?.let { deletedIds ->
                deletedCount = deletedIds.size
                if (deletedIds.isNotEmpty()) {
                    todoDao.deleteByIds(deletedIds)
                }
            }
        }
        body.since?.toInstant()?.let { cursorStore.write(it) }
        AppLog.i(TAG, "sync.success", mapOf("upserted" to upsertedCount, "deleted" to deletedCount))
    }.onFailure { ex -> AppLog.e(TAG, "sync.failed", throwable = ex) }
}
