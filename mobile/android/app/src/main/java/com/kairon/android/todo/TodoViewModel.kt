package com.kairon.android.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.TodoControllerApi
import com.kairon.android.client.model.CompleteRequest
import com.kairon.android.client.model.CreateTodoRequest
import com.kairon.android.client.model.PatchTodoRequest
import com.kairon.android.client.model.ReorderRequest
import com.kairon.android.client.model.RolloverRequest
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.data.TodoItemEntity
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.network.requireSuccessful
import com.kairon.android.core.sync.SyncRepository
import com.kairon.android.core.sync.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

private const val TAG = "TodoViewModel"

data class DayViewUiState(
    val day: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val items: List<TodoItemEntity> = emptyList(),
    val syncing: Boolean = true,
    val error: String? = null,
    val rolloverCandidateCount: Int? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoApi: TodoControllerApi,
    private val todoDao: TodoDao,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val day = MutableStateFlow(LocalDate.now())
    private val syncing = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)
    private val rolloverCandidateCount = MutableStateFlow<Int?>(null)

    val state: StateFlow<DayViewUiState> = combine(
        day,
        day.flatMapLatest { todoDao.observeForDay(it) },
        syncing,
        error,
        rolloverCandidateCount,
    ) { d, items, isSyncing, err, rolloverCount ->
        DayViewUiState(d, LocalDate.now(), items, isSyncing, err, rolloverCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayViewUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            syncing.value = true
            syncRepository.sync()
                .onSuccess { error.value = null }
                .onFailure { ex ->
                    // SyncRepository already logged the detailed failure; just note the UI-visible effect.
                    AppLog.w(TAG, "refresh.syncFailed")
                    error.value = ex.message ?: "Sync failed"
                }
            syncing.value = false
            checkRolloverCandidates()
        }
    }

    fun goToDate(date: LocalDate) {
        day.value = date
    }

    fun goToPreviousDay() {
        goToDate(day.value.minusDays(1))
    }

    fun goToNextDay() {
        goToDate(day.value.plusDays(1))
    }

    fun goToToday() {
        goToDate(LocalDate.now())
    }

    fun quickAdd(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val response = todoApi.create(CreateTodoRequest(day = day.value, title = title.trim()))
                response.bodyOrThrow(TAG, "quickAdd")
            }.onSuccess { created ->
                todoDao.upsert(created.toEntity())
                AppLog.i(TAG, "quickAdd.success", mapOf("id" to created.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "quickAdd.failed", throwable = ex)
                error.value = ex.message ?: "Failed to add todo"
            }
        }
    }

    fun toggleComplete(item: TodoItemEntity) {
        viewModelScope.launch {
            runCatching {
                val response = todoApi.complete(item.id, CompleteRequest(complete = item.status != "DONE"))
                response.bodyOrThrow(TAG, "toggleComplete")
            }.onSuccess { updated ->
                todoDao.upsert(updated.toEntity())
                AppLog.i(TAG, "toggleComplete.success", mapOf("id" to item.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "toggleComplete.failed", mapOf("id" to item.id), throwable = ex)
                error.value = ex.message ?: "Failed to update todo"
            }
        }
    }

    fun delete(item: TodoItemEntity) {
        viewModelScope.launch {
            runCatching {
                todoApi.delete(item.id).requireSuccessful(TAG, "delete")
            }.onSuccess {
                todoDao.deleteById(item.id)
                AppLog.i(TAG, "delete.success", mapOf("id" to item.id))
            }.onFailure { ex ->
                AppLog.e(TAG, "delete.failed", mapOf("id" to item.id), throwable = ex)
                error.value = ex.message ?: "Failed to delete todo"
            }
        }
    }

    /**
     * The backend's `:reorder` endpoint requires every non-deleted id for the
     * day in the submitted order (`TodoService.reorder` 400s otherwise), so a
     * reorder confined to the open section has to splice its new order back
     * into the done/cancelled items' original slots — same approach the web
     * app's `DayView.reorderSelected`/`TodoList.handleDragEnd` already use.
     */
    private fun spliceOpenOrder(fullDayItems: List<TodoItemEntity>, newOpenOrder: List<UUID>): List<UUID> {
        var i = 0
        return fullDayItems.map { if (it.status == "OPEN") newOpenOrder[i++] else it.id }
    }

    fun reorderOpen(fullDayItems: List<TodoItemEntity>, newOpenOrder: List<UUID>) {
        viewModelScope.launch {
            val orderedIds = spliceOpenOrder(fullDayItems, newOpenOrder)
            runCatching {
                val response = todoApi.reorder(ReorderRequest(day = day.value, orderedIds = orderedIds))
                response.bodyOrThrow(TAG, "reorder")
            }.onSuccess { updated ->
                todoDao.upsertAll(updated.map { it.toEntity() })
                AppLog.i(TAG, "reorder.success", mapOf("count" to updated.size))
            }.onFailure { ex ->
                AppLog.e(TAG, "reorder.failed", throwable = ex)
                error.value = ex.message ?: "Failed to reorder"
            }
        }
    }

    private suspend fun applyPatch(item: TodoItemEntity, request: PatchTodoRequest, action: String) {
        runCatching {
            val response = todoApi.patch(item.id, request)
            response.bodyOrThrow(TAG, action)
        }.onSuccess { updated ->
            todoDao.upsert(updated.toEntity())
            AppLog.i(TAG, "$action.success", mapOf("id" to item.id))
        }.onFailure { ex ->
            AppLog.e(TAG, "$action.failed", mapOf("id" to item.id), throwable = ex)
            error.value = ex.message ?: "Failed to update todo"
        }
    }

    fun rename(item: TodoItemEntity, title: String) {
        viewModelScope.launch { applyPatch(item, PatchTodoRequest(title = title), "rename") }
    }

    fun setNotes(item: TodoItemEntity, notes: String) {
        viewModelScope.launch { applyPatch(item, PatchTodoRequest(notes = notes), "setNotes") }
    }

    fun cancel(item: TodoItemEntity) {
        viewModelScope.launch { applyPatch(item, PatchTodoRequest(status = "CANCELLED"), "cancel") }
    }

    private fun checkRolloverCandidates() {
        viewModelScope.launch {
            runCatching {
                val response = todoApi.rolloverPreview(onDay = day.value)
                response.bodyOrThrow(TAG, "rolloverPreview").totalItems
            }.onSuccess { count ->
                rolloverCandidateCount.value = count?.takeIf { it > 0 }
            }.onFailure { ex ->
                AppLog.w(TAG, "rolloverPreview.failed", throwable = ex)
            }
        }
    }

    fun rollover() {
        viewModelScope.launch {
            runCatching {
                val response = todoApi.rollover(RolloverRequest(toDay = day.value))
                response.bodyOrThrow(TAG, "rollover")
            }.onSuccess { result ->
                result.rolledOver?.let { todoDao.upsertAll(it.map { item -> item.toEntity() }) }
                rolloverCandidateCount.value = null
                AppLog.i(TAG, "rollover.success", mapOf("count" to (result.rolledOver?.size ?: 0)))
            }.onFailure { ex ->
                AppLog.e(TAG, "rollover.failed", throwable = ex)
                error.value = ex.message ?: "Rollover failed"
            }
        }
    }
}
