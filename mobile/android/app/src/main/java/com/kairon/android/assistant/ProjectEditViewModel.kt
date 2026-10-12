package com.kairon.android.assistant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.MeControllerApi
import com.kairon.android.client.api.ProjectEditControllerApi
import com.kairon.android.client.api.SuggestedProjectEditControllerApi
import com.kairon.android.client.api.TaskDependencyControllerApi
import com.kairon.android.client.model.AcceptProjectEditRequest
import com.kairon.android.client.model.AssistantSuggestedProjectEditView
import com.kairon.android.client.model.ProjectEditRequest
import com.kairon.android.client.model.TaskDependencyResponse
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.ProjectTaskEntity
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ProjectEditViewModel"

data class ProjectEditUiState(
    val enabled: Boolean? = null,
    val tasks: List<ProjectTaskEntity> = emptyList(),
    val dependencies: List<TaskDependencyResponse> = emptyList(),
    val generating: Boolean = false,
    val diff: AssistantSuggestedProjectEditView? = null,
    val excludedKeys: Set<String> = emptySet(),
    val applying: Boolean = false,
    val error: String? = null,
    val applied: Boolean = false,
)

/**
 * Backs [ProjectEditEntryPoint] (D11 — checks its own `GET /me` flag). Reads
 * the project's current tasks from Room (D10/Q4) and fetches dependencies
 * live (same precedent as [com.kairon.android.projects.DependencySection] —
 * `task_dependency` is never Room-cached) so the diff review can show real
 * names instead of bare ids, mirroring web's `EditProjectDialog`.
 */
@HiltViewModel
class ProjectEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meApi: MeControllerApi,
    private val projectEditApi: ProjectEditControllerApi,
    private val suggestedProjectEditApi: SuggestedProjectEditControllerApi,
    private val dependencyApi: TaskDependencyControllerApi,
    private val projectTaskDao: ProjectTaskDao,
) : ViewModel() {

    val projectId: UUID = UUID.fromString(requireNotNull(savedStateHandle.get<String>("projectId")))

    private val _state = MutableStateFlow(ProjectEditUiState())
    val state: StateFlow<ProjectEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { meApi.me().bodyOrThrow(TAG, "checkEnabled") }
                .onSuccess { me -> _state.value = _state.value.copy(enabled = parseAssistantPreferences(me.preferences).projectEditing.enabled) }
                .onFailure { ex ->
                    AppLog.w(TAG, "checkEnabled.failed", throwable = ex)
                    _state.value = _state.value.copy(enabled = false)
                }
        }
    }

    /** Loads the project's current tasks/dependencies — called when the entry point's dialog opens. */
    fun loadContext() {
        viewModelScope.launch {
            val tasks = runCatching { projectTaskDao.listForProject(projectId) }.getOrDefault(emptyList())
            val dependencies = runCatching { dependencyApi.list5(projectId).bodyOrThrow(TAG, "loadDependencies") }
                .onFailure { ex -> AppLog.w(TAG, "loadDependencies.failed", throwable = ex) }
                .getOrDefault(emptyList())
            _state.value = _state.value.copy(tasks = tasks, dependencies = dependencies)
        }
    }

    fun generate(description: String) {
        _state.value = _state.value.copy(generating = true, error = null)
        viewModelScope.launch {
            runCatching {
                projectEditApi.generate1(ProjectEditRequest(projectId, description)).bodyOrThrow(TAG, "generate")
            }.onSuccess { run ->
                AppLog.i(TAG, "generate.success", mapOf("taskOps" to run.suggestedProjectEdit?.taskOperations.orEmpty().size))
                _state.value = _state.value.copy(generating = false, diff = run.suggestedProjectEdit)
            }.onFailure { ex ->
                AppLog.e(TAG, "generate.failed", throwable = ex)
                _state.value = _state.value.copy(generating = false, error = ex.message ?: "Could not generate a diff")
            }
        }
    }

    fun toggleExcluded(key: String) {
        val current = _state.value.excludedKeys
        _state.value = _state.value.copy(excludedKeys = if (key in current) current - key else current + key)
    }

    fun apply() {
        val diff = _state.value.diff ?: return
        val id = diff.id ?: return
        _state.value = _state.value.copy(applying = true, error = null)
        viewModelScope.launch {
            runCatching {
                suggestedProjectEditApi.accept2(id, AcceptProjectEditRequest(_state.value.excludedKeys.toList())).bodyOrThrow(TAG, "apply")
            }.onSuccess {
                AppLog.i(TAG, "apply.success", mapOf("projectId" to projectId))
                _state.value = _state.value.copy(applying = false, applied = true)
            }.onFailure { ex ->
                AppLog.e(TAG, "apply.failed", throwable = ex)
                _state.value = _state.value.copy(applying = false, error = ex.message ?: "Could not apply the changes")
            }
        }
    }

    fun cancelReview() {
        val id = _state.value.diff?.id
        if (id != null) {
            viewModelScope.launch {
                runCatching { suggestedProjectEditApi.dismiss2(id) }
                    .onFailure { ex -> AppLog.w(TAG, "dismiss.failed", throwable = ex) }
            }
        }
        reset()
    }

    fun reset() {
        _state.value = _state.value.copy(generating = false, diff = null, excludedKeys = emptySet(), applying = false, error = null, applied = false)
    }
}
