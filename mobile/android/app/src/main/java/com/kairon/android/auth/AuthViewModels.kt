package com.kairon.android.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairon.android.core.auth.AuthRepository
import com.kairon.android.core.logging.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Success : AuthUiState
    data class Error(val message: String) : AuthUiState
}

private const val LOGIN_TAG = "LoginViewModel"
private const val REGISTER_TAG = "RegisterViewModel"

@HiltViewModel
class LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(email: String, password: String) {
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            // AuthRepository already logs the detailed outcome (login.success/login.failed);
            // this just reflects it into UI state — never log the password itself.
            authRepository.login(email, password)
                .onSuccess { _state.value = AuthUiState.Success }
                .onFailure {
                    AppLog.w(LOGIN_TAG, "login.uiError")
                    _state.value = AuthUiState.Error(it.message ?: "Login failed")
                }
        }
    }
}

@HiltViewModel
class RegisterViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun register(email: String, password: String, displayName: String) {
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            authRepository.register(email, password, displayName, timezone = null)
                .onSuccess { _state.value = AuthUiState.Success }
                .onFailure {
                    AppLog.w(REGISTER_TAG, "register.uiError")
                    _state.value = AuthUiState.Error(it.message ?: "Registration failed")
                }
        }
    }
}
