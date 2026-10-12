package com.kairon.android.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairon.android.core.data.ProjectTaskEntity

private val COLUMN_LABELS = mapOf(
    "TODO" to "To do",
    "IN_PROGRESS" to "In progress",
    "BLOCKED" to "Blocked",
    "DONE" to "Done",
)

/** Four status columns; tapping a card's status chip opens a picker (D4) instead of drag. */
@Composable
fun TaskBoardTab(
    tasks: List<ProjectTaskEntity>,
    onSetStatus: (ProjectTaskEntity, String) -> Unit,
    onClick: (ProjectTaskEntity) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
    ) {
        TASK_STATUSES.forEach { status ->
            val columnTasks = tasks.filter { it.status == status }.sortedBy { it.position }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    "${COLUMN_LABELS[status]} (${columnTasks.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(columnTasks, key = { it.id.toString() }) { task ->
                        BoardCard(task = task, onSetStatus = onSetStatus, onClick = { onClick(task) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCard(
    task: ProjectTaskEntity,
    onSetStatus: (ProjectTaskEntity, String) -> Unit,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(task.name, style = MaterialTheme.typography.bodyMedium)
            Box {
                AssistChip(
                    onClick = { menuOpen = true },
                    label = { Text(COLUMN_LABELS[task.status] ?: task.status) },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    TASK_STATUSES.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(COLUMN_LABELS[status] ?: status) },
                            onClick = { menuOpen = false; onSetStatus(task, status) },
                        )
                    }
                }
            }
        }
    }
}
