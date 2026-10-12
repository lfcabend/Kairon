package com.kairon.android.journal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.JournalControllerApi
import com.kairon.android.client.model.CreateJournalEntryRequest
import com.kairon.android.client.model.PatchJournalEntryRequest
import com.kairon.android.core.data.JournalDao
import com.kairon.android.core.data.JournalEntryEntity
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.AppError
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.network.requireSuccessful
import com.kairon.android.core.network.toAppError
import com.kairon.android.core.network.userMessage
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
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

private const val DAY_TAG = "JournalViewModel"
private const val ENTRY_TAG = "JournalEntryViewModel"

data class JournalDayUiState(
    val day: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val entries: List<JournalEntryEntity> = emptyList(),
    val syncing: Boolean = true,
    /** A background `/sync` failure (D6) — shown as a banner above the cached list, never blocking it. */
    val syncError: AppError? = null,
    val error: String? = null,
)

data class JournalCalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val markedDays: Set<LocalDate> = emptySet(),
)

/**
 * Backs [JournalDayScreen] (date-navigable entry list, D1's plain-content
 * editing being the separate [JournalEntryViewModel]) and the calendar
 * popover's data (D5 — a Room-only query, no network call).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val journalApi: JournalControllerApi,
    private val journalDao: JournalDao,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val day = MutableStateFlow(LocalDate.now())
    private val syncing = MutableStateFlow(true)
    private val syncError = MutableStateFlow<AppError?>(null)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<JournalDayUiState> = combine(
        day,
        day.flatMapLatest { journalDao.observeForDay(it) },
        syncing,
        syncError,
        error,
    ) { d, entries, isSyncing, syncErr, err ->
        JournalDayUiState(d, LocalDate.now(), entries, isSyncing, syncErr, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalDayUiState())

    private val calendarMonth = MutableStateFlow(YearMonth.now())
    private val calendarMarkedDays = MutableStateFlow<Set<LocalDate>>(emptySet())
    val calendarState: StateFlow<JournalCalendarUiState> = combine(
        calendarMonth, calendarMarkedDays,
    ) { month, markedDays -> JournalCalendarUiState(month, markedDays) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalCalendarUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            syncing.value = true
            syncRepository.sync()
                .onSuccess { syncError.value = null }
                .onFailure { ex ->
                    AppLog.w(DAY_TAG, "refresh.syncFailed")
                    syncError.value = ex.toAppError()
                }
            syncing.value = false
        }
    }

    fun dismissSyncError() {
        syncError.value = null
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

    fun delete(entry: JournalEntryEntity) {
        viewModelScope.launch {
            runCatching {
                journalApi.delete4(entry.id).requireSuccessful(DAY_TAG, "delete")
            }.onSuccess {
                journalDao.deleteById(entry.id)
                AppLog.i(DAY_TAG, "delete.success", mapOf("id" to entry.id))
            }.onFailure { ex ->
                AppLog.e(DAY_TAG, "delete.failed", mapOf("id" to entry.id), throwable = ex)
                error.value = ex.toAppError().userMessage()
            }
        }
    }

    fun openCalendar() {
        setCalendarMonth(YearMonth.from(day.value))
    }

    fun setCalendarMonth(month: YearMonth) {
        calendarMonth.value = month
        viewModelScope.launch {
            val days = journalDao.findEntryDays(month.atDay(1), month.atEndOfMonth())
            calendarMarkedDays.value = days.toSet()
        }
    }
}

data class JournalEntryUiState(
    val day: LocalDate = LocalDate.now(),
    val title: String = "",
    val content: String = "",
    val mood: Int? = null,
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val error: AppError? = null,
    val saved: Boolean = false,
    /** D9 — the row was confirmed deleted elsewhere; Save stays disabled until the user backs out. */
    val gone: Boolean = false,
)

/**
 * Backs [JournalEntryScreen] — create (no `entryId` nav arg) or edit (an
 * existing one) a single entry. D1: the content field is plain markdown
 * text with no formatting toolbar.
 */
@HiltViewModel
class JournalEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val journalApi: JournalControllerApi,
    private val journalDao: JournalDao,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    private val entryId: UUID? = savedStateHandle.get<String>("entryId")
        ?.takeIf { it != "new" }
        ?.let(UUID::fromString)
    private var version: Long? = null

    private val _state = MutableStateFlow(
        JournalEntryUiState(
            day = savedStateHandle.get<String>("day")?.let(LocalDate::parse) ?: LocalDate.now(),
            isNew = entryId == null,
            loaded = entryId == null,
        ),
    )
    val state: StateFlow<JournalEntryUiState> = _state

    init {
        entryId?.let { id ->
            viewModelScope.launch {
                journalDao.findById(id)?.let { entry ->
                    version = entry.version
                    _state.value = _state.value.copy(
                        day = entry.day,
                        title = entry.title ?: "",
                        content = entry.content,
                        mood = entry.mood,
                        loaded = true,
                    )
                }
            }
        }
    }

    fun setTitle(title: String) {
        _state.value = _state.value.copy(title = title)
    }

    fun setContent(content: String) {
        _state.value = _state.value.copy(content = content)
    }

    fun setMood(mood: Int?) {
        _state.value = _state.value.copy(mood = mood)
    }

    fun save() {
        val current = _state.value
        viewModelScope.launch {
            _state.value = current.copy(saving = true, error = null)
            val id = entryId
            runCatching {
                if (id == null) {
                    val response = journalApi.create5(
                        CreateJournalEntryRequest(
                            day = current.day,
                            title = current.title.trim().ifBlank { null },
                            content = current.content,
                            mood = current.mood,
                        ),
                    )
                    response.bodyOrThrow(ENTRY_TAG, "create")
                } else {
                    val response = journalApi.patch4(
                        id,
                        PatchJournalEntryRequest(
                            title = current.title.trim().ifBlank { null },
                            content = current.content,
                            mood = current.mood,
                            expectedVersion = version,
                        ),
                    )
                    response.bodyOrThrow(ENTRY_TAG, "patch")
                }
            }.onSuccess { saved ->
                journalDao.upsert(saved.toEntity())
                AppLog.i(ENTRY_TAG, "save.success", mapOf("id" to saved.id))
                _state.value = _state.value.copy(saving = false, saved = true)
            }.onFailure { ex -> handleSaveFailure(ex, id) }
        }
    }

    private suspend fun handleSaveFailure(ex: Throwable, id: UUID?) {
        val appError = ex.toAppError()
        AppLog.e(ENTRY_TAG, "save.failed", mapOf("id" to id), throwable = ex)
        if (appError is AppError.NotFound && id != null) {
            journalDao.deleteById(id)
            _state.value = _state.value.copy(saving = false, error = appError, gone = true)
        } else {
            _state.value = _state.value.copy(saving = false, error = appError)
        }
    }

    fun delete() {
        val id = entryId ?: return
        viewModelScope.launch {
            runCatching {
                journalApi.delete4(id).requireSuccessful(ENTRY_TAG, "delete")
            }.onSuccess {
                journalDao.deleteById(id)
                AppLog.i(ENTRY_TAG, "delete.success", mapOf("id" to id))
                _state.value = _state.value.copy(saved = true)
            }.onFailure { ex ->
                val appError = ex.toAppError()
                AppLog.e(ENTRY_TAG, "delete.failed", mapOf("id" to id), throwable = ex)
                if (appError is AppError.NotFound) {
                    journalDao.deleteById(id)
                    _state.value = _state.value.copy(error = appError, gone = true)
                } else {
                    _state.value = _state.value.copy(error = appError)
                }
            }
        }
    }

    /** D10 — re-syncs then re-reads this entry from Room, overwriting any unsaved local edits. */
    fun reload() {
        val id = entryId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            syncRepository.sync().onFailure { ex -> AppLog.w(ENTRY_TAG, "reload.syncFailed", throwable = ex) }
            journalDao.findById(id)?.let { entry ->
                version = entry.version
                _state.value = _state.value.copy(
                    day = entry.day,
                    title = entry.title ?: "",
                    content = entry.content,
                    mood = entry.mood,
                )
            }
        }
    }
}
