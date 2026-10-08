package com.kairon.android.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.kairon.android.todo.TodoViewModel
import com.kairon.android.todo.todoListItems

/**
 * The app's landing screen (no date-nav — always "today"). Embeds its own
 * [TodoViewModel] instance (defaults to today's date) purely for the todo
 * section, reusing [todoListItems] so this list gets the exact same
 * interactive features as `/day` — the same thing the web app's `TodayTasks`
 * does by reusing `DayView`'s own `TodoList`/`DaySummary`/`QuickAdd`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    todoViewModel: TodoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val todoState by todoViewModel.state.collectAsState()
    var showCancelled by remember { mutableStateOf(false) }
    var quickAddText by remember { mutableStateOf("") }
    val addedTaskIds = todoState.items.mapNotNull { it.sourceProjectTaskId }.toSet()

    Scaffold(topBar = { TopAppBar(title = { Text("Today") }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing || todoState.syncing,
            onRefresh = {
                viewModel.refresh(isPullToRefresh = true)
                todoViewModel.refresh()
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.loading) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
                return@PullToRefreshBox
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.error != null) {
                    item { Text(state.error ?: "", modifier = Modifier.padding(16.dp)) }
                }

                item { Text("Today's tasks", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                todoListItems(
                    viewModel = todoViewModel,
                    state = todoState,
                    showCancelled = showCancelled,
                    onToggleShowCancelled = { showCancelled = !showCancelled },
                    quickAddText = quickAddText,
                    onQuickAddTextChange = { quickAddText = it },
                    onQuickAddSubmit = {
                        todoViewModel.quickAdd(quickAddText)
                        quickAddText = ""
                    },
                )

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
                            val added = addedTaskIds.contains(taskId)
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
}
