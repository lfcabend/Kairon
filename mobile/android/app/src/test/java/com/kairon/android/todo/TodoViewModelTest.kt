package com.kairon.android.todo

import com.kairon.android.client.api.TodoControllerApi
import com.kairon.android.client.model.CompleteRequest
import com.kairon.android.client.model.TodoItemResponse
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.data.TodoItemEntity
import com.kairon.android.core.network.AppError
import com.kairon.android.core.sync.SyncRepository
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TodoViewModelTest {

    private val todoApi = mockk<TodoControllerApi>()
    private val todoDao = mockk<TodoDao>(relaxed = true)
    private val syncRepository = mockk<SyncRepository>()

    private val itemId = UUID.randomUUID()

    private fun entity() = TodoItemEntity(
        id = itemId,
        day = LocalDate.now(),
        title = "Buy milk",
        notes = null,
        status = "OPEN",
        priority = 0,
        position = 0,
        estimateMinutes = null,
        sourceProjectTaskId = null,
        rolledOverFromId = null,
        completedAt = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        version = 1L,
    )

    private fun notFoundResponse(): Response<TodoItemResponse> =
        Response.error(404, """{"status":404,"detail":"Todo item not found."}""".toResponseBody("application/json".toMediaType()))

    private fun conflictResponse(): Response<TodoItemResponse> =
        Response.error(
            409,
            """{"status":409,"detail":"This item was modified by another request. Reload and try again."}"""
                .toResponseBody("application/json".toMediaType()),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { todoDao.observeForDay(any()) } returns flowOf(emptyList())
        coEvery { syncRepository.sync() } returns Result.success(Unit)
        coEvery { todoApi.rolloverPreview(any()) } returns Response.success(
            com.kairon.android.client.model.RolloverPreviewResponse(totalItems = 0),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aToggleCompleteThatFourOhFoursPurgesTheRowFromRoom() = runTest {
        coEvery { todoApi.complete(itemId, any()) } returns notFoundResponse()
        val viewModel = TodoViewModel(todoApi, todoDao, syncRepository)

        viewModel.state.test {
            skipItems(1) // initial default state
            viewModel.toggleComplete(entity())

            val afterFailure = awaitItem()
            assertTrue(afterFailure.error is AppError.NotFound)
            coVerify(exactly = 1) { todoDao.deleteById(itemId) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aToggleCompleteThatFourOhNinesLeavesTheRowAloneAndReloadIsJustRefresh() = runTest {
        coEvery { todoApi.complete(itemId, any()) } returns conflictResponse()
        val viewModel = TodoViewModel(todoApi, todoDao, syncRepository)

        viewModel.state.test {
            skipItems(1) // initial default state
            viewModel.toggleComplete(entity())

            val afterFailure = awaitItem()
            assertTrue(afterFailure.error is AppError.Conflict)
            coVerify(exactly = 0) { todoDao.deleteById(itemId) }
            cancelAndIgnoreRemainingEvents()
        }

        // D10 — Conflict's "Reload" action is wired to this same refresh() — already called
        // once by init{}, so a working Reload is this test's second call. Re-subscribing (rather
        // than reading the next item off the first subscription) sidesteps any intermediate
        // syncing=true emission landing before the final, settled value.
        viewModel.refresh()
        coVerify(exactly = 2) { syncRepository.sync() }

        viewModel.state.test {
            val latest = awaitItem()
            assertNull(latest.syncError)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
