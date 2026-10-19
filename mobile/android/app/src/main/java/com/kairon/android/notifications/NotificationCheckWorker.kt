package com.kairon.android.notifications

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kairon.android.R
import com.kairon.android.core.data.ProjectTaskDao
import com.kairon.android.core.data.TodoDao
import com.kairon.android.core.logging.AppLog
import com.kairon.android.core.sync.SyncRepository
import com.kairon.android.core.sync.SyncScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalTime

private const val TAG = "NotificationCheckWorker"
private const val DAILY_REMINDER_NOTIFICATION_ID = 1001
private const val OVERDUE_TASKS_NOTIFICATION_ID = 1002

/**
 * The ~30-minute periodic job (D4) [NotificationScheduler] enqueues: a cheap
 * no-op if both toggles are off, otherwise refreshes Room via its own
 * [SyncScope.NOTIFICATIONS] sync (D3) before evaluating the two pure
 * conditions in [NotificationConditions] against the refreshed cache.
 * Always returns [androidx.work.ListenableWorker.Result.success] (§5.4 step
 * 5) — a missed tick is recovered by the next one 30 minutes later, so
 * nothing here should trigger `WorkManager`'s own retry/backoff.
 */
@HiltWorker
class NotificationCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val todoDao: TodoDao,
    private val projectTaskDao: ProjectTaskDao,
    private val preferences: NotificationPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = preferences.current()
        if (!prefs.dailyReminderEnabled && !prefs.overdueNotificationsEnabled) {
            AppLog.d(TAG, "check.skipped", mapOf("reason" to "bothTogglesOff"))
            return Result.success()
        }

        syncRepository.sync(SyncScope.NOTIFICATIONS)
            .onFailure { ex -> AppLog.w(TAG, "check.syncFailed", throwable = ex) }

        val today = LocalDate.now()
        if (prefs.dailyReminderEnabled) {
            checkDailyReminder(prefs, today)
        }
        if (prefs.overdueNotificationsEnabled) {
            checkOverdueTasks(today)
        }
        return Result.success()
    }

    private suspend fun checkDailyReminder(prefs: NotificationPreferencesState, today: LocalDate) {
        val nowMinutes = LocalTime.now().toSecondOfDay() / 60
        val lastReminderDate = preferences.lastDailyReminderDate()
        val hasIncompleteTodos = todoDao.listForDay(today).any { it.status != "DONE" }
        val due = isDailyReminderDue(
            today = today,
            nowMinutesSinceMidnight = nowMinutes,
            reminderTimeMinutes = prefs.dailyReminderTimeMinutes,
            lastReminderDate = lastReminderDate,
            hasIncompleteTodos = hasIncompleteTodos,
        )
        if (!due) return

        postNotification(
            channelId = NotificationChannels.DAILY_REMINDER_CHANNEL_ID,
            notificationId = DAILY_REMINDER_NOTIFICATION_ID,
            title = "Unfinished todos today",
            text = "You still have unfinished items on today's todo list.",
        )
        preferences.setLastDailyReminderDate(today)
        AppLog.i(TAG, "check.dailyReminderFired")
    }

    private suspend fun checkOverdueTasks(today: LocalDate) {
        val overdueIds = projectTaskDao.findOverdue(today).map { it.id.toString() }.toSet()
        val previouslyNotified = preferences.notifiedOverdueTaskIds()
        val diff = diffOverdueTasks(overdueIds, previouslyNotified)

        if (diff.newlyOverdueTaskIds.isNotEmpty()) {
            val count = diff.newlyOverdueTaskIds.size
            postNotification(
                channelId = NotificationChannels.OVERDUE_TASKS_CHANNEL_ID,
                notificationId = OVERDUE_TASKS_NOTIFICATION_ID,
                title = if (count == 1) "A project task is overdue" else "$count project tasks are overdue",
                text = if (count == 1) "One of your project tasks is now overdue." else "$count of your project tasks are now overdue.",
            )
            AppLog.i(TAG, "check.overdueTasksFired", mapOf("count" to count))
        }
        preferences.setNotifiedOverdueTaskIds(diff.nextNotifiedTaskIds)
    }

    /** The real gate is [NotificationManagerCompat.areNotificationsEnabled] — a denied permission still saves the toggle (§5.3). */
    private fun postNotification(channelId: String, notificationId: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(applicationContext)
        // areNotificationsEnabled() is the real gate (§5.3) — the explicit
        // checkSelfPermission below is only to satisfy lint's MissingPermission
        // check, since it doesn't recognize areNotificationsEnabled() as one.
        val hasPermission = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!manager.areNotificationsEnabled() || !hasPermission) {
            AppLog.w(TAG, "check.notificationsDisabled", mapOf("channelId" to channelId))
            return
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }
}
