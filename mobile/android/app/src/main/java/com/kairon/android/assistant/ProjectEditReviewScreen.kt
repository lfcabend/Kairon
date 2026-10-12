package com.kairon.android.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairon.android.client.model.AssistantSuggestedProjectEditView
import com.kairon.android.client.model.DependencyOperationView
import com.kairon.android.client.model.TaskDependencyResponse
import com.kairon.android.client.model.TaskOperationView
import com.kairon.android.core.data.ProjectTaskEntity

/**
 * Read-only diff grouped by change type, with per-change exclude checkboxes
 * and full cascade from an excluded new task to anything else referencing
 * its key (D3/D9) — mirrors web's `ProjectEditReview`. No field editing.
 */
@Composable
fun ProjectEditReviewScreen(
    diff: AssistantSuggestedProjectEditView,
    currentTasks: List<ProjectTaskEntity>,
    currentDependencies: List<TaskDependencyResponse>,
    excludedKeys: Set<String>,
    onToggle: (String) -> Unit,
) {
    val taskOperations = diff.taskOperations.orEmpty()
    val dependencyOperations = diff.dependencyOperations.orEmpty()
    val reorderOperations = diff.reorderOperations.orEmpty()
    val excludedNewKeys = excludedNewTaskKeys(taskOperations, excludedKeys)

    val taskById = currentTasks.associateBy { it.id.toString() }
    val addedNameByKey = taskOperations.filter { it.op == "ADD" && it.key != null }.associate { it.key!! to (it.name ?: "") }
    fun refLabel(ref: String?): String {
        if (ref == null) return "top level"
        return taskById[ref]?.name ?: addedNameByKey[ref] ?: ref
    }

    val hasChanges = diff.projectChanges != null || taskOperations.isNotEmpty() || dependencyOperations.isNotEmpty() || reorderOperations.isNotEmpty()

    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
        if (!hasChanges) {
            item { Text("The assistant didn't propose any changes.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        diff.projectChanges?.let { changes ->
            item { Text("Project", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Checkbox(checked = "project" !in excludedKeys, onCheckedChange = { onToggle("project") })
                    Column {
                        changes.name?.let { Text("Rename to \"$it\"") }
                        changes.categoryName?.let { name ->
                            Text(
                                if (changes.categoryId != null) name else "New category: $name",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (taskOperations.isNotEmpty()) {
            item { Text("Tasks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(taskOperations) { index, op -> TaskOperationRow(op, index, taskById, excludedKeys, excludedNewKeys, onToggle) }
        }

        if (dependencyOperations.isNotEmpty()) {
            item { Text("Dependencies", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            itemsIndexed(dependencyOperations) { index, op ->
                DependencyOperationRow(op, index, currentDependencies, excludedKeys, excludedNewKeys, ::refLabel, onToggle)
            }
        }

        if (reorderOperations.isNotEmpty()) {
            item { Text("Reordering", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            items(reorderOperations) { op ->
                Text(
                    "Reorder under ${refLabel(op.parentRef)}: ${op.orderedRefs.orEmpty().joinToString(", ") { refLabel(it) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskOperationRow(
    op: TaskOperationView,
    index: Int,
    taskById: Map<String, ProjectTaskEntity>,
    excludedKeys: Set<String>,
    excludedNewKeys: Set<String>,
    onToggle: (String) -> Unit,
) {
    val excluded = isTaskOperationExcluded(op, index, excludedKeys, excludedNewKeys)
    val disabled = isTaskOperationDisabled(op, excludedNewKeys)
    val existingName = op.existingTaskId?.toString()?.let { taskById[it] }?.name
    val label = when (op.op) {
        "ADD" -> op.name ?: ""
        "UPDATE" -> "${existingName ?: "Task"} → ${op.name}"
        else -> existingName ?: "Task"
    }
    val prefix = when (op.op) { "ADD" -> "+ "; "REMOVE" -> "– "; else -> "~ " }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Checkbox(checked = !excluded, enabled = !disabled, onCheckedChange = { onToggle(taskOperationKey(index)) })
        Column {
            Text(
                buildString {
                    append(prefix)
                    if (op.isMilestone == true) append("◆ ")
                    append(label)
                },
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (op.plannedStart != null || op.plannedEnd != null) {
                Text(
                    buildString {
                        op.plannedStart?.let { append(it) }
                        op.plannedEnd?.takeIf { it != op.plannedStart }?.let { append(" – $it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DependencyOperationRow(
    op: DependencyOperationView,
    index: Int,
    currentDependencies: List<TaskDependencyResponse>,
    excludedKeys: Set<String>,
    excludedNewKeys: Set<String>,
    refLabel: (String?) -> String,
    onToggle: (String) -> Unit,
) {
    val disabled = isDependencyOperationDisabled(op, excludedNewKeys)
    val label = if (op.op == "ADD") {
        "${refLabel(op.predecessorRef)} → ${refLabel(op.successorRef)} (${op.type ?: "FS"})"
    } else {
        val edge = currentDependencies.firstOrNull { it.id == op.existingDependencyId }
        if (edge != null) "${refLabel(edge.predecessorId?.toString())} → ${refLabel(edge.successorId?.toString())}" else "an existing dependency"
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Checkbox(
            checked = dependencyOperationKey(index) !in excludedKeys && !disabled,
            enabled = !disabled,
            onCheckedChange = { onToggle(dependencyOperationKey(index)) },
        )
        Text(
            (if (op.op == "ADD") "+ " else "– ") + label,
            color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}
