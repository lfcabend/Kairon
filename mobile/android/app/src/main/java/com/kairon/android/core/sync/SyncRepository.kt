package com.kairon.android.core.sync

import com.kairon.android.client.api.SyncControllerApi
import com.kairon.android.core.data.JournalDao
import com.kairon.android.core.data.ProjectDao
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncRepository"

/** Every type this app version requests — the one place to extend when a later milestone widens it. */
private val REQUESTED_TYPES = listOf("todo", "journal", "project", "projectTask")

/**
 * Owns the `/sync` call and fans upserts/tombstones out to each feature's
 * Room DAO (M11 §6.3). M11.5 widens the requested set to every type the
 * backend already supports (D7) — `project_category`/`task_dependency` stay
 * out, per M11 D9/M11.5's own confirmation of it.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val syncApi: SyncControllerApi,
    private val cursorStore: SyncCursorStore,
    private val todoDao: TodoDao,
    private val journalDao: JournalDao,
    private val projectDao: ProjectDao,
    private val projectTaskDao: ProjectTaskDao,
) {

    suspend fun sync(): Result<Unit> = runCatching {
        val storedSince = cursorStore.read()
        val syncedTypes = cursorStore.readSyncedTypes()
        val neverSyncedTypes = REQUESTED_TYPES.filterNot { it in syncedTypes }
        // A single shared cursor only makes sense for a fixed type set. The
        // first time this device requests a type it's never synced before
        // (freshly widened by an app update, or a genuinely fresh install),
        // treat the stored cursor as absent for this one call — otherwise
        // any pre-existing row for that type older than the cursor would be
        // silently excluded forever (see SyncCursorStore's own doc comment).
        val effectiveSince = if (neverSyncedTypes.isEmpty()) storedSince else null
        AppLog.d(TAG, "sync.start", mapOf("since" to effectiveSince, "neverSyncedTypes" to neverSyncedTypes))
        val response = syncApi.sync(
            effectiveSince?.let { OffsetDateTime.ofInstant(it, java.time.ZoneOffset.UTC) },
            REQUESTED_TYPES.joinToString(","),
        )
        val body = response.bodyOrThrow(TAG, "sync")
        var upsertedCount = 0
        var deletedCount = 0

        body.todos?.let { changes ->
            changes.upserted?.let { upserted ->
                upsertedCount += upserted.size
                todoDao.upsertAll(upserted.map { it.toEntity() })
            }
            changes.deletedIds?.let { deletedIds ->
                deletedCount += deletedIds.size
                if (deletedIds.isNotEmpty()) {
                    todoDao.deleteByIds(deletedIds)
                }
            }
        }
        body.journalEntries?.let { changes ->
            changes.upserted?.let { upserted ->
                upsertedCount += upserted.size
                journalDao.upsertAll(upserted.map { it.toEntity() })
            }
            changes.deletedIds?.let { deletedIds ->
                deletedCount += deletedIds.size
                if (deletedIds.isNotEmpty()) {
                    journalDao.deleteByIds(deletedIds)
                }
            }
        }
        body.projects?.let { changes ->
            changes.upserted?.let { upserted ->
                upsertedCount += upserted.size
                projectDao.upsertAll(upserted.map { it.toEntity() })
            }
            changes.deletedIds?.let { deletedIds ->
                deletedCount += deletedIds.size
                if (deletedIds.isNotEmpty()) {
                    projectDao.deleteByIds(deletedIds)
                }
            }
        }
        body.projectTasks?.let { changes ->
            changes.upserted?.let { upserted ->
                upsertedCount += upserted.size
                projectTaskDao.upsertAll(upserted.map { it.toEntity() })
            }
            changes.deletedIds?.let { deletedIds ->
                deletedCount += deletedIds.size
                if (deletedIds.isNotEmpty()) {
                    projectTaskDao.deleteByIds(deletedIds)
                }
            }
        }

        body.since?.toInstant()?.let { cursorStore.write(it) }
        cursorStore.writeSyncedTypes(REQUESTED_TYPES.toSet())
        AppLog.i(TAG, "sync.success", mapOf("upserted" to upsertedCount, "deleted" to deletedCount))
    }.onFailure { ex -> AppLog.e(TAG, "sync.failed", throwable = ex) }
}
