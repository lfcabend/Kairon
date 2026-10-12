package com.kairon.android.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.core.ui.InlineError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Self-contained "New project from description" button + two-step dialog
 * (D11) — renders nothing unless `assistant.projectGeneration` is enabled.
 * Placed on [com.kairon.android.projects.ProjectListScreen]. Accepting
 * navigates to the new project's detail screen via [onCreated].
 */
@Composable
fun ProjectGenerationEntryPoint(onCreated: (UUID) -> Unit, viewModel: ProjectPlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var targetDeadline by remember { mutableStateOf<LocalDate?>(null) }

    if (state.enabled != true) return

    LaunchedEffect(state.createdProjectId) {
        state.createdProjectId?.let { id ->
            open = false
            onCreated(id)
        }
    }

    fun handleOpenChange(next: Boolean) {
        open = next
        if (!next) {
            viewModel.cancelReview()
            description = ""
            startDate = LocalDate.now()
            targetDeadline = null
        }
    }

    OutlinedButton(onClick = { handleOpenChange(true) }) { Text("New project from description") }

    if (open) {
        AlertDialog(
            onDismissRequest = { handleOpenChange(false) },
            title = { Text("New project from description") },
            text = {
                Column {
                    if (state.plan == null) {
                        Text("Describe the project — Kairon sends this description and your dates to Anthropic to propose a task tree.")
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            PlanDateField(label = "Start date", date = startDate, onChange = { startDate = it ?: startDate }, clearable = false)
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            PlanDateField(label = "Target deadline (optional)", date = targetDeadline, onChange = { targetDeadline = it }, clearable = true)
                        }
                        state.error?.let { InlineError(it) }
                    } else {
                        ProjectPlanReviewScreen(plan = state.plan!!, excludedKeys = state.excludedKeys, onToggle = viewModel::toggleExcluded)
                        state.error?.let { InlineError(it) }
                    }
                }
            },
            confirmButton = {
                if (state.plan == null) {
                    TextButton(
                        enabled = !state.generating && description.isNotBlank(),
                        onClick = { viewModel.generate(description, startDate, targetDeadline) },
                    ) { Text(if (state.generating) "Thinking…" else "Generate plan") }
                } else {
                    TextButton(enabled = !state.accepting, onClick = viewModel::accept) {
                        Text(if (state.accepting) "Creating…" else "Create project")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { handleOpenChange(false) }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit, clearable: Boolean) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Text(label)
        Row {
            TextButton(onClick = { showPicker = true }) { Text(date?.toString() ?: "Not set") }
            if (clearable && date != null) {
                TextButton(onClick = { onChange(null) }) { Text("Clear") }
            }
        }
    }
    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
