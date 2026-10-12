package com.kairon.android.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairon.android.client.model.ProjectCategoryResponse
import java.util.UUID

/**
 * Add/rename/delete project categories — a dialog rather than a full
 * screen (Q3: small, infrequent management action, not worth a 5th
 * bottom-nav-adjacent destination). Delete is a real hard delete (M4 D14) —
 * referencing projects become uncategorized.
 */
@Composable
fun CategoryManagerDialog(
    categories: List<ProjectCategoryResponse>,
    onDismiss: () -> Unit,
    onCreate: (name: String, color: String?) -> Unit,
    onRename: (ProjectCategoryResponse, String, String?) -> Unit,
    onDelete: (ProjectCategoryResponse) -> Unit,
    onReorder: (List<UUID>) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ProjectCategoryResponse?>(null) }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${category.name}\"?") },
            text = { Text("Projects in this category become uncategorized.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(category)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categories") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(categories, key = { it.id.toString() }) { category ->
                        val index = categories.indexOf(category)
                        CategoryEditRow(
                            category = category,
                            onRename = { name -> onRename(category, name, category.color) },
                            onDelete = { pendingDelete = category },
                            onMoveUp = { onReorder(swapped(categories, index, index - 1)) }.takeIf { index > 0 },
                            onMoveDown = { onReorder(swapped(categories, index, index + 1)) }.takeIf { index < categories.lastIndex },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New category") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            onCreate(newName.trim(), null)
                            newName = ""
                        }
                    }) { Text("Add") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun CategoryEditRow(
    category: ProjectCategoryResponse,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    var name by remember(category.id) { mutableStateOf(category.name ?: "") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) { Text("▲") }
        IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) { Text("▼") }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (name != category.name) {
            TextButton(onClick = { onRename(name) }) { Text("Save") }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete category")
        }
    }
}

private fun swapped(categories: List<ProjectCategoryResponse>, from: Int, to: Int): List<UUID> =
    categories.map { it.id!! }.toMutableList().apply { add(to, removeAt(from)) }
