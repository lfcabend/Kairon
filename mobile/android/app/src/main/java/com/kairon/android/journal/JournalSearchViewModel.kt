package com.kairon.android.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.JournalControllerApi
import com.kairon.android.client.model.JournalSearchHitResponse
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import com.kairon.android.core.network.toAppError
import com.kairon.android.core.network.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "JournalSearchViewModel"

data class JournalSearchUiState(
    val query: String = "",
    val results: List<JournalSearchHitResponse> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null,
)

/**
 * D6 — search is a live network call (`GET /journal:search`), never
 * Room-backed: full-text ranking/snippets are a Postgres-side computation
 * with no local equivalent.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class JournalSearchViewModel @Inject constructor(
    private val journalApi: JournalControllerApi,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<List<JournalSearchHitResponse>>(emptyList())
    private val searching = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<JournalSearchUiState> = combine(
        query, results, searching, error,
    ) { q, r, s, e -> JournalSearchUiState(q, r, s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JournalSearchUiState())

    init {
        viewModelScope.launch {
            query.debounce(300).collectLatest { q ->
                if (q.isBlank()) {
                    results.value = emptyList()
                    searching.value = false
                    return@collectLatest
                }
                searching.value = true
                runCatching {
                    journalApi.search(q).bodyOrThrow(TAG, "search")
                }.onSuccess { page ->
                    results.value = page.content ?: emptyList()
                    error.value = null
                    searching.value = false
                }.onFailure { ex ->
                    AppLog.e(TAG, "search.failed", throwable = ex)
                    error.value = ex.toAppError().userMessage()
                    searching.value = false
                }
            }
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }
}
