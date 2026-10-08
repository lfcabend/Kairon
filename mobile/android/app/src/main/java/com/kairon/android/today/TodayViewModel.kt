package com.kairon.android.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.PlanningControllerApi
import com.kairon.android.client.model.ProjectTaskView
import com.kairon.android.client.model.PromoteRequest
import com.kairon.android.client.model.TodoItemView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

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
                val body = response.body()
                if (!response.isSuccessful || body == null) error("HTTP ${response.code()}")
                body
            }.onSuccess { body ->
                _state.value = _state.value.copy(
                    loading = false,
                    todos = body.todos.orEmpty(),
                    dueProjectTasks = body.dueProjectTasks.orEmpty(),
                    hasJournalEntry = body.journalPrompt?.hasEntry ?: false,
                )
            }.onFailure { ex ->
                _state.value = _state.value.copy(loading = false, error = ex.message ?: "Failed to load Today")
            }
        }
    }

    fun promote(taskId: UUID) {
        viewModelScope.launch {
            runCatching {
                val response = planningApi.promote(PromoteRequest(projectTaskId = taskId, day = LocalDate.now()))
                val body = response.body()
                if (!response.isSuccessful || body == null) error("HTTP ${response.code()}")
                body
            }.onSuccess { created ->
                _state.value = _state.value.copy(
                    todos = _state.value.todos + created,
                    addedTaskIds = _state.value.addedTaskIds + taskId,
                )
            }.onFailure { ex ->
                _state.value = _state.value.copy(error = ex.message ?: "Failed to add to today")
            }
        }
    }
}
