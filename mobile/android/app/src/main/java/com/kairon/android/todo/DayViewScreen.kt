package com.kairon.android.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.kairon.android.core.data.TodoItemEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayViewScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var quickAddText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = viewModel::goToPreviousDay) { Text("<") }
                    Text(state.day.toString())
                    IconButton(onClick = viewModel::goToNextDay) { Text(">") }
                }
            })
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.error != null) {
                item { Text(state.error ?: "", modifier = Modifier.padding(16.dp)) }
            }

            state.rolloverCandidateCount?.let { count ->
                item {
                    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$count item(s) to roll over")
                            TextButton(onClick = viewModel::rollover) { Text("Roll over") }
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    OutlinedTextField(
                        value = quickAddText,
                        onValueChange = { quickAddText = it },
                        label = { Text("Quick add") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = {
                    viewModel.quickAdd(quickAddText)
                    quickAddText = ""
                }) {
                    Text("Add")
                }
            }

            items(state.items, key = { it.id }) { item ->
                TodoRow(
                    item = item,
                    onToggle = { viewModel.toggleComplete(item) },
                    onDelete = { viewModel.delete(item) },
                    onMoveUp = { viewModel.move(item, -1) },
                    onMoveDown = { viewModel.move(item, 1) },
                )
            }
        }
    }
}

@Composable
private fun TodoRow(
    item: TodoItemEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.status == "DONE", onCheckedChange = { onToggle() })
        Text(item.title, modifier = Modifier.weight(1f))
        IconButton(onClick = onMoveUp) { Text("↑") }
        IconButton(onClick = onMoveDown) { Text("↓") }
        IconButton(onClick = onDelete) { Text("✕") }
    }
}
