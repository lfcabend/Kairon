package com.kairon.android.notifications

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.ProjectTaskEntity
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.data.TodoItemEntity
import com.kairon.android.core.sync.SyncRepository
import com.kairon.android.core.sync.SyncScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Instrumented (not plain-JUnit) because [TestListenableWorkerBuilder] needs
 * a real [Context] ([ApplicationProvider]) — this app has no Robolectric.
 * Covers the worker's own orchestration (§6); the two conditions it
 * evaluates have their own dependency-free coverage in
 * `NotificationConditionsTest`.
 */
class NotificationCheckWorkerTest {

    private val syncRepository = mockk<SyncRepository>()
    private val todoDao = mockk<TodoDao>()
    private val projectTaskDao = mockk<ProjectTaskDao>()
    private val prefsFile = File.createTempFile("notification_prefs_test", ".preferences_pb")
    private val preferences = NotificationPreferences(PreferenceDataStoreFactory.create(produceFile = { prefsFile }))

    @After
    fun tearDown() {
        prefsFile.delete()
    }

    private fun buildWorker(): NotificationCheckWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<NotificationCheckWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = NotificationCheckWorker(appContext, workerParameters, syncRepository, todoDao, projectTaskDao, preferences)
            })
            .build()
    }

    private fun todoItem(status: String) = TodoItemEntity(
        id = UUID.randomUUID(), day = LocalDate.now(), title = "A todo", notes = null, status = status,
        priority = 0, position = 0, estimateMinutes = null, sourceProjectTaskId = null, rolledOverFromId = null,
        completedAt = null, createdAt = Instant.now(), updatedAt = Instant.now(), version = 0,
    )

    private fun overdueTask(id: UUID) = ProjectTaskEntity(
        id = id, projectId = UUID.randomUUID(), projectName = "A project", projectColor = "#FF0000",
        parentTaskId = null, name = "An overdue task", description = null, status = "TODO", isMilestone = false,
        plannedStart = null, plannedEnd = LocalDate.now().minusDays(1), estimateHours = BigDecimal.ONE, actualHours = null,
        progressPercent = 0, position = 0, createdAt = Instant.now(), updatedAt = Instant.now(), version = 0,
    )

    @Test
    fun bothTogglesOffShortCircuitsBeforeSync() = runBlocking {
        val worker = buildWorker()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { syncRepository.sync(any()) }
    }

    @Test
    fun aDailyConditionDueFiresAndUpdatesLastReminderDate() = runBlocking {
        preferences.setDailyReminderEnabled(true)
        preferences.setDailyReminderTimeMinutes(0) // always already past "now"
        coEvery { syncRepository.sync(SyncScope.NOTIFICATIONS) } returns Result.success(Unit)
        coEvery { todoDao.listForDay(any()) } returns listOf(todoItem(status = "OPEN"))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify { syncRepository.sync(SyncScope.NOTIFICATIONS) }
        assertEquals(LocalDate.now(), preferences.lastDailyReminderDate())
    }

    @Test
    fun allTodosDoneNeverUpdatesLastReminderDate() = runBlocking {
        preferences.setDailyReminderEnabled(true)
        preferences.setDailyReminderTimeMinutes(0)
        coEvery { syncRepository.sync(SyncScope.NOTIFICATIONS) } returns Result.success(Unit)
        coEvery { todoDao.listForDay(any()) } returns listOf(todoItem(status = "DONE"))

        buildWorker().doWork()

        assertEquals(null, preferences.lastDailyReminderDate())
    }

    @Test
    fun newlyOverdueTasksUpdateTheNotifiedIdSet() = runBlocking {
        preferences.setOverdueNotificationsEnabled(true)
        val taskId = UUID.randomUUID()
        coEvery { syncRepository.sync(SyncScope.NOTIFICATIONS) } returns Result.success(Unit)
        coEvery { projectTaskDao.findOverdue(any()) } returns listOf(overdueTask(taskId))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(setOf(taskId.toString()), preferences.notifiedOverdueTaskIds())
    }

    @Test
    fun aTaskNoLongerOverdueIsPrunedFromTheNotifiedIdSet() = runBlocking {
        preferences.setOverdueNotificationsEnabled(true)
        preferences.setNotifiedOverdueTaskIds(setOf("stale-task-id"))
        coEvery { syncRepository.sync(SyncScope.NOTIFICATIONS) } returns Result.success(Unit)
        coEvery { projectTaskDao.findOverdue(any()) } returns emptyList()

        buildWorker().doWork()

        assertEquals(emptySet<String>(), preferences.notifiedOverdueTaskIds())
    }

    @Test
    fun aFailedSyncStillProceedsToEvaluateAgainstExistingRoomData() = runBlocking {
        preferences.setDailyReminderEnabled(true)
        preferences.setDailyReminderTimeMinutes(0)
        coEvery { syncRepository.sync(SyncScope.NOTIFICATIONS) } returns Result.failure(RuntimeException("boom"))
        coEvery { todoDao.listForDay(any()) } returns listOf(todoItem(status = "OPEN"))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(LocalDate.now(), preferences.lastDailyReminderDate())
    }
}
