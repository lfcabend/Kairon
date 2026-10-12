package com.kairon.android.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val TONE_OPTIONS: List<Pair<String, String>> = listOf("encouraging" to "Encouraging", "balanced" to "Balanced", "direct" to "Direct")
private val MODEL_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "Instance default",
    "claude-sonnet-5" to "Claude Sonnet 5",
    "claude-opus-5" to "Claude Opus 5",
    "claude-haiku-4-5" to "Claude Haiku 4.5",
)

/**
 * Embedded in `AccountScreen` (D2) — five per-feature opt-in toggles, a tone
 * selector, and a model-override selector, mirroring web's `AssistantSettings`.
 * Enabling journal reflection specifically opens a confirmation dialog first
 * (D4); every other toggle commits immediately.
 */
@Composable
fun AssistantSettingsSection(viewModel: AssistantSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var confirmingJournalReflection by remember { mutableStateOf(false) }
    val prefs = state.preferences

    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text("Assistant", style = MaterialTheme.typography.titleMedium)
        Text(
            "Optional, off by default. When enabled, your open project tasks, recent todo " +
                "history, and recent journal entries are sent to Anthropic to generate " +
                "suggestions — nothing is ever created without you explicitly accepting it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        ToggleRow(
            title = "Todo suggestions",
            description = "Show a \"Suggest todos\" option on Today and the day view.",
            checked = prefs.todoSuggestions.enabled,
            enabled = !state.saving,
            onCheckedChange = { checked -> viewModel.update { copy(todoSuggestions = AssistantFeatureToggle(checked)) } },
        )
        ToggleRow(
            title = "Project generation",
            description = "Show a \"New project from description\" option on the project list.",
            checked = prefs.projectGeneration.enabled,
            enabled = !state.saving,
            onCheckedChange = { checked -> viewModel.update { copy(projectGeneration = AssistantFeatureToggle(checked)) } },
        )
        ToggleRow(
            title = "Project editing",
            description = "Show an \"Edit with AI\" option on a project's detail screen.",
            checked = prefs.projectEditing.enabled,
            enabled = !state.saving,
            onCheckedChange = { checked -> viewModel.update { copy(projectEditing = AssistantFeatureToggle(checked)) } },
        )
        ToggleRow(
            title = "Execution summaries",
            description = "Auto-generate a weekly and monthly summary of your todo and project activity.",
            checked = prefs.executionSummaries.enabled,
            enabled = !state.saving,
            onCheckedChange = { checked -> viewModel.update { copy(executionSummaries = AssistantFeatureToggle(checked)) } },
        )
        ToggleRow(
            title = "Journal reflection",
            description = "Show a \"Generate weekly reflection\" option on the Summaries screen. Sends your full journal entry text for the selected week to Anthropic.",
            checked = prefs.journalReflection.enabled,
            enabled = !state.saving,
            onCheckedChange = { checked ->
                if (checked) confirmingJournalReflection = true
                else viewModel.update { copy(journalReflection = AssistantFeatureToggle(false)) }
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        PickerRow(
            label = "Tone",
            value = TONE_OPTIONS.firstOrNull { it.first == prefs.tone }?.second ?: prefs.tone,
            options = TONE_OPTIONS,
            onSelect = { value -> viewModel.update { copy(tone = value) } },
        )
        PickerRow(
            label = "Model",
            value = MODEL_OPTIONS.firstOrNull { it.first == prefs.modelOverride }?.second ?: "Instance default",
            options = MODEL_OPTIONS,
            onSelect = { value -> viewModel.update { copy(modelOverride = value) } },
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (confirmingJournalReflection) {
        AlertDialog(
            onDismissRequest = { confirmingJournalReflection = false },
            title = { Text("Enable journal reflection?") },
            text = {
                Text(
                    "Journal reflection sends your journal entries' full text for the selected " +
                        "week to Anthropic, not just a summary. This is different from Kairon's " +
                        "other assistant features, which only send task and project details.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { copy(journalReflection = AssistantFeatureToggle(true)) }
                    confirmingJournalReflection = false
                }) { Text("Enable journal reflection") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingJournalReflection = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> PickerRow(label: String, value: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (optionValue, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = { expanded = false; onSelect(optionValue) },
                    )
                }
            }
        }
    }
}
