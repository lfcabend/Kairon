package com.kairon.android.assistant

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.client.model.SummaryRequest
import com.kairon.android.core.ui.InlineError

private val KIND_LABEL = mapOf(
    "WEEKLY_SUMMARY" to "Weekly",
    "MONTHLY_SUMMARY" to "Monthly",
    "JOURNAL_REFLECTION" to "Weekly reflection",
)

private fun periodLabel(periodStart: String?, periodEnd: String?): String {
    if (periodStart == null || periodEnd == null) return ""
    return if (periodStart == periodEnd) periodStart else "$periodStart – $periodEnd"
}

/**
 * The execution-summaries + journal-reflection history screen (D1), reached
 * via the top-bar overflow menu — mirrors web's `SummariesPage`. A row tap
 * opens [SummaryDetailDialog], which polls while its run is in flight (D8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummariesScreen(onBack: () -> Unit, viewModel: SummariesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Summaries") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(enabled = !state.requestingSummary, onClick = { viewModel.generateSummary(SummaryRequest.Period.WEEK) }) {
                    Text("Weekly summary")
                }
                OutlinedButton(enabled = !state.requestingSummary, onClick = { viewModel.generateSummary(SummaryRequest.Period.MONTH) }) {
                    Text("Monthly summary")
                }
            }
            if (state.journalReflectionEnabled) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedButton(enabled = !state.requestingReflection, onClick = viewModel::generateReflection) {
                        Text("Weekly reflection")
                    }
                }
            }

            state.error?.let {
                InlineError(it, modifier = Modifier.padding(16.dp))
            }

            if (!state.loading && state.runs.isEmpty()) {
                Text(
                    "No summaries yet — generate one above, or enable execution summaries in Account settings for automatic weekly/monthly generation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.runs, key = { it.id.toString() }) { run ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openRun(run) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(KIND_LABEL[run.kind] ?: run.kind ?: "")
                            Text(
                                periodLabel(run.periodStart?.toString(), run.periodEnd?.toString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AssistChip(onClick = { viewModel.openRun(run) }, label = { Text(run.status ?: "") })
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    SummaryDetailDialog(run = state.selectedRun, loading = state.selectedRunLoading, onDismiss = viewModel::closeDetail)
}
