package com.kairon.android.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairon.android.core.data.ProjectTaskEntity
import java.util.UUID

internal data class TreeRow(val task: ProjectTaskEntity, val depth: Int, val hasChildren: Boolean)

/** Depth-first parent-before-children order, reused by [GanttTab] for consistent row ordering. */
internal fun buildTreeOrder(tasks: List<ProjectTaskEntity>, collapsed: Set<UUID> = emptySet()): List<TreeRow> {
    val byParent = tasks.groupBy { it.parentTaskId }
    val rows = mutableListOf<TreeRow>()
    fun visit(parentId: UUID?, depth: Int) {
        byParent[parentId].orEmpty().sortedBy { it.position }.forEach { task ->
            val hasChildren = byParent[task.id].orEmpty().isNotEmpty()
            rows += TreeRow(task, depth, hasChildren)
            if (hasChildren && task.id !in collapsed) {
                visit(task.id, depth + 1)
            }
        }
    }
    visit(null, 0)
    return rows
}

/**
 * Flat rows with indentation for subtasks; tap-based "Move up/down" (D4,
 * within the same `parentTaskId` sibling group) instead of drag-to-reorder.
 */
@Composable
fun TaskTreeTab(
    tasks: List<ProjectTaskEntity>,
    onMoveUp: (ProjectTaskEntity) -> Unit,
    onMoveDown: (ProjectTaskEntity) -> Unit,
    onDelete: (ProjectTaskEntity) -> Unit,
    onClick: (ProjectTaskEntity) -> Unit,
    onAddSubtask: (ProjectTaskEntity) -> Unit,
) {
    var collapsed by remember { mutableStateOf(setOf<UUID>()) }
    val rows = remember(tasks, collapsed) { buildTreeOrder(tasks, collapsed) }

    LazyColumn {
        items(rows, key = { it.task.id.toString() }) { row ->
            TreeTaskRow(
                row = row,
                isCollapsed = row.task.id in collapsed,
                onToggleCollapse = {
                    collapsed = if (row.task.id in collapsed) collapsed - row.task.id else collapsed + row.task.id
                },
                onMoveUp = { onMoveUp(row.task) },
                onMoveDown = { onMoveDown(row.task) },
                onDelete = { onDelete(row.task) },
                onClick = { onClick(row.task) },
                onAddSubtask = { onAddSubtask(row.task) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun TreeTaskRow(
    row: TreeRow,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onAddSubtask: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val task = row.task
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width((row.depth * 20).dp))
        if (row.hasChildren) {
            IconButton(onClick = onToggleCollapse) { Text(if (isCollapsed) "▶" else "▼") }
        } else {
            Spacer(modifier = Modifier.width(40.dp))
        }
        Text(
            (if (task.isMilestone) "◆ " else "") + task.name,
            modifier = Modifier.weight(1f),
            textDecoration = if (task.status == "DONE") TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(task.status, style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = { menuOpen = true }) { Text("⋮") }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Move up") }, onClick = { menuOpen = false; onMoveUp() })
            DropdownMenuItem(text = { Text("Move down") }, onClick = { menuOpen = false; onMoveDown() })
            DropdownMenuItem(text = { Text("Add subtask") }, onClick = { menuOpen = false; onAddSubtask() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
        }
    }
}
