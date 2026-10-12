package com.kairon.android.projects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kairon.android.client.model.TaskDependencyResponse
import com.kairon.android.core.data.ProjectTaskEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private val DAY_WIDTH = 24.dp
private val ROW_HEIGHT = 40.dp
private val LABEL_WIDTH = 140.dp
private val HEADER_HEIGHT = 36.dp
private const val TICK_INTERVAL_DAYS = 7
private val GANTT_HEADER_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val GRIDLINE_COLOR = Color(0xFFE2E8F0)
private val TODAY_COLOR = Color(0xFFEF4444)

/**
 * D2 — a read-only Compose `Canvas` Gantt: bars, milestone diamonds (◆),
 * dependency arrows, a today line. No drag-to-reschedule; dates are edited
 * via [TaskEditScreen] instead. Horizontally scrollable from the start
 * (Q1) since any multi-week project overflows a phone screen immediately.
 */
@Composable
fun GanttTab(
    tasks: List<ProjectTaskEntity>,
    dependencies: List<TaskDependencyResponse>,
    onClick: (ProjectTaskEntity) -> Unit,
) {
    val rows = remember(tasks) { buildTreeOrder(tasks) }
    val dated = rows.map { it.task }.filter { it.plannedStart != null || it.plannedEnd != null }

    if (dated.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                "No tasks with planned dates yet",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val rangeStart = remember(dated) { dated.mapNotNull { it.plannedStart ?: it.plannedEnd }.min().minusDays(1) }
    val rangeEnd = remember(dated) { dated.mapNotNull { it.plannedEnd ?: it.plannedStart }.max().plusDays(1) }
    val totalDays = max(1, ChronoUnit.DAYS.between(rangeStart, rangeEnd).toInt())
    val rowIndex = remember(rows) { rows.mapIndexed { index, row -> row.task.id to index }.toMap() }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    // Tick marks every TICK_INTERVAL_DAYS, starting from the first such
    // offset so they land on calendar-meaningful days rather than drifting
    // with rangeStart's own padding.
    val tickOffsets = remember(totalDays) { (0..totalDays step TICK_INTERVAL_DAYS).toList() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: date ticks, pinned above the rows (no vertical scroll) but
        // horizontally scrolled in lockstep with the chart body below it —
        // without this, bars have no visible relationship to any date (the
        // original cut of this screen had no timeline indicators at all).
        Row(modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT)) {
            Spacer(modifier = Modifier.width(LABEL_WIDTH))
            Box(modifier = Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                Box(modifier = Modifier.size(DAY_WIDTH * totalDays, HEADER_HEIGHT)) {
                    tickOffsets.forEach { offset ->
                        val date = rangeStart.plusDays(offset.toLong())
                        Text(
                            date.format(GANTT_HEADER_FORMATTER),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.offset(x = DAY_WIDTH * offset)
                                .width(DAY_WIDTH * TICK_INTERVAL_DAYS)
                                .padding(start = 2.dp),
                        )
                    }
                    val todayOffsetDays = ChronoUnit.DAYS.between(rangeStart, LocalDate.now()).toInt()
                    if (todayOffsetDays in 0..totalDays) {
                        Text(
                            "Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = TODAY_COLOR,
                            modifier = Modifier.offset(x = DAY_WIDTH * todayOffsetDays + 2.dp, y = 18.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider()

        Row(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.width(LABEL_WIDTH).verticalScroll(vScroll)) {
                rows.forEach { row ->
                    Box(modifier = Modifier.height(ROW_HEIGHT).padding(start = (row.depth * 8).dp, top = 8.dp)) {
                        Text(
                            row.task.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll)
                    .verticalScroll(vScroll),
            ) {
                Canvas(
                    modifier = Modifier
                        .size(DAY_WIDTH * totalDays, ROW_HEIGHT * rows.size)
                        .pointerInput(rows) {
                            detectTapGestures { offset ->
                                val index = (offset.y / ROW_HEIGHT.toPx()).toInt()
                                rows.getOrNull(index)?.task?.let(onClick)
                            }
                        },
                ) {
                    val dayWidthPx = DAY_WIDTH.toPx()
                    val rowHeightPx = ROW_HEIGHT.toPx()

                    fun xFor(date: LocalDate): Float = ChronoUnit.DAYS.between(rangeStart, date).toFloat() * dayWidthPx
                    fun yCenterFor(taskId: UUID): Float = (rowIndex[taskId] ?: 0) * rowHeightPx + rowHeightPx / 2f

                    // Weekly gridlines, same offsets as the header's date ticks, so a
                    // bar can be traced straight up to the date it starts/ends on.
                    tickOffsets.forEach { offset ->
                        val x = offset * dayWidthPx
                        drawLine(
                            color = GRIDLINE_COLOR,
                            start = Offset(x, 0f),
                            end = Offset(x, rows.size * rowHeightPx),
                            strokeWidth = 1f,
                        )
                    }

                    // Today line.
                    val todayX = xFor(LocalDate.now())
                    if (todayX in 0f..(totalDays * dayWidthPx)) {
                        drawLine(
                            color = TODAY_COLOR,
                            start = Offset(todayX, 0f),
                            end = Offset(todayX, rows.size * rowHeightPx),
                            strokeWidth = 2f,
                        )
                    }

                    // Bars / milestones.
                    rows.forEach { row ->
                        val task = row.task
                        val color = parseHexColor(task.projectColor)
                        val y = yCenterFor(task.id)
                        if (task.isMilestone) {
                            val x = xFor(task.plannedStart ?: task.plannedEnd ?: rangeStart)
                            drawMilestoneDiamond(x, y, color)
                        } else {
                            val start = task.plannedStart ?: task.plannedEnd ?: return@forEach
                            val end = task.plannedEnd ?: task.plannedStart ?: return@forEach
                            val x1 = xFor(start)
                            val x2 = xFor(end) + dayWidthPx
                            drawBar(x1, x2, y, color, task.progressPercent, rowHeightPx)
                        }
                    }

                    // Dependency arrows (red if violatesConstraint — a soft FS-violation warning, M5).
                    dependencies.forEach { dep ->
                        val predecessor = rows.firstOrNull { it.task.id == dep.predecessorId }?.task ?: return@forEach
                        val successor = rows.firstOrNull { it.task.id == dep.successorId }?.task ?: return@forEach
                        val fromDate = predecessor.plannedEnd ?: predecessor.plannedStart ?: return@forEach
                        val toDate = successor.plannedStart ?: successor.plannedEnd ?: return@forEach
                        val fromX = xFor(fromDate) + (if (predecessor.isMilestone) 0f else dayWidthPx)
                        val toX = xFor(toDate)
                        val fromY = yCenterFor(predecessor.id)
                        val toY = yCenterFor(successor.id)
                        val arrowColor = if (dep.violatesConstraint == true) Color(0xFFEF4444) else Color(0xFF94A3B8)
                        drawDependencyArrow(fromX, fromY, toX, toY, arrowColor)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBar(x1: Float, x2: Float, yCenter: Float, color: Color, progressPercent: Int, rowHeightPx: Float) {
    val barHeight = rowHeightPx * 0.5f
    val top = yCenter - barHeight / 2f
    val width = max(2f, x2 - x1)
    drawRoundRect(
        color = color.copy(alpha = 0.35f),
        topLeft = Offset(x1, top),
        size = androidx.compose.ui.geometry.Size(width, barHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
    )
    val progressWidth = width * (progressPercent.coerceIn(0, 100) / 100f)
    if (progressWidth > 0f) {
        drawRoundRect(
            color = color,
            topLeft = Offset(x1, top),
            size = androidx.compose.ui.geometry.Size(progressWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
        )
    }
}

private fun DrawScope.drawMilestoneDiamond(x: Float, y: Float, color: Color) {
    val r = 10f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(x, y - r)
        lineTo(x + r, y)
        lineTo(x, y + r)
        lineTo(x - r, y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawDependencyArrow(x1: Float, y1: Float, x2: Float, y2: Float, color: Color) {
    val midX = (x1 + x2) / 2f
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(x1, y1)
        lineTo(midX, y1)
        lineTo(midX, y2)
        lineTo(x2, y2)
    }
    drawPath(path, color, style = Stroke(width = 2f))

    // Arrowhead at the successor end.
    val headSize = 6f
    val arrowPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(x2, y2)
        lineTo(x2 - headSize, y2 - headSize / 2f)
        lineTo(x2 - headSize, y2 + headSize / 2f)
        close()
    }
    drawPath(arrowPath, color)
}

private fun Iterable<LocalDate>.min(): LocalDate = reduce { a, b -> if (a.isBefore(b)) a else b }
private fun Iterable<LocalDate>.max(): LocalDate = reduce { a, b -> if (a.isAfter(b)) a else b }
