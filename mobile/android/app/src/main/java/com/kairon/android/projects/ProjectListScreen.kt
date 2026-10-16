package com.kairon.android.projects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.kairon.android.core.network.AppError
import com.kairon.android.core.network.userMessage
import com.kairon.android.core.ui.InlineError
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.assistant.ProjectGenerationEntryPoint
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
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

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
                title = {
                    if (searchActive) {
                        TextField(
                            value = state.filterText,
                            onValueChange = viewModel::setFilterText,
                            placeholder = { Text("Filter projects") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                        )
                    } else {
                        Text("Projects")
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.setFilterText("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close filter")
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Filter projects")
                        }
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
            state.error?.let { appError ->
                InlineError(
                    appError.userMessage(),
                    actionLabel = if (appError is AppError.Conflict) "Reload" else null,
                    onAction = if (appError is AppError.Conflict) viewModel::reloadCategories else null,
                    modifier = Modifier.padding(16.dp),
                )
            }

            val filteredProjects = state.filteredProjects

            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ProjectGenerationEntryPoint(onCreated = onOpenProject)
                }
                if (searchActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterDropdownChip(
                            options = PROJECT_STATUS_OPTIONS,
                            selected = state.statusFilter,
                            onSelect = viewModel::setStatusFilter,
                        )
                        FilterDropdownChip(
                            options = PROJECT_SIZE_OPTIONS,
                            selected = state.sizeFilter,
                            onSelect = viewModel::setSizeFilter,
                        )
                    }
                }
                when (state.sortMode) {
                    ProjectSortMode.PRIORITY -> {
                        // Drag-reorder only makes sense against the server's full priority
                        // order — disabled while the quick filter narrows the list, same
                        // rule the web app's own drag reorder follows (D21).
                        val reorderable = state.filterText.isBlank()
                        if (!reorderable) {
                            Text(
                                "Clear the filter to reorder by priority.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        PriorityProjectList(
                            projects = filteredProjects,
                            reorderable = reorderable,
                            onOpenProject = onOpenProject,
                            onChangeStatus = { project, status -> viewModel.changeStatus(project, status) },
                            onReorder = viewModel::reorderProjects,
                        )
                    }
                    ProjectSortMode.CATEGORY -> {
                        val byCategory = filteredProjects.groupBy { it.categoryId }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            state.categories.forEach { category ->
                                val projects = byCategory[category.id].orEmpty()
                                if (projects.isNotEmpty()) {
                                    item { CategoryHeader(category.name ?: "") }
                                    items(projects, key = { it.id.toString() }) { project ->
                                        ProjectRow(
                                            project = project,
                                            onClick = { project.id?.let(onOpenProject) },
                                            onChangeStatus = { status -> viewModel.changeStatus(project, status) },
                                        )
                                    }
                                }
                            }
                            val uncategorized = byCategory[null].orEmpty()
                            if (uncategorized.isNotEmpty()) {
                                item { CategoryHeader("Uncategorized") }
                                items(uncategorized, key = { it.id.toString() }) { project ->
                                    ProjectRow(
                                        project = project,
                                        onClick = { project.id?.let(onOpenProject) },
                                        onChangeStatus = { status -> viewModel.changeStatus(project, status) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A Status/Size chip — shows the current selection (or its "any" label) and opens a picker menu on tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdownChip(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: options.first().second
    Box {
        AssistChip(onClick = { expanded = true }, label = { Text(label) })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
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
private fun ProjectRow(
    project: ProjectResponse,
    onClick: () -> Unit,
    onChangeStatus: (String) -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dragHandle != null) {
                Box(modifier = Modifier.padding(end = 8.dp)) { dragHandle() }
            }
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
            project.propertySize?.let { ProjectSizeBadge(size = it, modifier = Modifier.padding(end = 8.dp)) }
            ProjectStatusChip(status = project.status, onSelect = onChangeStatus)
        }
    }
}

/** Small, non-interactive outline badge for `project.size` (mirrors the web app's own size `Badge`, M4 D16). Hidden entirely for an unsized project. */
@Composable
private fun ProjectSizeBadge(size: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            size,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Status chip as a tap-opened picker (same idiom as `TaskBoardTab`'s task-status chip) so status can change without opening the edit screen. */
@Composable
private fun ProjectStatusChip(status: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(PROJECT_STATUS_LABELS.firstOrNull { it.first == status }?.second ?: status ?: "") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PROJECT_STATUS_LABELS.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        if (value != status) onSelect(value)
                    },
                )
            }
        }
    }
}

internal fun parseHexColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex ?: "#6366f1")) }.getOrDefault(Color.Gray)

/**
 * "By priority" drag-reorder (mirrors the web app's own drag-based "Sort by:
 * Priority", D19). Hand-rolled on top of [LazyListState]'s own layout info rather
 * than a third-party reorder library — no auto-scroll-while-dragging, but this
 * app's project lists are short enough that it isn't missed, and it keeps this
 * the only drag interaction in the app from pulling in a new dependency.
 *
 * The drag gesture lives on a dedicated handle glyph (not the whole row), so it
 * never fights the row's own tap-to-open or the status chip's own tap-to-open
 * dropdown — same reasoning the web app's own `GripVertical` drag handle button
 * follows (`ProjectCard.tsx`).
 */
@Composable
private fun PriorityProjectList(
    projects: List<ProjectResponse>,
    reorderable: Boolean,
    onOpenProject: (UUID) -> Unit,
    onChangeStatus: (ProjectResponse, String) -> Unit,
    onReorder: (List<UUID>) -> Unit,
) {
    // Local working copy so rows can shuffle live while dragging, ahead of the
    // server round trip `onReorder` triggers on drop; resynced whenever the
    // upstream list changes for any other reason (refresh, filter toggle, etc.).
    var items by remember { mutableStateOf(projects) }
    LaunchedEffect(projects) { items = projects }

    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<UUID?>(null) }
    var draggingInitialOffset by remember { mutableFloatStateOf(0f) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }

    fun layoutInfoFor(id: UUID?) =
        id?.let { target -> listState.layoutInfo.visibleItemsInfo.firstOrNull { items.getOrNull(it.index)?.id == target } }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, project -> project.id.toString() }) { _, project ->
            val isDragging = project.id == draggingId
            val displacement = if (isDragging) {
                val info = layoutInfoFor(draggingId)
                if (info != null) draggingInitialOffset + draggedDistance - info.offset else 0f
            } else {
                0f
            }

            ProjectRow(
                project = project,
                onClick = { project.id?.let(onOpenProject) },
                onChangeStatus = { status -> onChangeStatus(project, status) },
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = displacement
                        shadowElevation = if (isDragging) 8f else 0f
                    }
                    .let { if (isDragging) it else it.animateItem() },
                dragHandle = if (!reorderable) null else {
                    {
                        Text(
                            "⋮⋮",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.pointerInput(project.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val info = layoutInfoFor(project.id)
                                        draggingId = project.id
                                        draggedDistance = 0f
                                        draggingInitialOffset = info?.offset?.toFloat() ?: 0f
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        draggedDistance = 0f
                                        onReorder(items.mapNotNull { it.id })
                                    },
                                    onDragCancel = {
                                        items = projects
                                        draggingId = null
                                        draggedDistance = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        draggedDistance += dragAmount.y
                                        val draggingIndex = items.indexOfFirst { it.id == draggingId }
                                        val info = layoutInfoFor(draggingId)
                                        if (draggingIndex == -1 || info == null) return@detectDragGesturesAfterLongPress
                                        val start = draggingInitialOffset + draggedDistance
                                        val end = start + info.size
                                        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
                                            candidate.index != draggingIndex &&
                                                start <= candidate.offset + candidate.size &&
                                                end >= candidate.offset
                                        }
                                        if (target != null) {
                                            items = items.toMutableList().apply { add(target.index, removeAt(draggingIndex)) }
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }
}
