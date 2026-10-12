package com.kairon.android.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.core.network.AppError
import com.kairon.android.core.network.userMessage
import com.kairon.android.core.ui.InlineError
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TASK_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    onBack: () -> Unit,
    viewModel: ProjectTaskViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New task" else "Edit task") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete task")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        val fieldError = { field: String ->
            (state.error as? AppError.Validation)?.fieldErrors?.firstOrNull { it.field == field }?.message
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
                isError = fieldError("name") != null,
                supportingText = fieldError("name")?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!state.isNew) {
                PickerRow(
                    label = "Status",
                    value = state.status,
                    options = TASK_STATUSES,
                    onSelect = viewModel::setStatus,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            PickerRow(
                label = "Parent task",
                value = state.siblingTasks.firstOrNull { it.id == state.parentTaskId }?.name ?: "None",
                options = listOf("None") + state.siblingTasks.map { it.name },
                onSelect = { selected ->
                    viewModel.setParentTaskId(state.siblingTasks.firstOrNull { it.name == selected }?.id)
                },
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.isMilestone, onCheckedChange = viewModel::setIsMilestone)
                Text("Milestone")
            }
            Spacer(modifier = Modifier.height(8.dp))

            TaskDateField(label = "Planned start", date = state.plannedStart, onChange = viewModel::setPlannedStart)
            Spacer(modifier = Modifier.height(8.dp))
            TaskDateField(label = "Planned end", date = state.plannedEnd, onChange = viewModel::setPlannedEnd)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.estimateHours,
                onValueChange = viewModel::setEstimateHours,
                label = { Text("Estimate hours") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!state.isNew) {
                OutlinedTextField(
                    value = state.actualHours,
                    onValueChange = viewModel::setActualHours,
                    label = { Text("Actual hours") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text("Progress: ${state.progressPercent}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.progressPercent.toFloat(),
                    onValueChange = { viewModel.setProgressPercent(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 19,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                DependencySection(
                    dependencies = state.dependencies,
                    loading = state.loadingDependencies,
                    candidates = state.siblingTasks,
                    onAdd = viewModel::addDependency,
                    onRemove = viewModel::removeDependency,
                )
            }

            val formLevelError = state.error?.let {
                if (it is AppError.Validation) null else it.userMessage()
            }
            formLevelError?.let { message ->
                InlineError(
                    message,
                    actionLabel = if (state.error is AppError.Conflict) "Reload" else null,
                    onAction = if (state.error is AppError.Conflict) viewModel::reload else null,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onBack) { Text("Cancel") }
                Button(onClick = viewModel::save, enabled = !state.saving && !state.gone) { Text("Save") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row {
            TextButton(onClick = { showPicker = true }) { Text(date?.format(TASK_DATE_FORMATTER) ?: "Not set") }
            if (date != null) {
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

@Composable
private fun PickerRow(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}
