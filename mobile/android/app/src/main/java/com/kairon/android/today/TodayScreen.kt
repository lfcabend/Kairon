package com.kairon.android.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Today") }) }) { padding ->
        if (state.loading) {
            Row(modifier = Modifier.fillMaxSize().padding(padding), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.error != null) {
                item { Text(state.error ?: "", modifier = Modifier.padding(16.dp)) }
            }

            item { Text("Todos", modifier = Modifier.padding(16.dp)) }
            items(state.todos) { todo ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = todo.status == "DONE", onCheckedChange = null, enabled = false)
                    Text(todo.title ?: "")
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { Text("Due from projects", modifier = Modifier.padding(16.dp)) }
            items(state.dueProjectTasks) { task ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(task.name ?: "", modifier = Modifier.weight(1f))
                    val taskId = task.id
                    if (taskId != null) {
                        val added = state.addedTaskIds.contains(taskId)
                        TextButton(onClick = { viewModel.promote(taskId) }, enabled = !added) {
                            Text(if (added) "Added" else "Add to today")
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Text(
                    if (state.hasJournalEntry) "Journal entry added today" else "No journal entry yet today",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
