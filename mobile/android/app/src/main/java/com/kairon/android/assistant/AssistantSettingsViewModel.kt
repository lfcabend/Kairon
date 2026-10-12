package com.kairon.android.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.MeControllerApi
import com.kairon.android.client.model.UpdateMeRequest
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

private const val TAG = "AssistantSettingsViewModel"

data class AssistantSettingsUiState(
    val loading: Boolean = true,
    val preferences: AssistantPreferences = AssistantPreferences(),
    val rawPreferences: Map<String, JsonElement>? = null,
    val saving: Boolean = false,
    val error: String? = null,
)

/**
 * Backs [AssistantSettingsSection] (embedded in `AccountScreen`, D2): a
 * read-modify-write `PATCH /me` for every toggle/tone/model-override change,
 * using [mergedAssistantPreferencesPatch] to leave every other preferences
 * key untouched (D7).
 */
@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    private val meApi: MeControllerApi,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantSettingsUiState())
    val state: StateFlow<AssistantSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { meApi.me().bodyOrThrow(TAG, "refresh") }
                .onSuccess { me ->
                    _state.value = _state.value.copy(
                        loading = false,
                        preferences = parseAssistantPreferences(me.preferences),
                        rawPreferences = me.preferences,
                    )
                }
                .onFailure { ex ->
                    AppLog.e(TAG, "refresh.failed", throwable = ex)
                    _state.value = _state.value.copy(loading = false, error = ex.message ?: "Failed to load settings")
                }
        }
    }

    /** Applies [patch] to the current preferences and PATCHes the result. */
    fun update(patch: AssistantPreferences.() -> AssistantPreferences) {
        val next = state.value.preferences.patch()
        _state.value = _state.value.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching {
                val body = UpdateMeRequest(preferences = mergedAssistantPreferencesPatch(state.value.rawPreferences, next))
                meApi.update(body).bodyOrThrow(TAG, "update")
            }.onSuccess { me ->
                AppLog.i(TAG, "update.success")
                _state.value = _state.value.copy(
                    saving = false,
                    preferences = parseAssistantPreferences(me.preferences),
                    rawPreferences = me.preferences,
                )
            }.onFailure { ex ->
                AppLog.e(TAG, "update.failed", throwable = ex)
                _state.value = _state.value.copy(saving = false, error = ex.message ?: "Could not save")
            }
        }
    }
}
