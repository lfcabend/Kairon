package com.kairon.android.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.core.data.JournalEntryEntity
import com.kairon.android.core.network.syncBannerMessage
import com.kairon.android.core.ui.ErrorBanner
import com.kairon.android.core.ui.InlineError
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val DATE_NAV_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalDayScreen(
    targetDay: LocalDate?,
    onNewEntry: (LocalDate) -> Unit,
    onEditEntry: (UUID, LocalDate) -> Unit,
    onSearch: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val calendarState by viewModel.calendarState.collectAsState()
    var showCalendar by remember { mutableStateOf(false) }

    LaunchedEffect(targetDay) {
        targetDay?.let(viewModel::goToDate)
    }

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
                        Text(state.day.format(DATE_NAV_FORMATTER))
                        IconButton(onClick = viewModel::goToNextDay) { Text(">") }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search journal")
                    }
                    IconButton(onClick = {
                        viewModel.openCalendar()
                        showCalendar = true
                    }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Calendar")
                    }
                    if (state.day != state.today) {
                        TextButton(onClick = viewModel::goToToday) { Text("Today") }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNewEntry(state.day) }) {
                Icon(Icons.Default.Add, contentDescription = "New entry")
            }
        },
    ) { padding ->
        if (showCalendar) {
            Dialog(onDismissRequest = { showCalendar = false }) {
                Surface(shape = RoundedCornerShape(16.dp)) {
                    JournalCalendar(
                        month = calendarState.month,
                        markedDays = calendarState.markedDays,
                        selectedDay = state.day,
                        onPreviousMonth = { viewModel.setCalendarMonth(calendarState.month.minusMonths(1)) },
                        onNextMonth = { viewModel.setCalendarMonth(calendarState.month.plusMonths(1)) },
                        onDayClick = { date ->
                            viewModel.goToDate(date)
                            showCalendar = false
                        },
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.syncing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            state.syncError?.let { appError ->
                ErrorBanner(
                    message = appError.syncBannerMessage(),
                    onDismiss = viewModel::dismissSyncError,
                    onRetry = viewModel::refresh,
                )
            }
            state.error?.let { message ->
                InlineError(message, modifier = Modifier.padding(16.dp))
            }
            if (state.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No entries yet — tap + to write about today",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onClick = { onEditEntry(entry.id, entry.day) },
                            onDelete = { viewModel.delete(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: JournalEntryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        entry.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    entry.mood?.let { mood ->
                        Text("Mood: $mood/5", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    entry.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete entry")
            }
        }
    }
}
