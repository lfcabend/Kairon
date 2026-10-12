package com.kairon.android.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.client.model.ProjectCategoryResponse
import com.kairon.android.client.model.ProjectResponse
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onOpenProject: (UUID) -> Unit,
    onNewProject: () -> Unit,
    viewModel: ProjectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }

    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = state.categories,
            onDismiss = { showCategoryManager = false },
            onCreate = viewModel::createCategory,
            onRename = viewModel::renameCategory,
            onDelete = viewModel::deleteCategory,
            onReorder = viewModel::reorderCategories,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) { Text("Sort") }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("By category") },
                                onClick = { sortMenuOpen = false; viewModel.setSortMode(ProjectSortMode.CATEGORY) },
                            )
                            DropdownMenuItem(
                                text = { Text("By priority") },
                                onClick = { sortMenuOpen = false; viewModel.setSortMode(ProjectSortMode.PRIORITY) },
                            )
                        }
                    }
                    IconButton(onClick = { showCategoryManager = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage categories")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewProject) {
                Icon(Icons.Default.Add, contentDescription = "New project")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.error != null) {
                Text(state.error ?: "", modifier = Modifier.padding(16.dp))
            }

            val filteredProjects = state.filteredProjects

            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = state.filterText,
                    onValueChange = viewModel::setFilterText,
                    label = { Text("Filter projects") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )

                when (state.sortMode) {
                    ProjectSortMode.PRIORITY -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredProjects, key = { it.id.toString() }) { project ->
                            ProjectRow(project = project, onClick = { project.id?.let(onOpenProject) })
                        }
                    }
                    ProjectSortMode.CATEGORY -> {
                        val byCategory = filteredProjects.groupBy { it.categoryId }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            state.categories.forEach { category ->
                                val projects = byCategory[category.id].orEmpty()
                                if (projects.isNotEmpty()) {
                                    item { CategoryHeader(category.name ?: "") }
                                    items(projects, key = { it.id.toString() }) { project ->
                                        ProjectRow(project = project, onClick = { project.id?.let(onOpenProject) })
                                    }
                                }
                            }
                            val uncategorized = byCategory[null].orEmpty()
                            if (uncategorized.isNotEmpty()) {
                                item { CategoryHeader("Uncategorized") }
                                items(uncategorized, key = { it.id.toString() }) { project ->
                                    ProjectRow(project = project, onClick = { project.id?.let(onOpenProject) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ProjectRow(project: ProjectResponse, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(project.color)),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(project.name ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                project.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(project.status ?: "", style = MaterialTheme.typography.labelSmall)
        }
    }
}

internal fun parseHexColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex ?: "#6366f1")) }.getOrDefault(Color.Gray)
