package com.kairon.android.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * Self-contained "Edit with AI" button + description-form dialog (D11) —
 * renders nothing unless `assistant.projectEditing` is enabled. Placed on
 * [com.kairon.android.projects.ProjectDetailScreen]; [onApplied] lets the
 * host screen refresh its own task/dependency data after a successful apply.
 */
@Composable
fun ProjectEditEntryPoint(onApplied: () -> Unit, viewModel: ProjectEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }

    if (state.enabled != true) return

    LaunchedEffect(state.applied) {
        if (state.applied) {
            open = false
            onApplied()
        }
    }

    fun handleOpenChange(next: Boolean) {
        open = next
        if (next) {
            viewModel.loadContext()
        } else {
            viewModel.cancelReview()
            description = ""
        }
    }

    OutlinedButton(onClick = { handleOpenChange(true) }) { Text("Edit with AI") }

    if (open) {
        AlertDialog(
            onDismissRequest = { handleOpenChange(false) },
            title = { Text("Edit project with AI") },
            text = {
                Column {
                    if (state.diff == null) {
                        Text(
                            "Describe the change — Kairon sends this project's current tasks, " +
                                "dependencies, and your description to Anthropic to propose a diff.",
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Describe the change") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        state.error?.let { InlineError(it) }
                    } else {
                        ProjectEditReviewScreen(
                            diff = state.diff!!,
                            currentTasks = state.tasks,
                            currentDependencies = state.dependencies,
                            excludedKeys = state.excludedKeys,
                            onToggle = viewModel::toggleExcluded,
                        )
                        state.error?.let { InlineError(it) }
                    }
                }
            },
            confirmButton = {
                if (state.diff == null) {
                    TextButton(
                        enabled = !state.generating && description.isNotBlank(),
                        onClick = { viewModel.generate(description) },
                    ) { Text(if (state.generating) "Thinking…" else "Generate changes") }
                } else {
                    TextButton(enabled = !state.applying, onClick = viewModel::apply) {
                        Text(if (state.applying) "Applying…" else "Apply changes")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { handleOpenChange(false) }) { Text("Cancel") }
            },
        )
    }
}
