package com.kairon.android.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.network.bodyOrThrow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

private const val TAG = "AboutViewModel"

data class AboutUiState(
    val loading: Boolean = true,
    val lines: List<String> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AboutViewModel @Inject constructor(private val aboutApi: AboutApi) : ViewModel() {

    private val _state = MutableStateFlow(AboutUiState())
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val response = aboutApi.info()
                response.bodyOrThrow(TAG, "refresh")
            }.onSuccess { info ->
                val lines = buildList {
                    info["build"]?.jsonObject?.let { build ->
                        build["version"]?.jsonPrimitive?.content?.let { add("Version: $it") }
                    }
                    info["git"]?.jsonObject?.let { git ->
                        git["commit"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.let { add("Commit: $it") }
                    }
                    info["deploy"]?.jsonObject?.let { deploy ->
                        deploy["image"]?.jsonPrimitive?.content?.let { add("Image: $it") }
                        deploy["deployedAt"]?.jsonPrimitive?.content?.let { add("Deployed: $it") }
                    }
                }
                _state.value = _state.value.copy(loading = false, lines = lines)
            }.onFailure { ex ->
                AppLog.e(TAG, "refresh.failed", throwable = ex)
                _state.value = _state.value.copy(loading = false, error = ex.message ?: "Failed to load build info")
            }
        }
    }
}
