package com.kairon.android.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.MeControllerApi
import com.kairon.android.client.api.ProjectPlanControllerApi
import com.kairon.android.client.api.SuggestedProjectControllerApi
import com.kairon.android.client.model.AcceptProjectPlanRequest
import com.kairon.android.client.model.AssistantSuggestedProjectView
import com.kairon.android.client.model.ProjectPlanRequest
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

private const val TAG = "ProjectPlanViewModel"

data class ProjectPlanUiState(
    val enabled: Boolean? = null,
    val generating: Boolean = false,
    val plan: AssistantSuggestedProjectView? = null,
    val excludedKeys: Set<String> = emptySet(),
    val accepting: Boolean = false,
    val error: String? = null,
    val createdProjectId: UUID? = null,
)

/**
 * Backs [ProjectGenerationEntryPoint] (D11 — checks its own `GET /me` flag):
 * `POST /assistant/project-plan` is synchronous (D8); accept applies the
 * cascade-exclusion from [effectiveExcludedTaskKeys] (D9).
 */
@HiltViewModel
class ProjectPlanViewModel @Inject constructor(
    private val meApi: MeControllerApi,
    private val projectPlanApi: ProjectPlanControllerApi,
    private val suggestedProjectApi: SuggestedProjectControllerApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectPlanUiState())
    val state: StateFlow<ProjectPlanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { meApi.me().bodyOrThrow(TAG, "checkEnabled") }
                .onSuccess { me -> _state.value = _state.value.copy(enabled = parseAssistantPreferences(me.preferences).projectGeneration.enabled) }
                .onFailure { ex ->
                    AppLog.w(TAG, "checkEnabled.failed", throwable = ex)
                    _state.value = _state.value.copy(enabled = false)
                }
        }
    }

    fun generate(description: String, startDate: LocalDate, targetDeadline: LocalDate?) {
        _state.value = _state.value.copy(generating = true, error = null)
        viewModelScope.launch {
            runCatching {
                projectPlanApi.generate(ProjectPlanRequest(description, startDate, targetDeadline)).bodyOrThrow(TAG, "generate")
            }.onSuccess { run ->
                AppLog.i(TAG, "generate.success", mapOf("tasks" to run.suggestedProject?.tasks.orEmpty().size))
                _state.value = _state.value.copy(generating = false, plan = run.suggestedProject)
            }.onFailure { ex ->
                AppLog.e(TAG, "generate.failed", throwable = ex)
                _state.value = _state.value.copy(generating = false, error = ex.message ?: "Could not generate a plan")
            }
        }
    }

    fun toggleExcluded(key: String) {
        val current = _state.value.excludedKeys
        _state.value = _state.value.copy(excludedKeys = if (key in current) current - key else current + key)
    }

    fun accept() {
        val plan = _state.value.plan ?: return
        val id = plan.id ?: return
        _state.value = _state.value.copy(accepting = true, error = null)
        viewModelScope.launch {
            runCatching {
                suggestedProjectApi.accept1(id, AcceptProjectPlanRequest(_state.value.excludedKeys.toList())).bodyOrThrow(TAG, "accept")
            }.onSuccess { created ->
                AppLog.i(TAG, "accept.success", mapOf("projectId" to created.id))
                _state.value = _state.value.copy(accepting = false, createdProjectId = created.id)
            }.onFailure { ex ->
                AppLog.e(TAG, "accept.failed", throwable = ex)
                _state.value = _state.value.copy(accepting = false, error = ex.message ?: "Could not create the project")
            }
        }
    }

    fun cancelReview() {
        val id = _state.value.plan?.id
        if (id != null) {
            viewModelScope.launch {
                runCatching { suggestedProjectApi.dismiss1(id) }
                    .onFailure { ex -> AppLog.w(TAG, "dismiss.failed", throwable = ex) }
            }
        }
        reset()
    }

    fun reset() {
        _state.value = _state.value.copy(
            generating = false, plan = null, excludedKeys = emptySet(), accepting = false, error = null, createdProjectId = null,
        )
    }
}
