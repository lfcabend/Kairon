package com.kairon.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 10, 19)
private const val REMINDER_TIME = 18 * 60 // 18:00

class NotificationConditionsTest {

    @Test
    fun notYetTimeIsNotDue() {
        val due = isDailyReminderDue(
            today = TODAY, nowMinutesSinceMidnight = REMINDER_TIME - 1, reminderTimeMinutes = REMINDER_TIME,
            lastReminderDate = null, hasIncompleteTodos = true,
        )
        assertFalse(due)
    }

    @Test
    fun atOrAfterTimeWithIncompleteTodosAndNoPriorReminderTodayIsDue() {
        val due = isDailyReminderDue(
            today = TODAY, nowMinutesSinceMidnight = REMINDER_TIME, reminderTimeMinutes = REMINDER_TIME,
            lastReminderDate = null, hasIncompleteTodos = true,
        )
        assertTrue(due)
    }

    @Test
    fun alreadyRemindedTodayIsNotDueAgain() {
        val due = isDailyReminderDue(
            today = TODAY, nowMinutesSinceMidnight = REMINDER_TIME + 60, reminderTimeMinutes = REMINDER_TIME,
            lastReminderDate = TODAY, hasIncompleteTodos = true,
        )
        assertFalse(due)
    }

    @Test
    fun aReminderFromAnEarlierDayDoesNotSuppressToday() {
        val due = isDailyReminderDue(
            today = TODAY, nowMinutesSinceMidnight = REMINDER_TIME, reminderTimeMinutes = REMINDER_TIME,
            lastReminderDate = TODAY.minusDays(1), hasIncompleteTodos = true,
        )
        assertTrue(due)
    }

    @Test
    fun allTodosDoneIsNeverDue() {
        val due = isDailyReminderDue(
            today = TODAY, nowMinutesSinceMidnight = REMINDER_TIME + 120, reminderTimeMinutes = REMINDER_TIME,
            lastReminderDate = null, hasIncompleteTodos = false,
        )
        assertFalse(due)
    }

    @Test
    fun mixedOverdueAndNotOverdueOnlyNewlyOverdueIdsAreReturned() {
        val diff = diffOverdueTasks(
            currentOverdueTaskIds = setOf("a", "b", "c"),
            previouslyNotifiedTaskIds = setOf("a"),
        )
        assertEquals(setOf("b", "c"), diff.newlyOverdueTaskIds)
        assertEquals(setOf("a", "b", "c"), diff.nextNotifiedTaskIds)
    }

    @Test
    fun aTaskNoLongerOverdueIsPrunedFromTheNotifiedSet() {
        val diff = diffOverdueTasks(
            currentOverdueTaskIds = setOf("a"),
            previouslyNotifiedTaskIds = setOf("a", "b"),
        )
        assertEquals(emptySet<String>(), diff.newlyOverdueTaskIds)
        assertEquals(setOf("a"), diff.nextNotifiedTaskIds)
    }

    @Test
    fun noOverdueTasksAtAllProducesNoNotificationAndAnEmptyNotifiedSet() {
        val diff = diffOverdueTasks(currentOverdueTaskIds = emptySet(), previouslyNotifiedTaskIds = setOf("a"))
        assertEquals(emptySet<String>(), diff.newlyOverdueTaskIds)
        assertEquals(emptySet<String>(), diff.nextNotifiedTaskIds)
    }
}
