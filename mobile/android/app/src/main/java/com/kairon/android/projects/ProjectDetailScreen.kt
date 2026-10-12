package com.kairon.android.projects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.assistant.ProjectEditEntryPoint
import java.util.UUID

private val TABS = listOf("Tree", "Board", "Gantt")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    onEditProject: (UUID) -> Unit,
    onEditTask: (UUID, UUID) -> Unit,
    onNewTask: (UUID, UUID?) -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.project?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ProjectEditEntryPoint(onApplied = viewModel::refresh)
                    IconButton(onClick = { onEditProject(viewModel.projectId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit project")
                    }
                },
            )
        },
        floatingActionButton = {
            if (selectedTab != 2) {
                FloatingActionButton(onClick = { onNewTask(viewModel.projectId, null) }) {
                    Icon(Icons.Default.Add, contentDescription = "New task")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab) {
                    TABS.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label) },
                        )
                    }
                }

                if (state.error != null) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                }

                when (selectedTab) {
                    0 -> TaskTreeTab(
                        tasks = state.tasks,
                        onMoveUp = { viewModel.moveTask(it, -1) },
                        onMoveDown = { viewModel.moveTask(it, 1) },
                        onDelete = viewModel::deleteTask,
                        onClick = { task -> onEditTask(viewModel.projectId, task.id) },
                        onAddSubtask = { parent -> onNewTask(viewModel.projectId, parent.id) },
                    )
                    1 -> TaskBoardTab(
                        tasks = state.tasks,
                        onSetStatus = viewModel::setStatus,
                        onClick = { task -> onEditTask(viewModel.projectId, task.id) },
                    )
                    2 -> GanttTab(
                        tasks = state.tasks,
                        dependencies = state.dependencies,
                        onClick = { task -> onEditTask(viewModel.projectId, task.id) },
                    )
                }
            }
        }
    }
}
