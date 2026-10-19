package com.kairon.android.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs [NotificationsSection] (embedded in `AccountScreen`) — reads/writes [NotificationPreferences] directly, no repository layer needed for five scalar values (D1/D9). */
@HiltViewModel
class NotificationsSettingsViewModel @Inject constructor(
    private val preferences: NotificationPreferences,
) : ViewModel() {

    val state: StateFlow<NotificationPreferencesState> = preferences.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationPreferencesState())

    fun setDailyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setDailyReminderEnabled(enabled) }
    }

    fun setDailyReminderTimeMinutes(minutes: Int) {
        viewModelScope.launch { preferences.setDailyReminderTimeMinutes(minutes) }
    }

    fun setOverdueNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setOverdueNotificationsEnabled(enabled) }
    }
}
