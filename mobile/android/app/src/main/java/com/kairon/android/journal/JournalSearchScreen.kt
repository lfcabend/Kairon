package com.kairon.android.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kairon.android.client.model.JournalSearchHitResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HIT_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalSearchScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: JournalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search journal...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.searching -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                state.error != null -> Text(state.error ?: "", modifier = Modifier.padding(16.dp))
                state.query.isBlank() -> Text(
                    "Search your journal entries",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                state.results.isEmpty() -> Text(
                    "No matching entries",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.id.toString() }) { hit ->
                        SearchHitRow(hit = hit, onClick = { hit.day?.let(onOpenDay) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(hit: JournalSearchHitResponse, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                hit.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            hit.day?.let { day -> Text(day.format(HIT_DATE_FORMATTER), style = MaterialTheme.typography.labelSmall) }
        }
        hit.snippet?.let { snippet ->
            Text(
                snippetAnnotatedString(snippet),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private val SNIPPET_BOLD_TAG = Regex("<b>(.*?)</b>")

/** Renders a `ts_headline` snippet's `<b>…</b>` markers as bold spans — mirrors the web app's own `Snippet` component. */
private fun snippetAnnotatedString(text: String) = buildAnnotatedString {
    var lastEnd = 0
    for (match in SNIPPET_BOLD_TAG.findAll(text)) {
        append(text.substring(lastEnd, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        lastEnd = match.range.last + 1
    }
    append(text.substring(lastEnd))
}
