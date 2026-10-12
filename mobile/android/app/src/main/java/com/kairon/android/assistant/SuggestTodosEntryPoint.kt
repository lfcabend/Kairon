package com.kairon.android.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.client.model.TodoSuggestionRequest
import java.time.LocalDate

/**
 * Self-contained "Suggest todos" button + two-step dialog (D11) — renders
 * nothing while the flag check is loading or if `assistant.todoSuggestions`
 * is disabled. Placed on both [com.kairon.android.today.TodayScreen] and
 * [com.kairon.android.todo.DayViewScreen], mirroring web's own
 * `SuggestTodosButton` usage on both screens.
 */
@Composable
fun SuggestTodosEntryPoint(day: LocalDate, viewModel: SuggestTodosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    var horizon by remember { mutableStateOf(TodoSuggestionRequest.Horizon.DAY) }

    if (state.enabled != true) return

    fun handleOpenChange(next: Boolean) {
        open = next
        if (!next) {
            viewModel.reset()
            horizon = TodoSuggestionRequest.Horizon.DAY
        }
    }

    OutlinedButton(onClick = { handleOpenChange(true) }) { Text("Suggest todos") }

    if (open) {
        AlertDialog(
            onDismissRequest = { handleOpenChange(false) },
            title = { Text("Suggest todos") },
            text = {
                Column {
                    if (state.suggestions == null) {
                        Text("Kairon will look at your open project tasks, recent todo history, and recent journal entries.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = horizon == TodoSuggestionRequest.Horizon.DAY, onClick = { horizon = TodoSuggestionRequest.Horizon.DAY })
                            Text("Just $day")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = horizon == TodoSuggestionRequest.Horizon.WEEK, onClick = { horizon = TodoSuggestionRequest.Horizon.WEEK })
                            Text("This week")
                        }
                        state.error?.let { Text(it) }
                    } else {
                        SuggestedTaskList(
                            suggestions = state.suggestions.orEmpty(),
                            onAccept = viewModel::accept,
                            onDismiss = viewModel::dismiss,
                        )
                    }
                }
            },
            confirmButton = {
                if (state.suggestions == null) {
                    TextButton(enabled = !state.requesting, onClick = { viewModel.suggest(day, horizon) }) {
                        Text(if (state.requesting) "Thinking…" else "Suggest")
                    }
                } else {
                    TextButton(onClick = { handleOpenChange(false) }) { Text("Done") }
                }
            },
            dismissButton = {
                if (state.suggestions == null) {
                    TextButton(onClick = { handleOpenChange(false) }) { Text("Cancel") }
                }
            },
        )
    }
}
