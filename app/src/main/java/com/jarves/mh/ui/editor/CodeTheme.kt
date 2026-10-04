package com.jarves.mh.ui.editor

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.jarves.mh.ui.theme.LocalDarkTheme

/** Editor settings the user can change; saved across launches. */
@Immutable
data class EditorPreferences(val wordWrap: Boolean = true, val fontSize: Float = DefaultFontSize) {
    companion object {
        const val DefaultFontSize = 13f
        const val MinFontSize = 9f
        const val MaxFontSize = 24f
    }
}

/**
 * Syntax colours (GitHub's light and dark palettes, which read well on the app's tinted surfaces)
 * plus the editor's own highlight colours.
 */
@Immutable
class CodeTheme internal constructor(
    private val styles: Array<SpanStyle?>,
    val lineHighlight: Color,
    val match: Color,
    val currentMatch: Color,
    val bracket: Color,
    val flash: Color,
    val gutterText: Color,
    val gutterActiveText: Color,
) {
    fun style(kind: TokenKind): SpanStyle? = styles[kind.ordinal]
}

private class Palette(
    val keyword: Long,
    val type: Long,
    val function: Long,
    val string: Long,
    val escape: Long,
    val constant: Long,
    val comment: Long,
    val tag: Long,
    val variable: Long,
    val deleted: Long,
)

private val LightPalette = Palette(
    keyword = 0xFFCF222E, type = 0xFF953800, function = 0xFF8250DF, string = 0xFF0A3069, escape = 0xFF116329,
    constant = 0xFF0550AE, comment = 0xFF6E7781, tag = 0xFF116329, variable = 0xFF953800, deleted = 0xFF82071E,
)

private val DarkPalette = Palette(
    keyword = 0xFFFF7B72, type = 0xFFFFA657, function = 0xFFD2A8FF, string = 0xFFA5D6FF, escape = 0xFF7EE787,
    constant = 0xFF79C0FF, comment = 0xFF8B949E, tag = 0xFF7EE787, variable = 0xFFFFA657, deleted = 0xFFFFA198,
)

internal fun codeTheme(dark: Boolean, scheme: ColorScheme): CodeTheme {
    val p = if (dark) DarkPalette else LightPalette
    fun color(value: Long) = SpanStyle(color = Color(value))
    val styles = arrayOfNulls<SpanStyle>(TokenKind.entries.size)
    fun set(kind: TokenKind, style: SpanStyle) {
        styles[kind.ordinal] = style
    }
    set(TokenKind.KEYWORD, color(p.keyword))
    set(TokenKind.TYPE, color(p.type))
    set(TokenKind.FUNCTION, color(p.function))
    set(TokenKind.STRING, color(p.string))
    set(TokenKind.ESCAPE, color(p.escape))
    set(TokenKind.NUMBER, color(p.constant))
    set(TokenKind.CONSTANT, color(p.constant))
    set(TokenKind.COMMENT, SpanStyle(color = Color(p.comment), fontStyle = FontStyle.Italic))
    set(TokenKind.ANNOTATION, color(p.function))
    set(TokenKind.PROPERTY, color(p.tag))
    set(TokenKind.TAG, color(p.tag))
    set(TokenKind.ATTRIBUTE, color(p.constant))
    set(TokenKind.VARIABLE, color(p.variable))
    set(TokenKind.REGEX, color(p.escape))
    set(TokenKind.META, color(p.keyword))
    set(TokenKind.HEADING, SpanStyle(color = Color(p.constant), fontWeight = FontWeight.Bold))
    set(TokenKind.EMPHASIS, SpanStyle(fontStyle = FontStyle.Italic))
    set(TokenKind.STRONG, SpanStyle(fontWeight = FontWeight.Bold))
    set(TokenKind.LINK, SpanStyle(color = Color(p.string), textDecoration = TextDecoration.Underline))
    set(TokenKind.INSERTED, color(p.tag))
    set(TokenKind.DELETED, color(p.deleted))
    val search = if (dark) Color(0xFFD29922) else Color(0xFFE3B341)
    return CodeTheme(
        styles = styles,
        lineHighlight = scheme.onSurface.copy(alpha = if (dark) 0.06f else 0.045f),
        match = search.copy(alpha = if (dark) 0.28f else 0.35f),
        currentMatch = search.copy(alpha = if (dark) 0.7f else 0.75f),
        bracket = scheme.primary.copy(alpha = 0.22f),
        flash = scheme.primary.copy(alpha = 0.16f),
        gutterText = scheme.onSurfaceVariant.copy(alpha = 0.5f),
        gutterActiveText = scheme.onSurface,
    )
}

@Composable
internal fun rememberCodeTheme(): CodeTheme {
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    return remember(dark, scheme) { codeTheme(dark, scheme) }
}
