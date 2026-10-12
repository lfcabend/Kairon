package com.kairon.android.projects

import androidx.lifecycle.SavedStateHandle
import com.kairon.android.client.api.ProjectTaskControllerApi
import com.kairon.android.client.api.TaskDependencyControllerApi
import com.kairon.android.client.model.ProjectTaskResponse
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.ProjectTaskEntity
import com.kairon.android.core.sync.SyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectTaskViewModelTest {

    private val taskApi = mockk<ProjectTaskControllerApi>()
    private val dependencyApi = mockk<TaskDependencyControllerApi>()
    private val projectTaskDao = mockk<ProjectTaskDao>(relaxed = true)
    private val syncRepository = mockk<SyncRepository>()

    private val projectId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()

    private fun existingEntity(version: Long = 1L) = ProjectTaskEntity(
        id = taskId,
        projectId = projectId,
        projectName = "Kitchen remodel",
        projectColor = "#6366f1",
        parentTaskId = null,
        name = "Pick tiles",
        description = null,
        status = "TODO",
        isMilestone = false,
        plannedStart = null,
        plannedEnd = null,
        estimateHours = BigDecimal.ONE,
        actualHours = null,
        progressPercent = 0,
        position = 0,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        version = version,
    )

    private fun newViewModel(): ProjectTaskViewModel {
        val handle = SavedStateHandle(mapOf("projectId" to projectId.toString(), "taskId" to taskId.toString()))
        return ProjectTaskViewModel(handle, taskApi, dependencyApi, projectTaskDao, syncRepository)
    }

    private fun notFoundResponse(): Response<ProjectTaskResponse> =
        Response.error(404, """{"status":404,"detail":"Task not found."}""".toResponseBody("application/json".toMediaType()))

    private fun conflictResponse(): Response<ProjectTaskResponse> =
        Response.error(
            409,
            """{"status":409,"detail":"This task was modified by another request. Reload and try again."}"""
                .toResponseBody("application/json".toMediaType()),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { projectTaskDao.listForProject(projectId) } returns listOf(existingEntity())
        coEvery { dependencyApi.list5(projectId) } returns Response.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun aSaveThatFourOhFoursPurgesTheRowFromRoomAndMarksTheFormGone() = runTest {
        coEvery { taskApi.patch1(taskId, any()) } returns notFoundResponse()
        val viewModel = newViewModel()

        viewModel.save()

        coVerify(exactly = 1) { projectTaskDao.deleteById(taskId) }
        assertTrue(viewModel.state.value.gone)
        assertTrue(viewModel.state.value.error is com.kairon.android.core.network.AppError.NotFound)
    }

    @Test
    fun aSaveThatFourOhNinesLeavesTheFormUntouchedAndReloadRefetchesTheRow() = runTest {
        coEvery { taskApi.patch1(taskId, any()) } returns conflictResponse()
        val viewModel = newViewModel()
        val originalName = viewModel.state.value.name

        viewModel.save()

        assertFalse(viewModel.state.value.gone)
        assertTrue(viewModel.state.value.error is com.kairon.android.core.network.AppError.Conflict)
        assertEquals(originalName, viewModel.state.value.name)

        coEvery { syncRepository.sync() } returns Result.success(Unit)
        coEvery { projectTaskDao.listForProject(projectId) } returns listOf(existingEntity(version = 2L).copy(name = "Pick tiles (updated)"))

        viewModel.reload()

        coVerify(exactly = 1) { syncRepository.sync() }
        assertEquals("Pick tiles (updated)", viewModel.state.value.name)
        assertEquals(null, viewModel.state.value.error)
    }
}
