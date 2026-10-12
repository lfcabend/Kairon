package com.kairon.android.core.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped one-shot signal distinguishing a forced session clear (expired
 * JWT, rejected/revoked refresh token — [com.kairon.android.core.network.AuthAuthenticator])
 * from an ordinary manual "Log out" (which never emits here), so the login
 * screen can explain *why* the user landed there (M11.7 D5). Not persisted —
 * a process restart has no "was mid-expiry" state to recover; the next
 * failed-refresh attempt, if any, emits fresh.
 *
 * `extraBufferCapacity = 1` so one emission survives until a collector
 * attaches, covering the ordinary case where the nav-host transition to the
 * login route hasn't finished composing yet when this fires.
 */
@Singleton
class SessionExpiredSignal @Inject constructor() {

    private val _events = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyExpired() {
        _events.tryEmit(Unit)
    }
}
