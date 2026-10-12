package com.kairon.android.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.AssistantRunControllerApi
import com.kairon.android.client.api.MeControllerApi
import com.kairon.android.client.api.SuggestedTaskControllerApi
import com.kairon.android.client.model.AssistantSuggestedTaskView
import com.kairon.android.client.model.TodoSuggestionRequest
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.network.toAppError
import com.kairon.android.core.network.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "SuggestTodosViewModel"

data class SuggestTodosUiState(
    val enabled: Boolean? = null,
    val requesting: Boolean = false,
    val suggestions: List<AssistantSuggestedTaskView>? = null,
    val error: String? = null,
)

/**
 * Backs [SuggestTodosEntryPoint] (D11 — checks its own `GET /me` flag):
 * `POST /assistant/todo-suggestions` is synchronous (D8), returning a
 * populated run directly; accept/dismiss resolve one row at a time, same
 * as web's `SuggestedTaskList`.
 */
@HiltViewModel
class SuggestTodosViewModel @Inject constructor(
    private val meApi: MeControllerApi,
    private val assistantRunApi: AssistantRunControllerApi,
    private val suggestedTaskApi: SuggestedTaskControllerApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SuggestTodosUiState())
    val state: StateFlow<SuggestTodosUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { meApi.me().bodyOrThrow(TAG, "checkEnabled") }
                .onSuccess { me -> _state.value = _state.value.copy(enabled = parseAssistantPreferences(me.preferences).todoSuggestions.enabled) }
                .onFailure { ex ->
                    AppLog.w(TAG, "checkEnabled.failed", throwable = ex)
                    _state.value = _state.value.copy(enabled = false)
                }
        }
    }

    fun suggest(day: LocalDate, horizon: TodoSuggestionRequest.Horizon) {
        _state.value = _state.value.copy(requesting = true, error = null)
        viewModelScope.launch {
            runCatching {
                assistantRunApi.suggestTodos(TodoSuggestionRequest(day, horizon)).bodyOrThrow(TAG, "suggest")
            }.onSuccess { run ->
                AppLog.i(TAG, "suggest.success", mapOf("count" to run.suggestions.orEmpty().size))
                _state.value = _state.value.copy(requesting = false, suggestions = run.suggestions.orEmpty())
            }.onFailure { ex ->
                AppLog.e(TAG, "suggest.failed", throwable = ex)
                _state.value = _state.value.copy(requesting = false, error = ex.toAppError().userMessage())
            }
        }
    }

    fun accept(suggestion: AssistantSuggestedTaskView) {
        val id = suggestion.id ?: return
        viewModelScope.launch {
            runCatching { suggestedTaskApi.accept(id).bodyOrThrow(TAG, "accept") }
                .onSuccess {
                    AppLog.i(TAG, "accept.success", mapOf("id" to id))
                    resolve(id)
                }
                .onFailure { ex ->
                    AppLog.e(TAG, "accept.failed", mapOf("id" to id), throwable = ex)
                    _state.value = _state.value.copy(error = ex.toAppError().userMessage())
                }
        }
    }

    fun dismiss(suggestion: AssistantSuggestedTaskView) {
        val id = suggestion.id ?: return
        viewModelScope.launch {
            runCatching { suggestedTaskApi.dismiss(id).bodyOrThrow(TAG, "dismiss") }
                .onSuccess {
                    AppLog.i(TAG, "dismiss.success", mapOf("id" to id))
                    resolve(id)
                }
                .onFailure { ex ->
                    AppLog.e(TAG, "dismiss.failed", mapOf("id" to id), throwable = ex)
                    _state.value = _state.value.copy(error = ex.toAppError().userMessage())
                }
        }
    }

    private fun resolve(id: java.util.UUID) {
        _state.value = _state.value.copy(suggestions = _state.value.suggestions?.filterNot { it.id == id })
    }

    /** Resets review state when the dialog closes, so reopening starts a fresh request. */
    fun reset() {
        _state.value = _state.value.copy(requesting = false, suggestions = null, error = null)
    }
}
