package com.kairon.android.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.ProjectTaskControllerApi
import com.kairon.android.client.api.TaskDependencyControllerApi
import com.kairon.android.client.model.CreateProjectTaskRequest
import com.kairon.android.client.model.CreateTaskDependencyRequest
import com.kairon.android.client.model.PatchProjectTaskRequest
import com.kairon.android.client.model.TaskDependencyResponse
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.ProjectTaskEntity
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.network.requireSuccessful
import com.kairon.android.core.sync.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ProjectTaskViewModel"

data class ProjectTaskFormUiState(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val name: String = "",
    val description: String = "",
    val status: String = "TODO",
    val parentTaskId: UUID? = null,
    val plannedStart: LocalDate? = null,
    val plannedEnd: LocalDate? = null,
    val estimateHours: String = "",
    val actualHours: String = "",
    val progressPercent: Int = 0,
    val isMilestone: Boolean = false,
    val siblingTasks: List<ProjectTaskEntity> = emptyList(),
    val dependencies: List<TaskDependencyResponse> = emptyList(),
    val loadingDependencies: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

/**
 * Backs [TaskEditScreen] — create (no `taskId` nav arg) or whole-form edit
 * (D15) a single task, plus its "Depends on" section (D-out: dependencies
 * are always a live fetch, never Room-cached).
 */
@HiltViewModel
class ProjectTaskViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskApi: ProjectTaskControllerApi,
    private val dependencyApi: TaskDependencyControllerApi,
    private val projectTaskDao: ProjectTaskDao,
) : ViewModel() {

    val projectId: UUID = UUID.fromString(requireNotNull(savedStateHandle.get<String>("projectId")))
    private val taskId: UUID? = savedStateHandle.get<String>("taskId")
        ?.takeIf { it != "new" }
        ?.let(UUID::fromString)
    private val initialParentTaskId: UUID? = savedStateHandle.get<String>("parentTaskId")?.let(UUID::fromString)
    private var version: Long? = null
    private var projectName: String = ""
    private var projectColor: String = ""

    private val _state = MutableStateFlow(ProjectTaskFormUiState(isNew = taskId == null))
    val state: StateFlow<ProjectTaskFormUiState> = _state

    init {
        viewModelScope.launch {
            val siblings = projectTaskDao.listForProject(projectId)
            projectName = siblings.firstOrNull()?.projectName ?: ""
            projectColor = siblings.firstOrNull()?.projectColor ?: "#6366f1"
            val existing = taskId?.let { id -> siblings.firstOrNull { it.id == id } }
            if (existing != null) {
                version = existing.version
            }
            _state.value = _state.value.copy(
                siblingTasks = siblings.filter { it.id != taskId },
                loaded = true,
                name = existing?.name ?: _state.value.name,
                description = existing?.description ?: "",
                status = existing?.status ?: "TODO",
                parentTaskId = existing?.parentTaskId ?: initialParentTaskId,
                plannedStart = existing?.plannedStart,
                plannedEnd = existing?.plannedEnd,
                estimateHours = existing?.estimateHours?.toPlainString() ?: "",
                actualHours = existing?.actualHours?.toPlainString() ?: "",
                progressPercent = existing?.progressPercent ?: 0,
                isMilestone = existing?.isMilestone ?: false,
            )
            loadDependencies()
        }
    }

    private suspend fun loadDependencies() {
        val id = taskId
        if (id == null) {
            _state.value = _state.value.copy(loadingDependencies = false)
            return
        }
        runCatching {
            dependencyApi.list5(projectId).bodyOrThrow(TAG, "listDependencies")
        }.onSuccess { all ->
            _state.value = _state.value.copy(
                dependencies = all.filter { it.successorId == id },
                loadingDependencies = false,
            )
        }.onFailure { ex ->
            AppLog.e(TAG, "listDependencies.failed", throwable = ex)
            _state.value = _state.value.copy(error = ex.message ?: "Failed to load dependencies", loadingDependencies = false)
        }
    }

    fun setName(name: String) { _state.value = _state.value.copy(name = name) }
    fun setDescription(description: String) { _state.value = _state.value.copy(description = description) }
    fun setStatus(status: String) { _state.value = _state.value.copy(status = status) }
    fun setParentTaskId(parentTaskId: UUID?) { _state.value = _state.value.copy(parentTaskId = parentTaskId) }
    fun setPlannedStart(date: LocalDate?) { _state.value = _state.value.copy(plannedStart = date) }
    fun setPlannedEnd(date: LocalDate?) { _state.value = _state.value.copy(plannedEnd = date) }
    fun setEstimateHours(value: String) { _state.value = _state.value.copy(estimateHours = value) }
    fun setActualHours(value: String) { _state.value = _state.value.copy(actualHours = value) }
    fun setProgressPercent(value: Int) { _state.value = _state.value.copy(progressPercent = value) }
    fun setIsMilestone(value: Boolean) { _state.value = _state.value.copy(isMilestone = value) }

    fun save() {
        val current = _state.value
        if (current.name.isBlank()) {
            _state.value = current.copy(error = "Name is required")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(saving = true, error = null)
            val id = taskId
            runCatching {
                if (id == null) {
                    taskApi.create3(
                        projectId,
                        CreateProjectTaskRequest(
                            name = current.name.trim(),
                            description = current.description.trim().ifBlank { null },
                            parentTaskId = current.parentTaskId,
                            plannedStart = current.plannedStart,
                            plannedEnd = current.plannedEnd,
                            estimateHours = current.estimateHours.parseBigDecimalOrNull(),
                            isMilestone = current.isMilestone,
                        ),
                    ).bodyOrThrow(TAG, "create")
                } else {
                    taskApi.patch1(
                        id,
                        PatchProjectTaskRequest(
                            name = current.name.trim(),
                            status = current.status,
                            progressPercent = current.progressPercent,
                            isMilestone = current.isMilestone,
                            description = current.description.trim().ifBlank { null },
                            parentTaskId = current.parentTaskId,
                            plannedStart = current.plannedStart,
                            plannedEnd = current.plannedEnd,
                            estimateHours = current.estimateHours.parseBigDecimalOrNull(),
                            actualHours = current.actualHours.parseBigDecimalOrNull(),
                            expectedVersion = version,
                        ),
                    ).bodyOrThrow(TAG, "patch")
                }
            }.onSuccess { saved ->
                projectTaskDao.upsert(saved.toEntity(projectName, projectColor))
                AppLog.i(TAG, "save.success", mapOf("id" to saved.id))
                _state.value = _state.value.copy(saving = false, saved = true)
            }.onFailure { ex ->
                AppLog.e(TAG, "save.failed", mapOf("id" to id), throwable = ex)
                _state.value = _state.value.copy(saving = false, error = ex.message ?: "Failed to save task")
            }
        }
    }

    fun delete() {
        val id = taskId ?: return
        viewModelScope.launch {
            runCatching {
                taskApi.delete1(id).requireSuccessful(TAG, "delete")
            }.onSuccess {
                projectTaskDao.deleteById(id)
                AppLog.i(TAG, "delete.success", mapOf("id" to id))
                _state.value = _state.value.copy(saved = true)
            }.onFailure { ex ->
                AppLog.e(TAG, "delete.failed", mapOf("id" to id), throwable = ex)
                _state.value = _state.value.copy(error = ex.message ?: "Failed to delete task")
            }
        }
    }

    /** Surfaces the backend's own 400/409 body (self/cross-project/cycle/duplicate, M5 D3/D4) inline. */
    fun addDependency(predecessorId: UUID) {
        val id = taskId ?: return
        viewModelScope.launch {
            runCatching {
                dependencyApi.create1(id, CreateTaskDependencyRequest(predecessorId = predecessorId))
                    .bodyOrThrow(TAG, "addDependency")
            }.onSuccess {
                loadDependencies()
                AppLog.i(TAG, "addDependency.success", mapOf("taskId" to id, "predecessorId" to predecessorId))
            }.onFailure { ex ->
                AppLog.e(TAG, "addDependency.failed", mapOf("taskId" to id), throwable = ex)
                _state.value = _state.value.copy(error = ex.message ?: "Failed to add dependency")
            }
        }
    }

    fun removeDependency(dependency: TaskDependencyResponse) {
        viewModelScope.launch {
            runCatching {
                dependencyApi.delete5(dependency.id!!).requireSuccessful(TAG, "removeDependency")
            }.onSuccess {
                _state.value = _state.value.copy(dependencies = _state.value.dependencies.filterNot { it.id == dependency.id })
                AppLog.i(TAG, "removeDependency.success", mapOf("id" to dependency.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "removeDependency.failed", mapOf("id" to dependency.id), throwable = ex)
                _state.value = _state.value.copy(error = ex.message ?: "Failed to remove dependency")
            }
        }
    }
}

private fun String.parseBigDecimalOrNull(): BigDecimal? = trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
