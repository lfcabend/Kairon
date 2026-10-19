package com.kairon.android.notifications

import java.time.LocalDate

/**
 * Pure, Android/DataStore/Room-independent condition logic for
 * [NotificationCheckWorker] — kept dependency-free so it can be unit-tested
 * in isolation (M11.9 §6, mirroring the `assistant/AssistantCascade.kt`
 * precedent from M11.6).
 */

/**
 * The daily reminder fires at most once per calendar day (D5): the first
 * check at or after [reminderTimeMinutes] (minutes since midnight) on a day
 * that hasn't already reminded, and only while at least one of today's
 * todos is still incomplete.
 */
fun isDailyReminderDue(
    today: LocalDate,
    nowMinutesSinceMidnight: Int,
    reminderTimeMinutes: Int,
    lastReminderDate: LocalDate?,
    hasIncompleteTodos: Boolean,
): Boolean =
    hasIncompleteTodos && nowMinutesSinceMidnight >= reminderTimeMinutes && lastReminderDate != today

data class OverdueNotificationDiff(
    val newlyOverdueTaskIds: Set<String>,
    val nextNotifiedTaskIds: Set<String>,
)

/**
 * The overdue notification fires once per currently-overdue task (D5).
 * [nextNotifiedTaskIds] always equals [currentOverdueTaskIds] — a task that
 * drops out of the overdue set (completed, rescheduled, deleted) is pruned
 * from the stored set this way, so it can re-notify if it becomes overdue
 * again later.
 */
fun diffOverdueTasks(currentOverdueTaskIds: Set<String>, previouslyNotifiedTaskIds: Set<String>): OverdueNotificationDiff =
    OverdueNotificationDiff(
        newlyOverdueTaskIds = currentOverdueTaskIds - previouslyNotifiedTaskIds,
        nextNotifiedTaskIds = currentOverdueTaskIds,
    )
