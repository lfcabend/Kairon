package com.kairon.android.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/**
 * A plain [PreferenceDataStoreFactory]-built store over a JUnit temp file —
 * no Context/Robolectric needed, since [NotificationPreferences] takes the
 * `DataStore<Preferences>` itself rather than building one from a `Context`.
 *
 * Each test below gets its own fresh backing file via [newPreferences] —
 * issuing a *second* write against the same on-disk store within one JVM
 * run hits a Windows-only `datastore-preferences` limitation (the second
 * write's atomic rename fails with "Unable to rename ... This likely means
 * there are multiple instances of DataStore for this file", even though
 * there's only ever one); not reproducible on Linux CI or a real Android
 * device, but routed around here so this round-trip coverage is reliable on
 * every contributor's machine too. One write per store still proves each
 * field's own get/set correctness.
 */
class NotificationPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var fileCounter = 0

    private fun newPreferences(): NotificationPreferences {
        val file = File(tempFolder.root, "notification_prefs_test_${fileCounter++}.preferences_pb")
        return NotificationPreferences(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test
    fun aBrandNewInstallDefaultsEverythingOff() = runBlocking {
        val preferences = newPreferences()

        val state = preferences.current()

        assertFalse(state.dailyReminderEnabled)
        assertEquals(DEFAULT_DAILY_REMINDER_TIME_MINUTES, state.dailyReminderTimeMinutes)
        assertFalse(state.overdueNotificationsEnabled)
        assertNull(preferences.lastDailyReminderDate())
        assertEquals(emptySet<String>(), preferences.notifiedOverdueTaskIds())
    }

    @Test
    fun dailyReminderEnabledRoundTrips() = runBlocking {
        val preferences = newPreferences()

        preferences.setDailyReminderEnabled(true)

        assertTrue(preferences.current().dailyReminderEnabled)
    }

    @Test
    fun dailyReminderTimeMinutesRoundTrips() = runBlocking {
        val preferences = newPreferences()

        preferences.setDailyReminderTimeMinutes(7 * 60 + 30)

        assertEquals(7 * 60 + 30, preferences.current().dailyReminderTimeMinutes)
    }

    @Test
    fun overdueNotificationsEnabledRoundTrips() = runBlocking {
        val preferences = newPreferences()

        preferences.setOverdueNotificationsEnabled(true)

        assertTrue(preferences.current().overdueNotificationsEnabled)
    }

    @Test
    fun lastDailyReminderDateRoundTrips() = runBlocking {
        val preferences = newPreferences()

        preferences.setLastDailyReminderDate(LocalDate.of(2026, 10, 19))

        assertEquals(LocalDate.of(2026, 10, 19), preferences.lastDailyReminderDate())
    }

    @Test
    fun notifiedOverdueTaskIdsRoundTrips() = runBlocking {
        val preferences = newPreferences()

        preferences.setNotifiedOverdueTaskIds(setOf("task-a", "task-b"))

        assertEquals(setOf("task-a", "task-b"), preferences.notifiedOverdueTaskIds())
    }
}
