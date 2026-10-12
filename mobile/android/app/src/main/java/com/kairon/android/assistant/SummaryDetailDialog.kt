package com.kairon.android.assistant

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kairon.android.client.model.AssistantRunView

private val KIND_LABEL = mapOf(
    "WEEKLY_SUMMARY" to "Weekly summary",
    "MONTHLY_SUMMARY" to "Monthly summary",
    "JOURNAL_REFLECTION" to "Weekly reflection",
)

private fun periodLabel(run: AssistantRunView): String {
    val start = run.periodStart ?: return ""
    val end = run.periodEnd ?: return ""
    return if (start == end) start.toString() else "$start – $end"
}

/**
 * Renders one run's `outputMarkdown` (narrative + the deterministic stats
 * table for a summary run) via [MarkdownText] — mirrors web's
 * `SummaryDetail`. While `status` is `PENDING`/`RUNNING`, shows a spinner;
 * the caller ([SummariesViewModel]) does the polling (D8).
 */
@Composable
fun SummaryDetailDialog(run: AssistantRunView?, loading: Boolean, onDismiss: () -> Unit) {
    if (run == null && !loading) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(run?.let { "${KIND_LABEL[it.kind] ?: it.kind} — ${periodLabel(it)}" } ?: "Summary") },
        text = {
            when {
                loading && run == null -> Text("Loading…")
                run == null -> Text("Not found.")
                run.status == "PENDING" || run.status == "RUNNING" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text("  Generating your summary…")
                }
                run.status == "FAILED" -> Text(
                    run.error ?: "This could not be generated.",
                    color = MaterialTheme.colorScheme.error,
                )
                else -> MarkdownText(run.outputMarkdown ?: "", modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
