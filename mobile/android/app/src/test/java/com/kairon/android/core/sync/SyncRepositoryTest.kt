package com.kairon.android.core.sync

import com.kairon.android.client.api.SyncControllerApi
import com.kairon.android.client.model.ChangeSetTodoItemView
import com.kairon.android.client.model.SyncResponse
import com.kairon.android.client.model.TodoItemView
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SyncRepositoryTest {

    private val syncApi = mockk<SyncControllerApi>()
    private val cursorStore = mockk<SyncCursorStore>(relaxed = true)
    private val todoDao = mockk<TodoDao>(relaxed = true)
    private lateinit var repository: SyncRepository

    @Before
    fun setUp() {
        repository = SyncRepository(syncApi, cursorStore, todoDao)
        coEvery { cursorStore.read() } returns null
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
        coEvery { syncApi.sync(null, "todo") } returns Response.success(
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
    fun theReturnedSinceIsPersistedForTheNextCall() = runTest {
        val since = OffsetDateTime.now(ZoneOffset.UTC)
        coEvery { syncApi.sync(null, "todo") } returns Response.success(
            SyncResponse(since = since, todos = ChangeSetTodoItemView(upserted = emptyList(), deletedIds = emptyList(), truncated = false)),
        )

        repository.sync()

        coVerify { cursorStore.write(since.toInstant()) }
    }

    @Test
    fun aFailedCallIsSurfacedAsFailureWithoutTouchingTheCursor() = runTest {
        coEvery { syncApi.sync(null, "todo") } returns Response.error(500, "boom".toResponseBody(null))

        val result = repository.sync()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { cursorStore.write(any<Instant>()) }
    }
}
