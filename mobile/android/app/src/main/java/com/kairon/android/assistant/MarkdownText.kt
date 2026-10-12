package com.kairon.android.assistant

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A small hand-rolled Compose renderer for the handful of markdown shapes an
 * `assistant_run.outputMarkdown` narrative actually uses: headings,
 * paragraphs with `**bold**` spans, bullet lists, and one GFM table shape
 * (the deterministic stats table M9 appends). Chosen over a third-party
 * markdown-renderer dependency per M11.6 §8 Q3's own named fallback — see
 * the milestone plan's corrections section for the compatibility concern
 * that led here.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    renderInline(block.text),
                    style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                is MarkdownBlock.Paragraph -> Text(
                    renderInline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                is MarkdownBlock.BulletList -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    block.items.forEach { entry ->
                        Row {
                            Text("• ")
                            Text(renderInline(entry))
                        }
                    }
                }
                is MarkdownBlock.Table -> MarkdownTable(block)
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock()
}

private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.*)$")
private val BULLET_REGEX = Regex("^[-*]\\s+(.*)$")
private val TABLE_SEPARATOR_REGEX = Regex("^:?-{3,}:?$")

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.isBlank() -> i++
            HEADING_REGEX.matches(line) -> {
                val match = HEADING_REGEX.find(line)!!
                blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2].trim())
                i++
            }
            line.trimStart().startsWith("|") -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    tableLines += lines[i]
                    i++
                }
                val rows = tableLines.map(::parseTableRow)
                val header = rows.getOrElse(0) { emptyList() }
                val isSeparator = rows.getOrNull(1)?.all { TABLE_SEPARATOR_REGEX.matches(it.trim()) } == true
                val dataRows = if (isSeparator) rows.drop(2) else rows.drop(1)
                blocks += MarkdownBlock.Table(header, dataRows)
            }
            BULLET_REGEX.matches(line) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && BULLET_REGEX.matches(lines[i])) {
                    items += BULLET_REGEX.find(lines[i])!!.groupValues[1].trim()
                    i++
                }
                blocks += MarkdownBlock.BulletList(items)
            }
            else -> {
                val paragraphLines = mutableListOf<String>()
                while (i < lines.size && lines[i].isNotBlank() &&
                    !HEADING_REGEX.matches(lines[i]) && !lines[i].trimStart().startsWith("|") && !BULLET_REGEX.matches(lines[i])
                ) {
                    paragraphLines += lines[i]
                    i++
                }
                blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString(" "))
            }
        }
    }
    return blocks
}

private fun parseTableRow(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")

private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in BOLD_REGEX.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        lastIndex = match.range.last + 1
    }
    append(text.substring(lastIndex))
}

@Composable
private fun MarkdownTable(table: MarkdownBlock.Table) {
    Column(modifier = Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState())) {
        if (table.header.isNotEmpty()) {
            Row {
                table.header.forEach { cell ->
                    Text(
                        cell,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(min = 80.dp),
                    )
                }
            }
            HorizontalDivider()
        }
        table.rows.forEach { row ->
            Row {
                row.forEach { cell ->
                    Text(
                        cell,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(min = 80.dp),
                    )
                }
            }
        }
    }
}
