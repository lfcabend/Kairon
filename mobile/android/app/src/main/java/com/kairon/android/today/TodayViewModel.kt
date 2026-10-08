package com.kairon.android.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.PlanningControllerApi
import com.kairon.android.client.model.ProjectTaskView
import com.kairon.android.client.model.PromoteRequest
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.sync.toEntity
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
    val refreshing: Boolean = false,
    val dueProjectTasks: List<ProjectTaskView> = emptyList(),
    val hasJournalEntry: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val planningApi: PlanningControllerApi,
    private val todoDao: TodoDao,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * [isPullToRefresh] keeps the current content on screen (just shows the
     * pull indicator) instead of blanking it behind the full-screen spinner
     * the initial load uses.
     */
    fun refresh(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = _state.value.loading && !isPullToRefresh,
                refreshing = isPullToRefresh,
                error = null,
            )
            runCatching {
                val response = planningApi.today(LocalDate.now())
                response.bodyOrThrow(TAG, "refresh")
            }.onSuccess { body ->
                AppLog.i(
                    TAG, "refresh.success",
                    mapOf("dueProjectTasks" to body.dueProjectTasks.orEmpty().size),
                )
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    dueProjectTasks = body.dueProjectTasks.orEmpty(),
                    hasJournalEntry = body.journalPrompt?.hasEntry ?: false,
                )
            }.onFailure { ex ->
                AppLog.e(TAG, "refresh.failed", throwable = ex)
                _state.value = _state.value.copy(loading = false, refreshing = false, error = ex.message ?: "Failed to load Today")
            }
        }
    }

    /**
     * Upserts the created row into Room (rather than tracking it in
     * [TodayUiState]) so it shows up immediately in the [TodoViewModel]-backed
     * todo list [TodayScreen] embeds alongside this one — same shared source
     * of truth, so "already added" is derived from that list's own
     * `sourceProjectTaskId`s rather than a separate, session-only set here.
     */
    fun promote(taskId: UUID) {
        viewModelScope.launch {
            runCatching {
                val response = planningApi.promote(PromoteRequest(projectTaskId = taskId, day = LocalDate.now()))
                response.bodyOrThrow(TAG, "promote")
            }.onSuccess { created ->
                todoDao.upsert(created.toEntity())
                AppLog.i(TAG, "promote.success", mapOf("taskId" to taskId, "todoId" to created.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "promote.failed", mapOf("taskId" to taskId), throwable = ex)
                _state.value = _state.value.copy(error = ex.message ?: "Failed to add to today")
            }
        }
    }
}
