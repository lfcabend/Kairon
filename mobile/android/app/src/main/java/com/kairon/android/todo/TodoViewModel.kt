package com.kairon.android.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.TodoControllerApi
import com.kairon.android.client.model.CompleteRequest
import com.kairon.android.client.model.CreateTodoRequest
import com.kairon.android.client.model.ReorderRequest
import com.kairon.android.client.model.RolloverRequest
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.data.TodoItemEntity
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
import javax.inject.Inject

data class DayViewUiState(
    val day: LocalDate = LocalDate.now(),
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
        DayViewUiState(d, items, isSyncing, err, rolloverCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayViewUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            syncing.value = true
            syncRepository.sync()
                .onSuccess { error.value = null }
                .onFailure { error.value = it.message ?: "Sync failed" }
            syncing.value = false
            checkRolloverCandidates()
        }
    }

    fun goToPreviousDay() {
        day.value = day.value.minusDays(1)
    }

    fun goToNextDay() {
        day.value = day.value.plusDays(1)
    }

    fun quickAdd(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val response = todoApi.create(CreateTodoRequest(day = day.value, title = title.trim()))
                val body = response.body()
                if (!response.isSuccessful || body == null) error("HTTP ${response.code()}")
                body
            }.onSuccess { created ->
                todoDao.upsert(created.toEntity())
            }.onFailure { ex ->
                error.value = ex.message ?: "Failed to add todo"
            }
        }
    }

    fun toggleComplete(item: TodoItemEntity) {
        viewModelScope.launch {
            runCatching {
                val response = todoApi.complete(item.id, CompleteRequest(complete = item.status != "DONE"))
                val body = response.body()
                if (!response.isSuccessful || body == null) error("HTTP ${response.code()}")
                body
            }.onSuccess { updated ->
                todoDao.upsert(updated.toEntity())
            }.onFailure { ex ->
                error.value = ex.message ?: "Failed to update todo"
            }
        }
    }

    fun delete(item: TodoItemEntity) {
        viewModelScope.launch {
            runCatching {
                val response = todoApi.delete(item.id)
                if (!response.isSuccessful) error("HTTP ${response.code()}")
            }.onSuccess {
                todoDao.deleteById(item.id)
            }.onFailure { ex ->
                error.value = ex.message ?: "Failed to delete todo"
            }
        }
    }

    fun move(item: TodoItemEntity, delta: Int) {
        viewModelScope.launch {
            val current = todoDao.listForDay(day.value)
            val index = current.indexOfFirst { it.id == item.id }
            val target = index + delta
            if (index < 0 || target < 0 || target >= current.size) return@launch
            val reordered = current.toMutableList()
            val moved = reordered.removeAt(index)
            reordered.add(target, moved)
            val orderedIds = reordered.map { it.id }
            runCatching {
                val response = todoApi.reorder(ReorderRequest(day = day.value, orderedIds = orderedIds))
                val body = response.body()
                if (!response.isSuccessful || body == null) error("HTTP ${response.code()}")
                body
            }.onSuccess { updated ->
                todoDao.upsertAll(updated.map { it.toEntity() })
            }.onFailure { ex ->
                error.value = ex.message ?: "Failed to reorder"
            }
        }
    }

    private fun checkRolloverCandidates() {
        viewModelScope.launch {
            runCatching {
                val response = todoApi.rolloverPreview(onDay = day.value)
                response.body()?.totalItems
            }.onSuccess { count ->
                rolloverCandidateCount.value = count?.takeIf { it > 0 }
            }
        }
    }

    fun rollover() {
        viewModelScope.launch {
            runCatching {
                val response = todoApi.rollover(RolloverRequest(toDay = day.value))
                val body = response.body()
                if (!response.isSuccessful || body == null) error("HTTP ${response.code()}")
                body
            }.onSuccess { result ->
                result.rolledOver?.let { todoDao.upsertAll(it.map { item -> item.toEntity() }) }
                rolloverCandidateCount.value = null
            }.onFailure { ex ->
                error.value = ex.message ?: "Rollover failed"
            }
        }
    }
}
