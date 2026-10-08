package com.kairon.android.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

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
                val body = response.body()
                if (!response.isSuccessful || body == null) error("HTTP ${response.code()}")
                body
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
                _state.value = _state.value.copy(loading = false, error = ex.message ?: "Failed to load build info")
            }
        }
    }
}
