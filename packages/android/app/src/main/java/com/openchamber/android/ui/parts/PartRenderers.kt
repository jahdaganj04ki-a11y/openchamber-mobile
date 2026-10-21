package com.openchamber.android.ui.parts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openchamber.android.core.design.OpenChamberTheme
import com.openchamber.android.core.model.FilePart
import com.openchamber.android.core.model.Part
import com.openchamber.android.core.model.ReasoningPart
import com.openchamber.android.core.model.TextPart
import com.openchamber.android.core.model.ToolPart
import com.openchamber.android.core.model.ToolState
import com.openchamber.android.i18n.LocalCatalog

enum class Role { USER, ASSISTANT }

/**
 * The parts of one message, in the order the message carries them.
 */
@Composable
fun MessageParts(parts: List<Part>, role: Role) {
    Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        parts.forEach { part ->
            when (part) {
                is TextPart -> TextPartView(part)
                is ReasoningPart -> ReasoningPartView(part)
                is ToolPart -> ToolPartView(part)
                is FilePart -> FilePartView(part)
                is com.openchamber.android.core.model.AgentPart -> {
                    Text(
                        text = "@${part.name}",
                        style = MaterialTheme.typography.labelMedium,
                        color = OpenChamberTheme.colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextPartView(part: TextPart) {
    MarkdownText(part.text)
}

@Composable
private fun ReasoningPartView(part: ReasoningPart) {
    var expanded by remember { mutableStateOf(false) }
    val colors = OpenChamberTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.subtle, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Text(
            text = if (expanded) LocalCatalog.current.t("android.thinking") else LocalCatalog.current.t("android.thinkingCollapsed", mapOf("count" to part.text.length.toString())),
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedForeground,
            modifier = Modifier.then(
                if (expanded) Modifier else Modifier,
            ),
        )
        if (expanded) {
            MarkdownText(part.text)
        }
    }
}

@Composable
private fun ToolPartView(part: ToolPart) {
    val colors = OpenChamberTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.subtle, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        Text(
            text = part.tool.ifBlank { "tool" },
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedForeground,
            fontWeight = FontWeight.Medium,
        )
        when (val state = part.state) {
            is ToolState.Pending -> {
                Text(
                    text = LocalCatalog.current.t("android.tool.preparing"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedForeground,
                )
            }
            is ToolState.Running -> {
                Text(
                    text = LocalCatalog.current.t("android.tool.running"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.info,
                )
            }
            is ToolState.Completed -> {
                if (state.output.isNotBlank()) {
                    Text(
                        text = state.output,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }
                state.attachments?.forEach { attachment ->
                    FilePartView(attachment)
                }
            }
            is ToolState.Failed -> {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
                state.output?.let { output ->
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilePartView(part: FilePart) {
    val colors = OpenChamberTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.elevated, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = part.filename ?: part.mime,
            style = MaterialTheme.typography.labelSmall,
            color = colors.foreground,
        )
    }
}

/**
 * A small Markdown renderer.
 *
 * It covers what a model writes in a conversation: paragraphs, code blocks,
 * inline code, bold, italic and lists. Anything else is shown as the text it
 * is, because a phone screen has no room for a full renderer and a broken
 * one is worse than a plain one.
 */
@Composable
fun MarkdownText(source: String) {
    val colors = OpenChamberTheme.colors
    val blocks = remember(source) { parseMarkdown(source) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.syntax.background, RoundedCornerShape(6.dp))
                            .padding(8.dp),
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = colors.foreground,
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.foreground,
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.listMarker,
                        )
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.foreground,
                        )
                    }
                }
                is MarkdownBlock.Rule -> HorizontalDivider(color = colors.divider)
            }
        }
    }
}

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val code: String, val language: String?) : MarkdownBlock
    data class ListItem(val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

/**
 * Splits the text into blocks.
 *
 * The inline markers stay in the text, because the text is shown in one run
 * and the phone has no inline styling pass. The blocks are what the layout
 * needs: a code block gets its own surface, a list item gets a marker.
 */
fun parseMarkdown(source: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    val lines = source.lines()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks += MarkdownBlock.Paragraph(paragraph.toString().trim())
        }
        paragraph.setLength(0)
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val fence = line.trimStart().startsWith("```")
        if (fence) {
            flushParagraph()
            val language = line.trimStart().removePrefix("```").trim().takeIf { it.isNotEmpty() }
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                code.appendLine(lines[index])
                index++
            }
            blocks += MarkdownBlock.Code(code.toString().trimEnd(), language)
            index++
            continue
        }
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks += MarkdownBlock.Rule
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks += MarkdownBlock.ListItem(trimmed.drop(2).trim())
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                flushParagraph()
                blocks += MarkdownBlock.ListItem(trimmed.replaceFirst(Regex("^\\d+\\.\\s+"), ""))
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        index++
    }
    flushParagraph()
    return blocks
}
