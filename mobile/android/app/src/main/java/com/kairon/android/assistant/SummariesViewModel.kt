package com.kairon.android.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.AssistantRunControllerApi
import com.kairon.android.client.api.MeControllerApi
import com.kairon.android.client.model.AssistantRunView
import com.kairon.android.client.model.JournalReflectionRequest
import com.kairon.android.client.model.SummaryRequest
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

private const val TAG = "SummariesViewModel"
private const val POLL_INTERVAL_MS = 2000L
private val IN_FLIGHT = setOf("PENDING", "RUNNING")

data class SummariesUiState(
    val loading: Boolean = true,
    val journalReflectionEnabled: Boolean = false,
    val runs: List<AssistantRunView> = emptyList(),
    val requestingSummary: Boolean = false,
    val requestingReflection: Boolean = false,
    val error: String? = null,
    val selectedRun: AssistantRunView? = null,
    val selectedRunLoading: Boolean = false,
)

/**
 * Backs [SummariesScreen]: lists weekly/monthly summary (and, if opted in,
 * journal-reflection) runs, generates new ones, and polls one selected run
 * at a fixed interval while it's `PENDING`/`RUNNING` (D8), mirroring web's
 * `useAssistantRun`/`POLL_INTERVAL_MS`.
 */
@HiltViewModel
class SummariesViewModel @Inject constructor(
    private val meApi: MeControllerApi,
    private val assistantRunApi: AssistantRunControllerApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SummariesUiState())
    val state: StateFlow<SummariesUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val journalReflectionEnabled = runCatching { meApi.me().bodyOrThrow(TAG, "refresh.me") }
                .map { parseAssistantPreferences(it.preferences).journalReflection.enabled }
                .getOrDefault(false)
            val kinds = listOf("WEEKLY_SUMMARY", "MONTHLY_SUMMARY") + if (journalReflectionEnabled) listOf("JOURNAL_REFLECTION") else emptyList()
            runCatching { assistantRunApi.listRuns(kind = kinds).bodyOrThrow(TAG, "refresh.listRuns") }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        loading = false,
                        journalReflectionEnabled = journalReflectionEnabled,
                        runs = page.content.orEmpty(),
                    )
                }
                .onFailure { ex ->
                    AppLog.e(TAG, "refresh.failed", throwable = ex)
                    _state.value = _state.value.copy(loading = false, journalReflectionEnabled = journalReflectionEnabled, error = ex.message ?: "Failed to load summaries")
                }
        }
    }

    fun generateSummary(period: SummaryRequest.Period) {
        _state.value = _state.value.copy(requestingSummary = true, error = null)
        viewModelScope.launch {
            runCatching { assistantRunApi.requestSummary(SummaryRequest(period, LocalDate.now())).bodyOrThrow(TAG, "requestSummary") }
                .onSuccess { run ->
                    AppLog.i(TAG, "requestSummary.success", mapOf("runId" to run.id))
                    _state.value = _state.value.copy(requestingSummary = false)
                    openRun(run)
                    refresh()
                }
                .onFailure { ex ->
                    AppLog.e(TAG, "requestSummary.failed", throwable = ex)
                    _state.value = _state.value.copy(requestingSummary = false, error = ex.message ?: "Could not start a summary run")
                }
        }
    }

    fun generateReflection() {
        _state.value = _state.value.copy(requestingReflection = true, error = null)
        viewModelScope.launch {
            runCatching { assistantRunApi.requestJournalReflection(JournalReflectionRequest(LocalDate.now())).bodyOrThrow(TAG, "requestJournalReflection") }
                .onSuccess { run ->
                    AppLog.i(TAG, "requestJournalReflection.success", mapOf("runId" to run.id))
                    _state.value = _state.value.copy(requestingReflection = false)
                    openRun(run)
                    refresh()
                }
                .onFailure { ex ->
                    AppLog.e(TAG, "requestJournalReflection.failed", throwable = ex)
                    _state.value = _state.value.copy(requestingReflection = false, error = ex.message ?: "Could not start a reflection run")
                }
        }
    }

    fun openRun(run: AssistantRunView) {
        pollJob?.cancel()
        _state.value = _state.value.copy(selectedRun = run, selectedRunLoading = false)
        val id = run.id ?: return
        if (run.status in IN_FLIGHT) {
            pollJob = viewModelScope.launch { pollLoop(id) }
        }
    }

    fun closeDetail() {
        pollJob?.cancel()
        _state.value = _state.value.copy(selectedRun = null, selectedRunLoading = false)
    }

    private suspend fun pollLoop(id: UUID) {
        while (true) {
            val result = runCatching { assistantRunApi.getRun(id).bodyOrThrow(TAG, "getRun") }
            val run = result.getOrNull()
            if (run == null) {
                _state.value = _state.value.copy(selectedRunLoading = false, error = result.exceptionOrNull()?.message ?: "Failed to load run")
                return
            }
            _state.value = _state.value.copy(selectedRun = run, selectedRunLoading = false)
            if (run.status !in IN_FLIGHT) return
            delay(POLL_INTERVAL_MS)
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
    }
}
