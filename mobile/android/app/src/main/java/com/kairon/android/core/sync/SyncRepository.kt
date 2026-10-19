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
 * A safety net against a genuine non-convergence (a server-side bug, or the
 * no-progress case [sync] itself detects and fails fast on) — never expected
 * to be hit in practice, since each round makes forward progress or fails.
 */
private const val MAX_ROUNDS = 50

/**
 * Owns the `/sync` call and fans upserts/tombstones out to each feature's
 * Room DAO (M11 §6.3). M11.5 widens the requested set to every type the
 * backend already supports (D7) — `project_category`/`task_dependency` stay
 * out, per M11 D9/M11.5's own confirmation of it. M11.9 adds a second
 * [SyncScope] ([SyncScope.NOTIFICATIONS]) with its own narrower type set and
 * its own independent cursor (see [SyncCursorStore]'s own doc comment) —
 * every call site below that processes `body.*` stays scope-agnostic, since
 * a type outside the caller's requested set simply comes back as an empty
 * `ChangeSet` (confirmed server-side: `SyncService.sync` returns
 * `ChangeSet.empty()`, never a null field, for an unrequested type) rather
 * than needing its own branch here.
 *
 * M11.8: `GET /sync` caps each type at `kairon.sync.max-rows-per-type` rows
 * (oldest-`updatedAt`-first) and sets that type's `truncated` flag instead of
 * returning the rest. A single call can therefore leave rows behind — this
 * loops, re-requesting with `since` advanced to the oldest-newest `updatedAt`
 * it has actually seen (not the response's own `since`, which is "now" and
 * would skip the untouched remainder), until a round comes back with every
 * type non-truncated. [cursorStore] is only written once that happens, so a
 * sync that's interrupted mid-catch-up (process death, a failed round) simply
 * resumes the catch-up from the same starting cursor next time rather than
 * silently losing the tail of a type's backlog.
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

    suspend fun sync(scope: SyncScope = SyncScope.FULL): Result<Unit> = runCatching {
        val requestedTypes = scope.requestedTypes
        val storedSince = cursorStore.read(scope)
        val syncedTypes = cursorStore.readSyncedTypes(scope)
        val neverSyncedTypes = requestedTypes.filterNot { it in syncedTypes }
        // A single shared cursor only makes sense for a fixed type set. The
        // first time this device requests a type it's never synced before
        // (freshly widened by an app update, or a genuinely fresh install),
        // treat the stored cursor as absent for this one call — otherwise
        // any pre-existing row for that type older than the cursor would be
        // silently excluded forever (see SyncCursorStore's own doc comment).
        var since = if (neverSyncedTypes.isEmpty()) storedSince else null
        var upsertedCount = 0
        var deletedCount = 0
        var round = 0

        while (true) {
            round++
            check(round <= MAX_ROUNDS) { "sync did not converge after $MAX_ROUNDS rounds (since=$since)" }
            AppLog.d(TAG, "sync.start", mapOf("since" to since, "round" to round, "neverSyncedTypes" to neverSyncedTypes))
            val response = syncApi.sync(
                since?.let { OffsetDateTime.ofInstant(it, java.time.ZoneOffset.UTC) },
                requestedTypes.joinToString(","),
            )
            val body = response.bodyOrThrow(TAG, "sync")

            var anyTruncated = false
            var maxSeenUpdatedAt = since

            body.todos?.let { changes ->
                changes.upserted?.let { upserted ->
                    upsertedCount += upserted.size
                    todoDao.upsertAll(upserted.map { it.toEntity() })
                    upserted.forEach { maxSeenUpdatedAt = newest(maxSeenUpdatedAt, it.updatedAt?.toInstant()) }
                }
                changes.deletedIds?.let { deletedIds ->
                    deletedCount += deletedIds.size
                    if (deletedIds.isNotEmpty()) {
                        todoDao.deleteByIds(deletedIds)
                    }
                }
                anyTruncated = anyTruncated || changes.truncated == true
            }
            body.journalEntries?.let { changes ->
                changes.upserted?.let { upserted ->
                    upsertedCount += upserted.size
                    journalDao.upsertAll(upserted.map { it.toEntity() })
                    upserted.forEach { maxSeenUpdatedAt = newest(maxSeenUpdatedAt, it.updatedAt?.toInstant()) }
                }
                changes.deletedIds?.let { deletedIds ->
                    deletedCount += deletedIds.size
                    if (deletedIds.isNotEmpty()) {
                        journalDao.deleteByIds(deletedIds)
                    }
                }
                anyTruncated = anyTruncated || changes.truncated == true
            }
            body.projects?.let { changes ->
                changes.upserted?.let { upserted ->
                    upsertedCount += upserted.size
                    projectDao.upsertAll(upserted.map { it.toEntity() })
                    upserted.forEach { maxSeenUpdatedAt = newest(maxSeenUpdatedAt, it.updatedAt?.toInstant()) }
                }
                changes.deletedIds?.let { deletedIds ->
                    deletedCount += deletedIds.size
                    if (deletedIds.isNotEmpty()) {
                        projectDao.deleteByIds(deletedIds)
                    }
                }
                anyTruncated = anyTruncated || changes.truncated == true
            }
            body.projectTasks?.let { changes ->
                changes.upserted?.let { upserted ->
                    upsertedCount += upserted.size
                    projectTaskDao.upsertAll(upserted.map { it.toEntity() })
                    upserted.forEach { maxSeenUpdatedAt = newest(maxSeenUpdatedAt, it.updatedAt?.toInstant()) }
                }
                changes.deletedIds?.let { deletedIds ->
                    deletedCount += deletedIds.size
                    if (deletedIds.isNotEmpty()) {
                        projectTaskDao.deleteByIds(deletedIds)
                    }
                }
                anyTruncated = anyTruncated || changes.truncated == true
            }

            if (!anyTruncated) {
                body.since?.toInstant()?.let { cursorStore.write(it, scope) }
                cursorStore.writeSyncedTypes(requestedTypes.toSet(), scope)
                AppLog.i(
                    TAG, "sync.success",
                    mapOf("upserted" to upsertedCount, "deleted" to deletedCount, "rounds" to round),
                )
                break
            }

            // A truncated page with no upserted row at all (every row in it a
            // tombstone) carries no updatedAt we can advance past — the
            // backend's ChangeSet doesn't echo one for a deleted id. Rather
            // than reissue the identical request forever, fail loudly; this
            // is the gap the real-keyset-cursor follow-up would close.
            check(maxSeenUpdatedAt != since) {
                "sync truncated with no forward progress available (since=$since)"
            }
            AppLog.w(TAG, "sync.truncated", mapOf("since" to since, "nextSince" to maxSeenUpdatedAt, "round" to round))
            since = maxSeenUpdatedAt
        }
    }.onFailure { ex -> AppLog.e(TAG, "sync.failed", throwable = ex) }

    private fun newest(a: java.time.Instant?, b: java.time.Instant?): java.time.Instant? = when {
        a == null -> b
        b == null -> a
        b.isAfter(a) -> b
        else -> a
    }
}
