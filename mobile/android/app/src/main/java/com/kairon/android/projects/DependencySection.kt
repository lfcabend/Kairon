package com.kairon.android.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairon.android.client.model.TaskDependencyResponse
import com.kairon.android.core.data.ProjectTaskEntity
import java.util.UUID

/**
 * Lists this task's predecessors (with a remove action) and an "Add" picker
 * over the project's other tasks — mirrors the web `TaskDependencySection`.
 * A `400`/`409` from [onAdd] (self/cross-project/cycle/duplicate, M5 D3/D4)
 * surfaces as the screen's own inline error, same as any other save failure.
 */
@Composable
fun DependencySection(
    dependencies: List<TaskDependencyResponse>,
    loading: Boolean,
    candidates: List<ProjectTaskEntity>,
    onAdd: (UUID) -> Unit,
    onRemove: (TaskDependencyResponse) -> Unit,
) {
    var addMenuOpen by remember { mutableStateOf(false) }
    val availableCandidates = candidates.filter { candidate -> dependencies.none { it.predecessorId == candidate.id } }

    Column {
        Text("Depends on", style = MaterialTheme.typography.titleSmall)

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else if (dependencies.isEmpty()) {
            Text("No dependencies", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
        } else {
            dependencies.forEach { dependency ->
                val name = candidates.firstOrNull { it.id == dependency.predecessorId }?.name ?: "Unknown task"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(dependency) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove dependency")
                    }
                }
            }
        }

        Box {
            TextButton(onClick = { addMenuOpen = true }, enabled = availableCandidates.isNotEmpty()) { Text("+ Add dependency") }
            DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                availableCandidates.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(candidate.name) },
                        onClick = {
                            addMenuOpen = false
                            onAdd(candidate.id)
                        },
                    )
                }
            }
        }
    }
}
