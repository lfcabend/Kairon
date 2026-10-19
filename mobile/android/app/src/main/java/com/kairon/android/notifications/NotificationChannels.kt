package com.kairon.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Two channels (D6) so a user can mute/configure either notification type
 * independently from Android's own per-channel settings, not just this
 * app's own toggles. `minSdk` is 26 ([NotificationChannel] itself requires
 * API 26), so no version gate is needed. Created once at app start
 * ([com.kairon.android.KaironApplication]); re-creating an existing channel
 * is a no-op.
 */
object NotificationChannels {

    const val DAILY_REMINDER_CHANNEL_ID = "daily_reminder"
    const val OVERDUE_TASKS_CHANNEL_ID = "overdue_tasks"

    fun create(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(DAILY_REMINDER_CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminds you when today's todo list still has unfinished items."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(OVERDUE_TASKS_CHANNEL_ID, "Overdue project tasks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifies you when a project task becomes overdue."
            },
        )
    }
}
