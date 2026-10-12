package com.kairon.android.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairon.android.client.model.AssistantSuggestedTaskView

/** The review-and-accept list for one run's todo suggestions — mirrors web's `SuggestedTaskList`. */
@Composable
fun SuggestedTaskList(
    suggestions: List<AssistantSuggestedTaskView>,
    onAccept: (AssistantSuggestedTaskView) -> Unit,
    onDismiss: (AssistantSuggestedTaskView) -> Unit,
) {
    if (suggestions.isEmpty()) {
        Text("No suggestions right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
        items(suggestions, key = { it.id.toString() }) { suggestion ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(suggestion.title ?: "", style = MaterialTheme.typography.bodyLarge)
                    suggestion.rationale?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text(
                            buildString {
                                append(suggestion.suggestedForDay?.toString() ?: "")
                                suggestion.estimateMinutes?.let { append(" · ~$it min") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        TextButton(onClick = { onDismiss(suggestion) }) { Text("Dismiss") }
                        TextButton(onClick = { onAccept(suggestion) }) { Text("Accept") }
                    }
                }
            }
        }
    }
}
