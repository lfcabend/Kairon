package com.kairon.android.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Computed once at class-load time, not inside the composable body, to avoid
// Compose lint's NonObservableLocale check (java.util.Locale.getDefault()
// read live inside a @Composable isn't recomposition-safe) — same posture
// DayViewScreen's own DATE_NAV_FORMATTER already takes.
private val MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val WEEKDAY_LABELS = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1) }

/**
 * Month-grid calendar with a dot under any day that has ≥1 journal entry
 * (D5 — [markedDays] comes from a Room-only query, never a network call).
 */
@Composable
fun JournalCalendar(
    month: YearMonth,
    markedDays: Set<LocalDate>,
    selectedDay: LocalDate?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousMonth) { Text("<") }
            Text(
                month.atDay(1).format(MONTH_YEAR_FORMATTER),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onNextMonth) { Text(">") }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val firstOfMonth = month.atDay(1)
        // ISO Monday=1..Sunday=7; shift so the grid's first column is Monday.
        val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
        val daysInMonth = month.lengthOfMonth()
        val totalCells = leadingBlanks + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingBlanks + 1
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            val isSelected = date == selectedDay
                            val isToday = date == LocalDate.now()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clip(CircleShape).clickable { onDayClick(date) },
                            ) {
                                Text(
                                    dayNumber.toString(),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Box(modifier = Modifier.size(4.dp)) {
                                    if (date in markedDays) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
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
}
