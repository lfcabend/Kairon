package com.kairon.android.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectFormScreen(
    onBack: () -> Unit,
    onSaved: (UUID) -> Unit,
    viewModel: ProjectFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved, state.savedProjectId) {
        if (state.saved) {
            state.savedProjectId?.let(onSaved) ?: onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New project" else "Edit project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
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

            DropdownField(
                label = "Category",
                value = state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "Uncategorized",
                options = listOf("Uncategorized" to null as UUID?) + state.categories.map { (it.name ?: "") to it.id },
                onSelect = { viewModel.setCategoryId(it) },
            )
            Spacer(modifier = Modifier.height(8.dp))

            DropdownField(
                label = "Size",
                value = state.size ?: "Not set",
                options = listOf("Not set" to null as String?) + PROJECT_SIZES.map { it to it },
                onSelect = { viewModel.setSize(it) },
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!state.isNew) {
                DropdownField(
                    label = "Status",
                    value = state.status,
                    options = PROJECT_STATUSES.map { it to it },
                    onSelect = { viewModel.setStatus(it) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = state.color,
                onValueChange = viewModel::setColor,
                label = { Text("Color (hex)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            DateField(label = "Start date", date = state.startDate, onChange = viewModel::setStartDate)
            Spacer(modifier = Modifier.height(8.dp))
            DateField(label = "End date", date = state.endDate, onChange = viewModel::setEndDate)

            if (state.error != null) {
                Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onBack) { Text("Cancel") }
                Button(onClick = viewModel::save, enabled = !state.saving) { Text("Save") }
            }
        }
    }
}

private val FORM_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row {
            TextButton(onClick = { showPicker = true }) {
                Text(date?.format(FORM_DATE_FORMATTER) ?: "Not set")
            }
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
private fun <T> DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (label, optionValue) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSelect(optionValue); expanded = false },
                    )
                }
            }
        }
    }
}
