package com.kairon.android.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.client.api.MeControllerApi
import com.kairon.android.client.model.MeResponse
import com.kairon.android.core.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val loading: Boolean = true,
    val me: MeResponse? = null,
    val error: String? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val meApi: MeControllerApi,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val response = meApi.me()
                val body = response.body()
                if (!response.isSuccessful || body == null) error("HTTP ${response.code()}")
                body
            }.onSuccess { me ->
                _state.value = _state.value.copy(loading = false, me = me)
            }.onFailure { ex ->
                _state.value = _state.value.copy(loading = false, error = ex.message ?: "Failed to load profile")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = _state.value.copy(loggedOut = true)
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            authRepository.logoutAll()
            _state.value = _state.value.copy(loggedOut = true)
        }
    }
}
