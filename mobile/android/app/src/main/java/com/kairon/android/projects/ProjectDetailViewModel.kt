package com.kairon.android.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.ProjectTaskControllerApi
import com.kairon.android.client.api.TaskDependencyControllerApi
import com.kairon.android.client.model.PatchProjectTaskRequest
import com.kairon.android.client.model.ReorderTasksRequest
import com.kairon.android.client.model.TaskDependencyResponse
import com.kairon.android.core.data.ProjectDao
import com.kairon.android.core.data.ProjectEntity
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.ProjectTaskEntity
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.AppError
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.network.requireSuccessful
import com.kairon.android.core.network.toAppError
import com.kairon.android.core.network.userMessage
import com.kairon.android.core.sync.SyncRepository
import com.kairon.android.core.sync.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ProjectDetailViewModel"

/** How long a swiped-away task stays pending before [ProjectDetailViewModel.deleteTask] actually calls the backend. */
private const val DELETE_UNDO_WINDOW_MILLIS = 5000L

data class PendingTaskDeletion(val taskId: UUID, val taskName: String)

val TASK_STATUSES = listOf("TODO", "IN_PROGRESS", "BLOCKED", "DONE")

data class ProjectDetailUiState(
    val project: ProjectEntity? = null,
    val tasks: List<ProjectTaskEntity> = emptyList(),
    val dependencies: List<TaskDependencyResponse> = emptyList(),
    val syncing: Boolean = true,
    /** A background `/sync` failure (D6) — shown as a banner above the cached tree/board/Gantt, never blocking them. */
    val syncError: AppError? = null,
    val error: String? = null,
)

/**
 * Backs [ProjectDetailScreen]'s Tree/Board/Gantt tabs — project + tasks read
 * from Room as the single source of truth (D9), dependencies fetched live
 * on every visit (M11 D9 / M11.5's own confirmation — no soft delete means
 * no sync feed for them).
 */
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskApi: ProjectTaskControllerApi,
    private val dependencyApi: TaskDependencyControllerApi,
    private val projectDao: ProjectDao,
    private val projectTaskDao: ProjectTaskDao,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val projectId: UUID = UUID.fromString(requireNotNull(savedStateHandle.get<String>("projectId")))

    private val dependencies = MutableStateFlow<List<TaskDependencyResponse>>(emptyList())
    private val syncing = MutableStateFlow(true)
    private val syncError = MutableStateFlow<AppError?>(null)
    private val error = MutableStateFlow<String?>(null)

    /** Tasks swiped away but not yet actually deleted (D-undo) — hidden from [state] while pending. */
    private val pendingDeleteIds = MutableStateFlow<Set<UUID>>(emptySet())
    private val pendingDeleteJobs = mutableMapOf<UUID, Job>()
    private val _pendingDeletions = MutableSharedFlow<PendingTaskDeletion>(extraBufferCapacity = 1)
    val pendingDeletions: SharedFlow<PendingTaskDeletion> = _pendingDeletions

    val state: StateFlow<ProjectDetailUiState> = combine(
        combine(
            projectDao.observeById(projectId),
            projectTaskDao.observeForProject(projectId),
            pendingDeleteIds,
        ) { project, tasks, pendingIds -> Triple(project, tasks.filterNot { it.id in pendingIds }, pendingIds) },
        dependencies,
        syncing,
        syncError,
        error,
    ) { (project, tasks, _), deps, isSyncing, syncErr, err ->
        ProjectDetailUiState(project, tasks, deps, isSyncing, syncErr, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectDetailUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            syncing.value = true
            syncRepository.sync()
                .onSuccess { syncError.value = null }
                .onFailure { ex ->
                    AppLog.w(TAG, "refresh.syncFailed", throwable = ex)
                    syncError.value = ex.toAppError()
                }
            loadDependencies()
            syncing.value = false
        }
    }

    fun dismissSyncError() {
        syncError.value = null
    }

    private suspend fun loadDependencies() {
        runCatching {
            dependencyApi.list5(projectId).bodyOrThrow(TAG, "listDependencies")
        }.onSuccess { deps ->
            dependencies.value = deps
            error.value = null
        }.onFailure { ex ->
            AppLog.e(TAG, "listDependencies.failed", throwable = ex)
            error.value = ex.toAppError().userMessage()
        }
    }

    /**
     * Tap-based reorder (D4) — swaps [task] with its sibling neighbor (same
     * `parentTaskId`) in the given [direction], then submits the *whole*
     * sibling group's new order, matching the backend's own all-or-nothing
     * `:reorder` contract.
     */
    fun moveTask(task: ProjectTaskEntity, direction: Int) {
        val siblings = state.value.tasks
            .filter { it.parentTaskId == task.parentTaskId }
            .sortedBy { it.position }
        val index = siblings.indexOfFirst { it.id == task.id }
        val targetIndex = index + direction
        if (index == -1 || targetIndex < 0 || targetIndex >= siblings.size) return
        val reordered = siblings.toMutableList().apply {
            add(targetIndex, removeAt(index))
        }
        viewModelScope.launch {
            runCatching {
                taskApi.reorder2(projectId, ReorderTasksRequest(reordered.map { it.id }, task.parentTaskId))
                    .bodyOrThrow(TAG, "reorder")
            }.onSuccess { updated ->
                projectTaskDao.upsertAll(updated.map { it.toEntity(task.projectName, task.projectColor) })
                AppLog.i(TAG, "reorder.success", mapOf("count" to updated.size))
            }.onFailure { ex ->
                AppLog.e(TAG, "reorder.failed", throwable = ex)
                error.value = ex.toAppError().userMessage()
            }
        }
    }

    /** Status-chip tap (D4) — a whole-form PATCH, same shape web's own drag/patch flow sends. */
    fun setStatus(task: ProjectTaskEntity, status: String) {
        viewModelScope.launch {
            runCatching {
                taskApi.patch1(
                    task.id,
                    PatchProjectTaskRequest(
                        name = task.name,
                        status = status,
                        progressPercent = task.progressPercent,
                        isMilestone = task.isMilestone,
                        description = task.description,
                        parentTaskId = task.parentTaskId,
                        plannedStart = task.plannedStart,
                        plannedEnd = task.plannedEnd,
                        estimateHours = task.estimateHours,
                        actualHours = task.actualHours,
                        expectedVersion = task.version,
                    ),
                ).bodyOrThrow(TAG, "setStatus")
            }.onSuccess { updated ->
                projectTaskDao.upsert(updated.toEntity(task.projectName, task.projectColor))
                AppLog.i(TAG, "setStatus.success", mapOf("id" to task.id, "status" to status))
            }.onFailure { ex ->
                AppLog.e(TAG, "setStatus.failed", mapOf("id" to task.id), throwable = ex)
                error.value = ex.toAppError().userMessage()
            }
        }
    }

    /**
     * Hides [task] immediately (via [pendingDeleteIds]) and emits a
     * [PendingTaskDeletion] for the screen to show an "Undo" snackbar, but
     * only actually calls the backend after [DELETE_UNDO_WINDOW_MILLIS] —
     * [undoDeleteTask] cancels that job and un-hides the task instead.
     */
    fun deleteTask(task: ProjectTaskEntity) {
        pendingDeleteIds.value = pendingDeleteIds.value + task.id
        _pendingDeletions.tryEmit(PendingTaskDeletion(task.id, task.name))
        pendingDeleteJobs[task.id] = viewModelScope.launch {
            delay(DELETE_UNDO_WINDOW_MILLIS)
            pendingDeleteJobs.remove(task.id)
            runCatching {
                taskApi.delete1(task.id).requireSuccessful(TAG, "deleteTask")
            }.onSuccess {
                projectTaskDao.deleteById(task.id)
                pendingDeleteIds.value = pendingDeleteIds.value - task.id
                AppLog.i(TAG, "deleteTask.success", mapOf("id" to task.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "deleteTask.failed", mapOf("id" to task.id), throwable = ex)
                pendingDeleteIds.value = pendingDeleteIds.value - task.id
                error.value = ex.toAppError().userMessage()
            }
        }
    }

    fun undoDeleteTask(taskId: UUID) {
        pendingDeleteJobs.remove(taskId)?.cancel()
        pendingDeleteIds.value = pendingDeleteIds.value - taskId
        AppLog.i(TAG, "deleteTask.undone", mapOf("id" to taskId))
    }
}
