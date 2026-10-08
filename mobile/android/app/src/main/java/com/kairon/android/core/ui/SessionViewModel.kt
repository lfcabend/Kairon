package com.kairon.android.core.ui

import androidx.lifecycle.ViewModel
import com.kairon.android.core.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes [TokenStore.signedIn] so the nav graph can react to login/logout from anywhere. */
@HiltViewModel
class SessionViewModel @Inject constructor(tokenStore: TokenStore) : ViewModel() {
    val signedIn: StateFlow<Boolean> = tokenStore.signedIn
}
