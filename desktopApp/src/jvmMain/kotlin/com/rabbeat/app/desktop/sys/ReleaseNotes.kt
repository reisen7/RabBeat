package com.rabbeat.app.desktop.sys

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

internal sealed interface ReleaseBlock {
    data class Heading(val text: String) : ReleaseBlock
    data class Bullet(val text: String) : ReleaseBlock
    data class Paragraph(val text: String) : ReleaseBlock
}

internal sealed interface MdSpan {
    data class Text(val value: String, val bold: Boolean = false, val code: Boolean = false) : MdSpan
    data class Link(val label: String, val url: String) : MdSpan
}

internal fun parseReleaseNotes(markdown: String): List<ReleaseBlock> {
    val blocks = mutableListOf<ReleaseBlock>()
    for (raw in markdown.replace("\r\n", "\n").split('\n')) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        val heading = headingPattern.matchEntire(line)
        if (heading != null) {
            blocks += ReleaseBlock.Heading(heading.groupValues[2].trim())
            continue
        }
        val bullet = bulletPattern.matchEntire(line)
        if (bullet != null) {
            blocks += ReleaseBlock.Bullet(bullet.groupValues[1].trim())
            continue
        }
        blocks += ReleaseBlock.Paragraph(line)
    }
    return blocks
}

internal fun parseInline(src: String): List<MdSpan> {
    val out = mutableListOf<MdSpan>()
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            out += MdSpan.Text(plain.toString())
            plain.clear()
        }
    }
    var i = 0
    while (i < src.length) {
        if (src.startsWith("**", i)) {
            val end = src.indexOf("**", i + 2)
            if (end > i) {
                flush()
                out += MdSpan.Text(src.substring(i + 2, end), bold = true)
                i = end + 2
                continue
            }
        }
        if (src[i] == '`') {
            val end = src.indexOf('`', i + 1)
            if (end > i) {
                flush()
                out += MdSpan.Text(src.substring(i + 1, end), code = true)
                i = end + 1
                continue
            }
        }
        if (src[i] == '[') {
            val labelEnd = src.indexOf("](", i + 1)
            val urlEnd = if (labelEnd > i) src.indexOf(')', labelEnd + 2) else -1
            if (labelEnd > i && urlEnd > labelEnd) {
                flush()
                out += MdSpan.Link(src.substring(i + 1, labelEnd), src.substring(labelEnd + 2, urlEnd))
                i = urlEnd + 1
                continue
            }
        }
        if (src.startsWith("https://", i) || src.startsWith("http://", i)) {
            var end = i
            while (end < src.length && !src[end].isWhitespace() && src[end] != ')') end++
            var url = src.substring(i, end)
            while (url.lastOrNull() in trailingUrlPunct) url = url.dropLast(1)
            if (url.length > "https://".length) {
                flush()
                out += MdSpan.Link(url, url)
                i += url.length
                continue
            }
        }
        plain.append(src[i])
        i++
    }
    flush()
    return out
}

private val headingPattern = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletPattern = Regex("""^[-*]\s+(.*)$""")
private val trailingUrlPunct = setOf('.', ',', ';', ':', '。', '，', '；', '：')

@Composable
internal fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseReleaseNotes(markdown) }
    if (blocks.isEmpty()) {
        Text("查看发布页了解详情", style = MaterialTheme.typography.bodySmall, modifier = modifier)
        return
    }
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ReleaseBlock.Heading -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp, bottom = 4.dp),
                )
                is ReleaseBlock.Bullet -> Row(Modifier.padding(bottom = 4.dp)) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    InlineMarkdown(block.text, Modifier.weight(1f))
                }
                is ReleaseBlock.Paragraph -> InlineMarkdown(
                    block.text,
                    Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InlineMarkdown(source: String, modifier: Modifier = Modifier) {
    val spans = remember(source) { parseInline(source) }
    val accent = LocalAccentPalette.current.color
    val codeFill = LocalRabBeatPalette.current.fill
    Text(
        buildAnnotatedString {
            spans.forEach { span ->
                when (span) {
                    is MdSpan.Link -> withLink(
                        LinkAnnotation.Url(
                            span.url,
                            TextLinkStyles(SpanStyle(color = accent)),
                        ) { DesktopUpdate.browse(span.url) },
                    ) { append(span.label) }
                    is MdSpan.Text -> {
                        val style = when {
                            span.code -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeFill)
                            span.bold -> SpanStyle(fontWeight = FontWeight.SemiBold)
                            else -> null
                        }
                        if (style == null) append(span.value) else withStyle(style) { append(span.value) }
                    }
                }
            }
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}
