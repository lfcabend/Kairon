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
        val since = cursorStore.read()
        AppLog.d(TAG, "sync.start", mapOf("since" to since))
        val response = syncApi.sync(
            since?.let { OffsetDateTime.ofInstant(it, java.time.ZoneOffset.UTC) },
            "todo,journal,project,projectTask",
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
        AppLog.i(TAG, "sync.success", mapOf("upserted" to upsertedCount, "deleted" to deletedCount))
    }.onFailure { ex -> AppLog.e(TAG, "sync.failed", throwable = ex) }
}
