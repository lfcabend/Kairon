package com.kairon.android.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_DAILY_REMINDER_TIME_MINUTES = 18 * 60

data class NotificationPreferencesState(
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderTimeMinutes: Int = DEFAULT_DAILY_REMINDER_TIME_MINUTES,
    val overdueNotificationsEnabled: Boolean = false,
)

private val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
private val DAILY_REMINDER_TIME_MINUTES = intPreferencesKey("daily_reminder_time_minutes")
private val OVERDUE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("overdue_notifications_enabled")
private val LAST_DAILY_REMINDER_DATE = stringPreferencesKey("last_daily_reminder_date")
private val NOTIFIED_OVERDUE_TASK_IDS = stringPreferencesKey("notified_overdue_task_ids")

/**
 * On-device-only notification preferences + bookkeeping (M11.9 D1/D5/D9) — a
 * small Preferences DataStore, the same conceptual shape as
 * [com.kairon.android.core.sync.SyncCursorStore] (a handful of per-device
 * scalar values, not a business entity worth a Room table). The
 * [DataStore] itself is injected (see `NotificationModule`) rather than
 * built here from a `Context` the way `SyncCursorStore` does, so
 * `NotificationPreferencesTest` can hand it its own store over a temp file
 * — this app has no Robolectric, so a real `Context` isn't available under
 * plain JUnit.
 */
@Singleton
class NotificationPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val state: Flow<NotificationPreferencesState> = dataStore.data.map { prefs ->
        NotificationPreferencesState(
            dailyReminderEnabled = prefs[DAILY_REMINDER_ENABLED] ?: false,
            dailyReminderTimeMinutes = prefs[DAILY_REMINDER_TIME_MINUTES] ?: DEFAULT_DAILY_REMINDER_TIME_MINUTES,
            overdueNotificationsEnabled = prefs[OVERDUE_NOTIFICATIONS_ENABLED] ?: false,
        )
    }

    suspend fun current(): NotificationPreferencesState = state.first()

    suspend fun setDailyReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[DAILY_REMINDER_ENABLED] = enabled }
    }

    suspend fun setDailyReminderTimeMinutes(minutes: Int) {
        dataStore.edit { it[DAILY_REMINDER_TIME_MINUTES] = minutes }
    }

    suspend fun setOverdueNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[OVERDUE_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun lastDailyReminderDate(): LocalDate? =
        dataStore.data.first()[LAST_DAILY_REMINDER_DATE]?.let(LocalDate::parse)

    suspend fun setLastDailyReminderDate(date: LocalDate) {
        dataStore.edit { it[LAST_DAILY_REMINDER_DATE] = date.toString() }
    }

    suspend fun notifiedOverdueTaskIds(): Set<String> =
        dataStore.data.first()[NOTIFIED_OVERDUE_TASK_IDS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    suspend fun setNotifiedOverdueTaskIds(ids: Set<String>) {
        dataStore.edit { it[NOTIFIED_OVERDUE_TASK_IDS] = ids.joinToString(",") }
    }
}
