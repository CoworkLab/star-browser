package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.agent.CitationItem
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalStarSemanticColors
import com.example.ui.theme.StarMonoTextStyle

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Bullet(val marker: String, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data object Divider : MdBlock()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichMarkdownText(
    markdown: String,
    citations: List<CitationItem> = emptyList(),
    onOpenUrl: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val semantic = LocalStarSemanticColors.current
    val linkColor = MaterialTheme.colorScheme.tertiary
    val inlineBg = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = parseInlineMarkdown(block.text, linkColor, inlineBg, onOpenUrl),
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                is MdBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = parseInlineMarkdown(block.text, linkColor, inlineBg, onOpenUrl),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is MdBlock.Bullet -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.marker,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, linkColor, inlineBg, onOpenUrl),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MdBlock.Code -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = semantic.codeSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(semantic.codeHeaderSurface)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = block.language.ifBlank { "code" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { onCopyCode(block.code) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_modern_copy),
                                        contentDescription = "Copy code",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = block.code,
                                style = StarMonoTextStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }
                }

                is MdBlock.Paragraph -> {
                    val annotated = remember(block.text, linkColor, inlineBg) {
                        parseInlineMarkdown(block.text, linkColor, inlineBg, onOpenUrl)
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }

        if (citations.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                citations.forEach { cite ->
                    AssistChip(
                        onClick = { onOpenUrl(cite.url) },
                        label = {
                            Text(
                                text = cite.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_modern_external),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.tertiary,
                            trailingIconContentColor = MaterialTheme.colorScheme.tertiary
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = RoundedCornerShape(100.dp)
                    )
                }
            }
        }
    }
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MdBlock>()
    var inCode = false
    var codeLang = ""
    val codeBuf = StringBuilder()
    val paraBuf = StringBuilder()

    fun flushPara() {
        val text = paraBuf.toString().trim()
        if (text.isNotEmpty()) blocks.add(MdBlock.Paragraph(text))
        paraBuf.clear()
    }

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks.add(MdBlock.Code(codeLang, codeBuf.toString().trimEnd()))
                codeBuf.clear()
                codeLang = ""
                inCode = false
            } else {
                flushPara()
                inCode = true
                codeLang = trimmed.removePrefix("```").trim()
            }
            continue
        }
        if (inCode) {
            codeBuf.append(line).append('\n')
            continue
        }

        when {
            trimmed.isEmpty() -> flushPara()
            trimmed == "---" || trimmed == "***" -> {
                flushPara()
                blocks.add(MdBlock.Divider)
            }
            trimmed.startsWith("### ") -> {
                flushPara()
                blocks.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushPara()
                blocks.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                flushPara()
                blocks.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("> ") -> {
                flushPara()
                val quoteText = trimmed.removePrefix("> ").trim()
                val last = blocks.lastOrNull()
                if (last is MdBlock.Quote) {
                    blocks[blocks.lastIndex] = MdBlock.Quote("${last.text}\n$quoteText")
                } else {
                    blocks.add(MdBlock.Quote(quoteText))
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushPara()
                blocks.add(MdBlock.Bullet("•", trimmed.drop(2).trim()))
            }
            Regex("""^\d+\.\s+""").containsMatchIn(trimmed) -> {
                flushPara()
                val match = Regex("""^(\d+\.)\s+(.*)$""").find(trimmed)
                if (match != null) {
                    blocks.add(MdBlock.Bullet(match.groupValues[1], match.groupValues[2]))
                }
            }
            else -> {
                if (paraBuf.isNotEmpty()) paraBuf.append(' ')
                paraBuf.append(line.trim())
            }
        }
    }
    if (inCode) {
        blocks.add(MdBlock.Code(codeLang, codeBuf.toString().trimEnd()))
    } else {
        flushPara()
    }
    return blocks
}

private fun parseInlineMarkdown(
    text: String,
    linkColor: Color,
    inlineCodeBg: Color,
    onOpenUrl: (String) -> Unit
): AnnotatedString = buildAnnotatedString {
    val tokenRegex = Regex("""(\[([^\]]+)]\((https?://[^)\s]+)\)|\*\*([^*]+)\*\*|`([^`]+)`|\*([^*]+)\*)""")
    var cursor = 0
    for (m in tokenRegex.findAll(text)) {
        if (m.range.first > cursor) {
            append(text.substring(cursor, m.range.first))
        }
        when {
            m.groupValues[2].isNotEmpty() -> {
                val label = m.groupValues[2]
                val url = m.groupValues[3]
                val link = LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
                        )
                    ),
                    linkInteractionListener = { item ->
                        val target = (item as? LinkAnnotation.Url)?.url
                        if (!target.isNullOrBlank()) onOpenUrl(target)
                    }
                )
                withLink(link) {
                    append(label)
                }
            }
            m.groupValues[4].isNotEmpty() -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(m.groupValues[4])
                }
            }
            m.groupValues[5].isNotEmpty() -> {
                withStyle(SpanStyle(fontFamily = JetBrainsMonoFamily, background = inlineCodeBg)) {
                    append(" ${m.groupValues[5]} ")
                }
            }
            m.groupValues[6].isNotEmpty() -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(m.groupValues[6])
                }
            }
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
