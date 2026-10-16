package com.kairon.android.core.sync

import com.kairon.android.client.api.SyncControllerApi
import com.kairon.android.client.model.ChangeSetJournalEntryView
import com.kairon.android.client.model.ChangeSetProjectTaskView
import com.kairon.android.client.model.ChangeSetProjectView
import com.kairon.android.client.model.ChangeSetTodoItemView
import com.kairon.android.client.model.JournalEntryView
import com.kairon.android.client.model.ProjectTaskView
import com.kairon.android.client.model.ProjectView
import com.kairon.android.client.model.SyncResponse
import com.kairon.android.client.model.TodoItemView
import com.kairon.android.core.data.JournalDao
import com.kairon.android.core.data.ProjectDao
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.TodoDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private const val TYPES = "todo,journal,project,projectTask"

class SyncRepositoryTest {

    private val syncApi = mockk<SyncControllerApi>()
    private val cursorStore = mockk<SyncCursorStore>(relaxed = true)
    private val todoDao = mockk<TodoDao>(relaxed = true)
    private val journalDao = mockk<JournalDao>(relaxed = true)
    private val projectDao = mockk<ProjectDao>(relaxed = true)
    private val projectTaskDao = mockk<ProjectTaskDao>(relaxed = true)
    private lateinit var repository: SyncRepository

    @Before
    fun setUp() {
        repository = SyncRepository(syncApi, cursorStore, todoDao, journalDao, projectDao, projectTaskDao)
        coEvery { cursorStore.read() } returns null
        coEvery { cursorStore.readSyncedTypes() } returns emptySet()
    }

    @Test
    fun upsertsAndTombstonesFromACannedResponseLandInTheFakeDao() = runTest {
        val upsertedId = UUID.randomUUID()
        val deletedId = UUID.randomUUID()
        val since = OffsetDateTime.now(ZoneOffset.UTC)
        val upsertedItem = TodoItemView(
            id = upsertedId, day = LocalDate.now(), title = "A todo", status = "OPEN",
            priority = 0, position = 100, createdAt = since, updatedAt = since, version = 0,
        )
        coEvery { syncApi.sync(null, TYPES) } returns Response.success(
            SyncResponse(since = since, todos = ChangeSetTodoItemView(
                upserted = listOf(upsertedItem), deletedIds = listOf(deletedId), truncated = false,
            )),
        )

        val result = repository.sync()

        assertTrue(result.isSuccess)
        coVerify { todoDao.upsertAll(listOf(upsertedItem.toEntity())) }
        coVerify { todoDao.deleteByIds(listOf(deletedId)) }
    }

    @Test
    fun journalProjectAndProjectTaskChangeSetsLandInTheirOwnDaos() = runTest {
        val since = OffsetDateTime.now(ZoneOffset.UTC)
        val journalId = UUID.randomUUID()
        val journalDeletedId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val projectDeletedId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val taskDeletedId = UUID.randomUUID()

        val journalEntry = JournalEntryView(
            id = journalId, day = LocalDate.now(), position = 0, title = "Day one", content = "Hello",
            mood = 3, createdAt = since, updatedAt = since, version = 0,
        )
        val project = ProjectView(
            id = projectId, categoryId = null, name = "Kitchen remodel", description = null, status = "ACTIVE",
            propertySize = "M", priorityRank = 0, categoryRank = 0, color = "#FF0000",
            startDate = null, endDate = null, actualStart = null, actualEnd = null,
            createdAt = since, updatedAt = since, version = 0,
        )
        val task = ProjectTaskView(
            id = taskId, projectId = projectId, projectName = "Kitchen remodel", projectColor = "#FF0000",
            parentTaskId = null, name = "Pick tiles", description = null, status = "TODO", isMilestone = false,
            plannedStart = null, plannedEnd = null, estimateHours = BigDecimal.ONE, actualHours = null,
            progressPercent = 0, position = 0, createdAt = since, updatedAt = since, version = 0,
        )

        coEvery { syncApi.sync(null, TYPES) } returns Response.success(
            SyncResponse(
                since = since,
                todos = ChangeSetTodoItemView(upserted = emptyList(), deletedIds = emptyList(), truncated = false),
                journalEntries = ChangeSetJournalEntryView(
                    upserted = listOf(journalEntry), deletedIds = listOf(journalDeletedId), truncated = false,
                ),
                projects = ChangeSetProjectView(
                    upserted = listOf(project), deletedIds = listOf(projectDeletedId), truncated = false,
                ),
                projectTasks = ChangeSetProjectTaskView(
                    upserted = listOf(task), deletedIds = listOf(taskDeletedId), truncated = false,
                ),
            ),
        )

        val result = repository.sync()

        assertTrue(result.isSuccess)
        coVerify { journalDao.upsertAll(listOf(journalEntry.toEntity())) }
        coVerify { journalDao.deleteByIds(listOf(journalDeletedId)) }
        coVerify { projectDao.upsertAll(listOf(project.toEntity())) }
        coVerify { projectDao.deleteByIds(listOf(projectDeletedId)) }
        coVerify { projectTaskDao.upsertAll(listOf(task.toEntity())) }
        coVerify { projectTaskDao.deleteByIds(listOf(taskDeletedId)) }
    }

    @Test
    fun theReturnedSinceIsPersistedForTheNextCall() = runTest {
        val since = OffsetDateTime.now(ZoneOffset.UTC)
        coEvery { syncApi.sync(null, TYPES) } returns Response.success(
            SyncResponse(since = since, todos = ChangeSetTodoItemView(upserted = emptyList(), deletedIds = emptyList(), truncated = false)),
        )

        repository.sync()

        coVerify { cursorStore.write(since.toInstant()) }
    }

    @Test
    fun aFailedCallIsSurfacedAsFailureWithoutTouchingTheCursor() = runTest {
        coEvery { syncApi.sync(null, TYPES) } returns Response.error(500, "boom".toResponseBody(null))

        val result = repository.sync()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { cursorStore.write(any<Instant>()) }
    }

    @Test
    fun aStoredCursorIsUsedOnceEveryRequestedTypeHasAlreadyBeenSynced() = runTest {
        val storedSince = Instant.now().minusSeconds(60)
        val since = OffsetDateTime.now(ZoneOffset.UTC)
        coEvery { cursorStore.read() } returns storedSince
        coEvery { cursorStore.readSyncedTypes() } returns setOf("todo", "journal", "project", "projectTask")
        coEvery { syncApi.sync(OffsetDateTime.ofInstant(storedSince, ZoneOffset.UTC), TYPES) } returns Response.success(
            SyncResponse(since = since, todos = ChangeSetTodoItemView(upserted = emptyList(), deletedIds = emptyList(), truncated = false)),
        )

        val result = repository.sync()

        assertTrue(result.isSuccess)
        coVerify { syncApi.sync(OffsetDateTime.ofInstant(storedSince, ZoneOffset.UTC), TYPES) }
    }

    /**
     * The bug M11.5 shipped and then had to patch: a device that already had
     * `todo`-only syncs running (M11) keeps its already-advanced cursor
     * across an update that widens the requested types — without this
     * override, any pre-existing journal/project/task row older than that
     * cursor would never sync down, since `changedSince` only returns rows
     * touched *after* it.
     */
    @Test
    fun aStoredCursorIsIgnoredWhenARequestedTypeHasNeverBeenSyncedBefore() = runTest {
        val staleSince = Instant.now().minusSeconds(3600)
        val since = OffsetDateTime.now(ZoneOffset.UTC)
        coEvery { cursorStore.read() } returns staleSince
        coEvery { cursorStore.readSyncedTypes() } returns setOf("todo") // pre-M11.5 state
        coEvery { syncApi.sync(null, TYPES) } returns Response.success(
            SyncResponse(since = since, todos = ChangeSetTodoItemView(upserted = emptyList(), deletedIds = emptyList(), truncated = false)),
        )

        val result = repository.sync()

        assertTrue(result.isSuccess)
        coVerify { syncApi.sync(null, TYPES) }
    }

    /**
     * M11.8: the bug this milestone fixes — a `truncated` page used to still
     * advance the cursor to the response's own `since` ("now"), silently
     * skipping every row past the first `max-rows-per-type` page. This
     * reproduces the roadmap's own repro: more rows than fit in one page,
     * synced from `since=null`, asserting every row is eventually fetched
     * rather than the sync stopping after the first truncated page.
     */
    @Test
    fun aTruncatedPageIsFollowedByARetryUntilEveryTypeIsNonTruncated() = runTest {
        val firstSince = Instant.now().minusSeconds(600)
        val secondOffset = OffsetDateTime.now(ZoneOffset.UTC)
        val item1 = TodoItemView(
            id = UUID.randomUUID(), day = LocalDate.now(), title = "Old 1", status = "OPEN",
            priority = 0, position = 0,
            createdAt = OffsetDateTime.ofInstant(firstSince, ZoneOffset.UTC),
            updatedAt = OffsetDateTime.ofInstant(firstSince, ZoneOffset.UTC), version = 0,
        )
        val item2UpdatedAt = firstSince.plusSeconds(1)
        val item2 = TodoItemView(
            id = UUID.randomUUID(), day = LocalDate.now(), title = "Old 2", status = "OPEN",
            priority = 0, position = 1,
            createdAt = OffsetDateTime.ofInstant(item2UpdatedAt, ZoneOffset.UTC),
            updatedAt = OffsetDateTime.ofInstant(item2UpdatedAt, ZoneOffset.UTC), version = 0,
        )
        val item3 = TodoItemView(
            id = UUID.randomUUID(), day = LocalDate.now(), title = "Newer", status = "OPEN",
            priority = 0, position = 2, createdAt = secondOffset, updatedAt = secondOffset, version = 0,
        )

        coEvery { syncApi.sync(null, TYPES) } returns Response.success(
            SyncResponse(
                since = OffsetDateTime.ofInstant(firstSince, ZoneOffset.UTC),
                todos = ChangeSetTodoItemView(upserted = listOf(item1, item2), deletedIds = emptyList(), truncated = true),
            ),
        )
        coEvery { syncApi.sync(OffsetDateTime.ofInstant(item2UpdatedAt, ZoneOffset.UTC), TYPES) } returns Response.success(
            SyncResponse(
                since = secondOffset,
                todos = ChangeSetTodoItemView(upserted = listOf(item3), deletedIds = emptyList(), truncated = false),
            ),
        )

        val result = repository.sync()

        assertTrue(result.isSuccess)
        coVerify { todoDao.upsertAll(listOf(item1.toEntity(), item2.toEntity())) }
        coVerify { todoDao.upsertAll(listOf(item3.toEntity())) }
        coVerify(exactly = 1) { cursorStore.write(secondOffset.toInstant()) }
        coVerify(exactly = 0) { cursorStore.write(firstSince) }
    }

    /**
     * A page that's truncated but contains only tombstones carries no
     * `updatedAt` the client can advance past (`ChangeSet` doesn't echo one
     * for a deleted id) — the known gap a real per-type keyset cursor would
     * close (still open on the roadmap). The fix must fail loudly here
     * rather than reissue the identical request forever.
     */
    @Test
    fun aTruncatedPageOfOnlyTombstonesFailsInsteadOfLoopingForever() = runTest {
        val deletedId = UUID.randomUUID()
        coEvery { syncApi.sync(null, TYPES) } returns Response.success(
            SyncResponse(
                since = OffsetDateTime.now(ZoneOffset.UTC),
                todos = ChangeSetTodoItemView(upserted = emptyList(), deletedIds = listOf(deletedId), truncated = true),
            ),
        )

        val result = repository.sync()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { cursorStore.write(any<Instant>()) }
    }

    @Test
    fun aSuccessfulSyncRecordsEveryRequestedTypeAsSynced() = runTest {
        val since = OffsetDateTime.now(ZoneOffset.UTC)
        coEvery { syncApi.sync(null, TYPES) } returns Response.success(
            SyncResponse(since = since, todos = ChangeSetTodoItemView(upserted = emptyList(), deletedIds = emptyList(), truncated = false)),
        )

        repository.sync()

        coVerify { cursorStore.writeSyncedTypes(setOf("todo", "journal", "project", "projectTask")) }
    }
}
