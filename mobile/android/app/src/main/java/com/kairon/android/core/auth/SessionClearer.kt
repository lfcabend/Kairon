package com.kairon.android.core.auth

import com.kairon.android.core.data.JournalDao
import com.kairon.android.core.data.ProjectDao
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.sync.SyncCursorStore
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionClearer"

/**
 * Single chokepoint for ending a session, whatever triggers it — an explicit
 * logout ([AuthRepository]) or a forced clear after a failed/rejected refresh
 * ([com.kairon.android.core.network.AuthAuthenticator]). Room's DAOs have no
 * `userId` column, so a stale row from the previous account would otherwise
 * render under the next one; clearing them here alongside the tokens and the
 * `/sync` cursor forces a full resync on the next login no matter which path
 * ended the session. M11.5 extends this to the three new Room tables —
 * leaving any of them out here would repeat the cross-account-leak bug M11.8
 * already found and fixed for `todo`.
 */
@Singleton
class SessionClearer @Inject constructor(
    private val tokenStore: TokenStore,
    private val todoDao: TodoDao,
    private val journalDao: JournalDao,
    private val projectDao: ProjectDao,
    private val projectTaskDao: ProjectTaskDao,
    private val syncCursorStore: SyncCursorStore,
) {
    suspend fun clearSession() {
        tokenStore.clear()
        todoDao.clearAll()
        journalDao.clearAll()
        projectDao.clearAll()
        projectTaskDao.clearAll()
        syncCursorStore.clear()
        AppLog.i(TAG, "session.cleared")
    }
}
