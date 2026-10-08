package com.kairon.android.todo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.core.data.TodoItemEntity
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val PRIORITY_COLOR = listOf(
    Color.Transparent,
    Color(0xFF0EA5E9), // Low
    Color(0xFFF59E0B), // Medium
    Color(0xFFEF4444), // High
)

// Matches the DatePickerDialog header's own style (e.g. "Oct 8, 2026").
private val DATE_NAV_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayViewScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var quickAddText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCancelled by remember { mutableStateOf(false) }

    val openItems = state.items.filter { it.status == "OPEN" }
    val doneItems = state.items.filter { it.status == "DONE" }
    val cancelledItems = state.items.filter { it.status == "CANCELLED" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = viewModel::goToPreviousDay) { Text("<") }
                        Text(
                            state.day.format(DATE_NAV_FORMATTER),
                            modifier = Modifier.clickable { showDatePicker = true },
                        )
                        IconButton(onClick = viewModel::goToNextDay) { Text(">") }
                    }
                },
                actions = {
                    if (state.day != state.today) {
                        TextButton(onClick = viewModel::goToToday) { Text("Today") }
                    }
                },
            )
        },
    ) { padding ->
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = state.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.goToDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

        PullToRefreshBox(
            isRefreshing = state.syncing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "${openItems.size} open · ${doneItems.size} done",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

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
                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        OutlinedTextField(
                            value = quickAddText,
                            onValueChange = { quickAddText = it },
                            label = { Text("Quick add") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = {
                            viewModel.quickAdd(quickAddText)
                            quickAddText = ""
                        }) {
                            Text("Add")
                        }
                    }
                }

                item {
                    OpenItemsSection(
                        fullDayItems = state.items,
                        openItems = openItems,
                        onReorder = viewModel::reorderOpen,
                        onToggleComplete = viewModel::toggleComplete,
                        onRename = viewModel::rename,
                        onEditNotes = viewModel::setNotes,
                        onCancel = viewModel::cancel,
                        onDelete = viewModel::delete,
                    )
                }

                if (doneItems.isNotEmpty()) {
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    items(doneItems, key = { it.id }) { item ->
                        TodoRow(
                            item = item,
                            draggable = false,
                            onToggleComplete = { viewModel.toggleComplete(item) },
                            onRename = { viewModel.rename(item, it) },
                            onEditNotes = { viewModel.setNotes(item, it) },
                            onCancel = { viewModel.cancel(item) },
                            onDelete = { viewModel.delete(item) },
                        )
                    }
                }

                if (cancelledItems.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showCancelled = !showCancelled }) {
                            Text(
                                if (showCancelled) "Hide ${cancelledItems.size} cancelled"
                                else "Show ${cancelledItems.size} cancelled",
                            )
                        }
                    }
                    if (showCancelled) {
                        items(cancelledItems, key = { it.id }) { item ->
                            TodoRow(
                                item = item,
                                draggable = false,
                                onToggleComplete = { viewModel.toggleComplete(item) },
                                onRename = { viewModel.rename(item, it) },
                                onEditNotes = { viewModel.setNotes(item, it) },
                                onCancel = { viewModel.cancel(item) },
                                onDelete = { viewModel.delete(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The open-item list, as a single non-lazy [Column] (daily lists are small —
 * no virtualization needed) so press-and-hold drag reorder can swap entries
 * by measured row height without fighting [LazyColumn]'s own virtualization.
 * [fullDayItems] (every status) is threaded through to [onReorder] because
 * the backend's `:reorder` endpoint requires the full day's ids, not just
 * the open ones — see [TodoViewModel.reorderOpen].
 */
@Composable
private fun OpenItemsSection(
    fullDayItems: List<TodoItemEntity>,
    openItems: List<TodoItemEntity>,
    onReorder: (List<TodoItemEntity>, List<UUID>) -> Unit,
    onToggleComplete: (TodoItemEntity) -> Unit,
    onRename: (TodoItemEntity, String) -> Unit,
    onEditNotes: (TodoItemEntity, String) -> Unit,
    onCancel: (TodoItemEntity) -> Unit,
    onDelete: (TodoItemEntity) -> Unit,
) {
    var localOrder by remember(openItems) { mutableStateOf(openItems) }
    val rowHeights = remember { mutableStateMapOf<UUID, Int>() }
    var draggedId by remember { mutableStateOf<UUID?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Keyed explicitly by id (unlike LazyColumn, a plain Column has no
        // built-in notion of item identity): without this, Compose reuses
        // each slot *positionally*, so swapping two entries during a drag
        // would hand the in-flight drag gesture's pointerInput coroutine off
        // to whatever item now lands in that slot instead of following the
        // item actually being dragged.
        localOrder.forEach { item ->
            key(item.id) {
                val isDragging = draggedId == item.id
                TodoRow(
                    item = item,
                    draggable = true,
                    rowModifier = Modifier
                        .onGloballyPositioned { rowHeights[item.id] = it.size.height }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f },
                    dragHandleModifier = Modifier.pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedId = item.id
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                draggedId = null
                                dragOffset = 0f
                                onReorder(fullDayItems, localOrder.map { it.id })
                            },
                            onDragCancel = {
                                draggedId = null
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val currentIndex = localOrder.indexOfFirst { it.id == item.id }
                                if (currentIndex == -1) return@detectDragGesturesAfterLongPress
                                dragOffset += dragAmount.y

                                if (dragOffset > 0 && currentIndex < localOrder.lastIndex) {
                                    val below = localOrder[currentIndex + 1]
                                    val belowHeight = rowHeights[below.id] ?: Int.MAX_VALUE
                                    if (dragOffset > belowHeight / 2f) {
                                        localOrder = localOrder.toMutableList().apply {
                                            add(currentIndex, removeAt(currentIndex + 1))
                                        }
                                        dragOffset -= belowHeight
                                    }
                                } else if (dragOffset < 0 && currentIndex > 0) {
                                    val above = localOrder[currentIndex - 1]
                                    val aboveHeight = rowHeights[above.id] ?: Int.MAX_VALUE
                                    if (-dragOffset > aboveHeight / 2f) {
                                        localOrder = localOrder.toMutableList().apply {
                                            add(currentIndex, removeAt(currentIndex - 1))
                                        }
                                        dragOffset += aboveHeight
                                    }
                                }
                            },
                        )
                    },
                    onToggleComplete = { onToggleComplete(item) },
                    onRename = { onRename(item, it) },
                    onEditNotes = { onEditNotes(item, it) },
                    onCancel = { onCancel(item) },
                    onDelete = { onDelete(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoRow(
    item: TodoItemEntity,
    draggable: Boolean,
    rowModifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    onToggleComplete: () -> Unit,
    onRename: (String) -> Unit,
    onEditNotes: (String) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var editingTitle by remember(item.id) { mutableStateOf(false) }
    var titleText by remember(item.id) { mutableStateOf(item.title) }
    var notesOpen by remember(item.id) { mutableStateOf(false) }
    var notesText by remember(item.id) { mutableStateOf(item.notes ?: "") }
    var menuOpen by remember { mutableStateOf(false) }
    // onFocusChanged reports an initial isFocused=false the moment the field
    // is composed (before the LaunchedEffect-driven requestFocus() below
    // lands) — without this guard that spurious report is indistinguishable
    // from a real blur and immediately exits edit mode before the user ever
    // gets focus.
    var titleHasFocused by remember(item.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember(item.id) { FocusRequester() }

    LaunchedEffect(item.title) { titleText = item.title }
    LaunchedEffect(item.notes) { notesText = item.notes ?: "" }
    LaunchedEffect(editingTitle) {
        if (editingTitle) {
            titleHasFocused = false
            titleFocusRequester.requestFocus()
        }
    }

    val done = item.status == "DONE"
    val cancelled = item.status == "CANCELLED"

    // confirmValueChange is re-invoked repeatedly while the swipe settles
    // (it's a veto hook, not a one-shot event), so calling onDelete() from
    // inside it fires once per re-evaluation — several times for one swipe.
    // React to currentValue settling instead; LaunchedEffect only restarts
    // when that value actually changes, so this fires exactly once.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it != SwipeToDismissBoxValue.StartToEnd },
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    // The margin has to wrap the whole swipe box, background included — padding
    // only the foreground Surface left the backgroundContent's full-bleed color
    // showing through around every card at rest.
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        SwipeToDismissBox(
            state = dismissState,
            modifier = rowModifier,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        if (draggable) {
                            Text("⠿", modifier = dragHandleModifier.padding(end = 4.dp))
                        } else {
                            Spacer(modifier = Modifier.width(20.dp))
                        }

                        Checkbox(checked = done, onCheckedChange = { onToggleComplete() })

                        if (editingTitle) {
                            OutlinedTextField(
                                value = titleText,
                                onValueChange = { titleText = it },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(titleFocusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            titleHasFocused = true
                                        } else if (titleHasFocused) {
                                            editingTitle = false
                                            val next = titleText.trim()
                                            if (next.isNotEmpty() && next != item.title) onRename(next) else titleText = item.title
                                        }
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            )
                        } else {
                            Text(
                                text = item.title,
                                modifier = Modifier.weight(1f).clickable { editingTitle = true },
                                textDecoration = if (done || cancelled) TextDecoration.LineThrough else null,
                                color = if (done || cancelled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        if (item.priority > 0) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PRIORITY_COLOR[item.priority]))
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        if (item.rolledOverFromId != null) {
                            Text("↩", modifier = Modifier.padding(horizontal = 2.dp))
                        }

                        TextButton(onClick = { notesOpen = !notesOpen }) {
                            Text(if (notesOpen) "–" else "+")
                        }

                        Box {
                            IconButton(onClick = { menuOpen = true }) { Text("⋮") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (!cancelled) {
                                    DropdownMenuItem(
                                        text = { Text("Cancel task") },
                                        onClick = {
                                            menuOpen = false
                                            onCancel()
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (notesOpen) {
                        OutlinedTextField(
                            value = notesText,
                            onValueChange = { notesText = it },
                            placeholder = { Text("Notes") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 28.dp, top = 4.dp)
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused && notesText != (item.notes ?: "")) onEditNotes(notesText)
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        )
                    }
                }
            }
        }
    }
}
