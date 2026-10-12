package com.kairon.android.assistant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairon.android.client.model.AssistantSuggestedProjectView
import com.kairon.android.client.model.PlannedTaskView

/**
 * Read-only project header + task tree with milestone markers and per-task
 * checkboxes to exclude, with full parent-to-children cascade (D3/D9) —
 * mirrors web's `ProjectPlanReview`. No field editing; adjustments happen
 * via the normal task-edit screen after creation.
 */
@Composable
fun ProjectPlanReviewScreen(plan: AssistantSuggestedProjectView, excludedKeys: Set<String>, onToggle: (String) -> Unit) {
    val tasks = plan.tasks.orEmpty()
    val effectiveExcluded = effectiveExcludedTaskKeys(tasks, excludedKeys)
    val topLevel = tasks.filter { it.parentKey == null }
    val childrenByParentKey = tasks.filter { it.parentKey != null }.groupBy { it.parentKey }

    Column {
        Text(plan.name ?: "", style = MaterialTheme.typography.titleMedium)
        plan.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            buildString {
                plan.startDate?.let { append(it) }
                plan.endDate?.let { append(" – $it") }
                plan.propertySize?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        plan.categoryName?.let { name ->
            Text(
                if (plan.categoryId != null) name else "New category: $name",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LazyColumn(modifier = Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
            topLevel.forEach { task ->
                item {
                    PlannedTaskRow(
                        task = task,
                        checked = task.key !in excludedKeys,
                        disabled = false,
                        onToggle = { task.key?.let(onToggle) },
                    )
                }
                items(childrenByParentKey[task.key].orEmpty(), key = { it.key ?: it.hashCode().toString() }) { child ->
                    Row(modifier = Modifier.padding(start = 24.dp)) {
                        PlannedTaskRow(
                            task = child,
                            checked = child.key !in effectiveExcluded,
                            disabled = task.key in excludedKeys,
                            onToggle = { child.key?.let(onToggle) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedTaskRow(task: PlannedTaskView, checked: Boolean, disabled: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Checkbox(checked = checked, enabled = !disabled, onCheckedChange = { onToggle() })
        Column {
            Text(
                buildString {
                    if (task.isMilestone == true) append("◆ ")
                    append(task.name ?: "")
                },
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (task.plannedStart != null || task.plannedEnd != null) {
                Text(
                    buildString {
                        task.plannedStart?.let { append(it) }
                        task.plannedEnd?.takeIf { it != task.plannedStart }?.let { append(" – $it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
