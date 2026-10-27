package com.kairos.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

/**
 * Note enrichie : texte libre où une ligne `- [ ] …` (ou `- [x] …`) devient une **case à cocher** et
 * où les **liens** (http(s)://, www.) sont cliquables. Le texte reste la source de vérité (stocké tel
 * quel en base, lisible partout, cherchable) : cocher une case réécrit simplement sa ligne.
 */
private val CheckboxLine = Regex("""^(\s*)[-*] \[([ xX])] ?(.*)$""")
private val UrlPattern = Regex("""\b(?:https?://|www\.)[^\s<>"]+[^\s<>".,;:!?)\]]""", RegexOption.IGNORE_CASE)

/** Préfixe d'une case à cocher vide, inséré par l'éditeur. */
const val NOTE_CHECKBOX_PREFIX = "- [ ] "

/** Avancement des cases à cocher d'une note (cochées, total), ou null si elle n'en contient pas. */
fun noteChecklist(note: String): Pair<Int, Int>? {
    val matches = note.lines().mapNotNull { CheckboxLine.matchEntire(it) }
    if (matches.isEmpty()) return null
    return matches.count { it.groupValues[2].isNotBlank() } to matches.size
}

/** Inverse l'état de la case à cocher de la ligne [lineIndex] (sans effet sur une ligne de texte). */
fun toggleNoteCheckbox(note: String, lineIndex: Int): String {
    val lines = note.lines().toMutableList()
    val match = lines.getOrNull(lineIndex)?.let { CheckboxLine.matchEntire(it) } ?: return note
    val (indent, mark, text) = match.destructured
    lines[lineIndex] = "$indent- [${if (mark.isBlank()) "x" else " "}] $text"
    return lines.joinToString("\n")
}

/** Rend [text] avec ses URLs cliquables (ouvertes dans le navigateur). */
fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    UrlPattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val url = match.value.let { if (it.startsWith("www.", ignoreCase = true)) "https://$it" else it }
        withLink(
            LinkAnnotation.Url(
                url,
                TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            ),
        ) { append(match.value) }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

/**
 * Affichage interactif d'une note : cases à cocher togglables ([onToggle] reçoit l'index de ligne)
 * et liens cliquables. Les lignes vides deviennent un petit espacement.
 */
@Composable
fun RichNoteView(note: String, accent: Color, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        note.lines().forEachIndexed { index, line ->
            val match = CheckboxLine.matchEntire(line)
            when {
                match != null -> {
                    val (indent, mark, text) = match.destructured
                    val checked = mark.isNotBlank()
                    Row(
                        modifier = Modifier
                            .padding(start = (indent.length * 8).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggle(index) }
                            .padding(vertical = 2.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            linkify(text, linkColor),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = if (checked) TextDecoration.LineThrough else null,
                            ),
                            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(linkify(line, linkColor), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
