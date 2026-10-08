package com.kairon.android.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.PlanningControllerApi
import com.kairon.android.client.model.ProjectTaskView
import com.kairon.android.client.model.PromoteRequest
import com.kairon.android.client.model.TodoItemView
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

private const val TAG = "TodayViewModel"

data class TodayUiState(
    val loading: Boolean = true,
    val todos: List<TodoItemView> = emptyList(),
    val dueProjectTasks: List<ProjectTaskView> = emptyList(),
    val hasJournalEntry: Boolean = false,
    val addedTaskIds: Set<UUID> = emptySet(),
    val error: String? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val planningApi: PlanningControllerApi,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val response = planningApi.today(LocalDate.now())
                response.bodyOrThrow(TAG, "refresh")
            }.onSuccess { body ->
                AppLog.i(
                    TAG, "refresh.success",
                    mapOf("todos" to body.todos.orEmpty().size, "dueProjectTasks" to body.dueProjectTasks.orEmpty().size),
                )
                _state.value = _state.value.copy(
                    loading = false,
                    todos = body.todos.orEmpty(),
                    dueProjectTasks = body.dueProjectTasks.orEmpty(),
                    hasJournalEntry = body.journalPrompt?.hasEntry ?: false,
                )
            }.onFailure { ex ->
                AppLog.e(TAG, "refresh.failed", throwable = ex)
                _state.value = _state.value.copy(loading = false, error = ex.message ?: "Failed to load Today")
            }
        }
    }

    fun promote(taskId: UUID) {
        viewModelScope.launch {
            runCatching {
                val response = planningApi.promote(PromoteRequest(projectTaskId = taskId, day = LocalDate.now()))
                response.bodyOrThrow(TAG, "promote")
            }.onSuccess { created ->
                AppLog.i(TAG, "promote.success", mapOf("taskId" to taskId, "todoId" to created.id))
                _state.value = _state.value.copy(
                    todos = _state.value.todos + created,
                    addedTaskIds = _state.value.addedTaskIds + taskId,
                )
            }.onFailure { ex ->
                AppLog.e(TAG, "promote.failed", mapOf("taskId" to taskId), throwable = ex)
                _state.value = _state.value.copy(error = ex.message ?: "Failed to add to today")
            }
        }
    }
}
