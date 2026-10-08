package com.kairon.android.core.auth

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the access token in memory only (same posture as the web app's
 * `client.ts`) and the refresh token in `EncryptedSharedPreferences`
 * (Keystore-backed) — docs/milestones/M11-android-foundation.md §6.2. Login
 * state is exposed as a [StateFlow] so the nav graph can react to logout from
 * anywhere (e.g. [com.kairon.android.core.network.AuthAuthenticator]'s
 * second-401 case).
 *
 * `EncryptedSharedPreferences`/`MasterKey` are deprecated in
 * `androidx.security:security-crypto:1.1.0` with no stable replacement
 * published yet — suppressed rather than worked around.
 */
@Suppress("DEPRECATION")
@Singleton
class TokenStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Volatile
    private var accessToken: String? = null

    private val _signedIn = MutableStateFlow(readRefreshToken() != null)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    fun currentAccessToken(): String? = accessToken

    fun currentRefreshToken(): String? = readRefreshToken()

    fun store(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        prefs.edit { putString(KEY_REFRESH_TOKEN, refreshToken) }
        _signedIn.value = true
    }

    /** Access-token-only update, after a successful silent refresh. */
    fun updateAccessToken(accessToken: String) {
        this.accessToken = accessToken
    }

    fun clear() {
        accessToken = null
        prefs.edit { remove(KEY_REFRESH_TOKEN) }
        _signedIn.value = false
    }

    private fun readRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    private companion object {
        const val PREFS_FILE = "kairon_auth_secure_prefs"
        const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
