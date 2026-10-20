package com.ultron.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.ultron.chat.R
import kotlinx.coroutines.delay

private data class MdSeg(val isCode: Boolean, val lang: String, val content: String)

private fun parseMarkdown(text: String): List<MdSeg> {
    val segs = mutableListOf<MdSeg>()
    val fence = "```"
    var i = 0
    while (i <= text.length) {
        val start = text.indexOf(fence, i)
        if (start < 0) {
            val rest = text.substring(i)
            if (rest.isNotEmpty()) segs.add(MdSeg(false, "", rest))
            break
        }
        val before = text.substring(i, start)
        if (before.isNotEmpty()) segs.add(MdSeg(false, "", before))
        val afterFence = start + 3
        val end = text.indexOf(fence, afterFence)
        val close = if (end < 0) text.length else end
        val nl = text.indexOf('\n', afterFence)
        val hasLangLine = nl in afterFence until close
        val lang = if (hasLangLine) text.substring(afterFence, nl).trim() else ""
        val codeStart = if (hasLangLine) nl + 1 else afterFence
        val code = text.substring(codeStart, close).trim('\n')
        segs.add(MdSeg(true, lang, code))
        i = if (end < 0) text.length + 1 else end + 3
    }
    return segs
}

/** Minimal inline markdown: **bold** and `inline code`. */
private fun inlineAnnotated(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '`') {
            val e = text.indexOf('`', i + 1)
            if (e > i) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x24FFFFFF), color = UltronRedSoft)) {
                    append(text.substring(i + 1, e))
                }
                i = e + 1; continue
            }
        }
        if (c == '*' && i + 1 < text.length && text[i + 1] == '*') {
            val e = text.indexOf("**", i + 2)
            if (e > i) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, e)) }
                i = e + 2; continue
            }
        }
        append(c); i++
    }
}

@Composable
fun MarkdownText(text: String, color: Color) {
    val segs = remember(text) { parseMarkdown(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segs.forEach { s ->
            if (s.isCode) CodeBlock(s.lang, s.content)
            else if (s.content.isNotBlank()) Text(inlineAnnotated(s.content.trim()), color = color, fontSize = 15.sp, lineHeight = 23.sp)
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clip = LocalClipboardManager.current
    val copyStr = stringResource(R.string.copy)
    val copiedStr = stringResource(R.string.copied)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0B0C0E)).border(1.dp, UltronLine, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF141619)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lang.ifBlank { "code" }, color = UltronMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { clip.setText(AnnotatedString(code)); copied = true }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = if (copied) Color(0xFF8CF0A6) else UltronMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(4.dp))
                Text(if (copied) copiedStr else copyStr, color = if (copied) Color(0xFF8CF0A6) else UltronMuted, fontSize = 11.sp)
            }
        }
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Text(code, color = Color(0xFFE6E6E6), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, softWrap = false)
        }
    }
}
