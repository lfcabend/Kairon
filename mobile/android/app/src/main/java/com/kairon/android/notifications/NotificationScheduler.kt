package com.kairon.android.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kairon.android.core.logging.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotificationScheduler"
private const val UNIQUE_WORK_NAME = "notification_check"

/**
 * D8 — ties [NotificationCheckWorker]'s lifecycle to the session:
 * [enqueue] is called after a successful login/register and on app start
 * if a session already exists; [cancel] is added to
 * [com.kairon.android.core.auth.SessionClearer.clearSession] alongside the
 * existing token/Room/cursor clearing, so a logged-out device doesn't keep
 * firing (and failing) a `/sync` call every ~30 minutes forever.
 */
@Singleton
class NotificationScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun enqueue() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<NotificationCheckWorker>(Duration.ofMinutes(30))
            .setConstraints(constraints)
            .build()
        // KEEP — re-enqueuing (e.g. every app start) must not reset an
        // already-scheduled job's own interval timing.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        AppLog.i(TAG, "scheduler.enqueued")
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        AppLog.i(TAG, "scheduler.cancelled")
    }
}
