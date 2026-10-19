package com.kairon.android.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Embedded in `AccountScreen`, mirroring `AssistantSettingsSection`'s own
 * "embedded section, not a new screen" precedent — two toggles (both
 * default off) and a time picker for the daily reminder, shown only while
 * that toggle is on. An off→on flip requests the Android 13+
 * `POST_NOTIFICATIONS` permission first (§5.3); a denial still saves the
 * toggle — [NotificationCheckWorker]'s own `areNotificationsEnabled()`
 * check at post-time is the real gate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSection(viewModel: NotificationsSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // A denial still runs the pending action — the toggle saves regardless (§5.3).
        pendingEnable?.invoke()
        pendingEnable = null
    }

    fun enableWithPermissionCheck(onEnabled: () -> Unit) {
        val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsRequest) {
            pendingEnable = onEnabled
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onEnabled()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        Text(
            "On-device only — these settings aren't synced to other devices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        ToggleRow(
            title = "Remind me about unfinished todos",
            description = "A once-a-day reminder if today's todo list still has unfinished items.",
            checked = state.dailyReminderEnabled,
            onCheckedChange = { checked ->
                if (checked) enableWithPermissionCheck { viewModel.setDailyReminderEnabled(true) }
                else viewModel.setDailyReminderEnabled(false)
            },
        )
        if (state.dailyReminderEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Remind at ${formatMinutesOfDay(state.dailyReminderTimeMinutes)}", modifier = Modifier.weight(1f))
                TextButton(onClick = { showTimePicker = true }) { Text("Change") }
            }
        }

        ToggleRow(
            title = "Notify me about overdue project tasks",
            description = "A notification whenever a project task becomes overdue.",
            checked = state.overdueNotificationsEnabled,
            onCheckedChange = { checked ->
                if (checked) enableWithPermissionCheck { viewModel.setOverdueNotificationsEnabled(true) }
                else viewModel.setOverdueNotificationsEnabled(false)
            },
        )
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = state.dailyReminderTimeMinutes / 60,
            initialMinute = state.dailyReminderTimeMinutes % 60,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDailyReminderTimeMinutes(timePickerState.hour * 60 + timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }
}

private fun formatMinutesOfDay(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern("h:mm a"))

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
