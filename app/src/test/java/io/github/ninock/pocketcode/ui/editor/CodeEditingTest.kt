package io.github.ninock.pocketcode.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodeEditingTest {
    private val kotlin = CodeLanguages.Kotlin
    private val spaces4 = IndentStyle(useTabs = false, size = 4)

    /** Applies [edit] to [text] and marks the resulting selection with `|` (caret) or `[...]`. */
    private fun apply(text: String, edit: CodeEdit?): String {
        requireNotNull(edit) { "no edit" }
        val result = text.substring(0, edit.start) + edit.text + text.substring(edit.end)
        return if (edit.selectionStart == edit.selectionEnd) {
            result.substring(0, edit.selectionStart) + "|" + result.substring(edit.selectionStart)
        } else {
            result.substring(0, edit.selectionStart) + "[" + result.substring(edit.selectionStart, edit.selectionEnd) + "]" + result.substring(edit.selectionEnd)
        }
    }

    /** Types [inserted] at the `|` in [marked] (replacing a `[...]` selection) through smart typing. */
    private fun type(marked: String, inserted: String, language: CodeLanguage = kotlin, indent: IndentStyle = spaces4): String? {
        val (text, start, end) = parse(marked)
        val edit = CodeEditing.smartType(text, start, end, inserted, start, end, language, indent) ?: return null
        return apply(text, edit)
    }

    private fun backspace(marked: String, language: CodeLanguage = kotlin, indent: IndentStyle = spaces4): String? {
        val (text, caret) = parse(marked)
        val edit = CodeEditing.smartType(text, caret - 1, caret, "", caret, caret, language, indent) ?: return null
        return apply(text, edit)
    }

    private fun parse(marked: String): Triple<String, Int, Int> {
        val open = marked.indexOf('[')
        if (open >= 0 && marked.contains(']')) {
            val close = marked.indexOf(']')
            return Triple(marked.removeRange(close, close + 1).removeRange(open, open + 1), open, close - 1)
        }
        val caret = marked.indexOf('|')
        return Triple(marked.removeRange(caret, caret + 1), caret, caret)
    }

    private fun command(marked: String, block: (String, Int, Int) -> CodeEdit?): String? {
        val (text, start, end) = parse(marked)
        return block(text, start, end)?.let { apply(text, it) }
    }

    @Test
    fun enterKeepsIndentation() {
        assertEquals("    foo()\n    |", type("    foo()|", "\n"))
        assertNull(type("foo()|", "\n"))
    }

    @Test
    fun enterAfterOpenerIndentsAndSplitsPairs() {
        assertEquals("fun a() {\n    |\n}", type("fun a() {|}", "\n"))
        assertEquals("    call(\n        |", type("    call(|", "\n"))
        assertEquals("def f():\n    |", type("def f():|", "\n", CodeLanguages.Python))
        assertEquals("if x {\n\t|\n}", type("if x {|}", "\n", CodeLanguages.Go, IndentStyle(useTabs = true, size = 4)))
    }

    @Test
    fun enterContinuesDocComments() {
        assertEquals("/**\n * |", type("/**|", "\n"))
        assertEquals("/**\n * a\n * |", type("/**\n * a|", "\n"))
        assertNull(type("x = a * b|", "\n"))
    }

    @Test
    fun enterContinuesMarkdownLists() {
        val md = CodeLanguages.Markdown
        assertEquals("- one\n- |", type("- one|", "\n", md))
        assertEquals("9. nine\n10. |", type("9. nine|", "\n", md))
        val task = "- [x] done"
        val edit = CodeEditing.smartType(task, task.length, task.length, "\n", task.length, task.length, md, spaces4)
        assertEquals(CodeEdit(task.length, task.length, "\n- [ ] ", task.length + 7), edit)
        assertEquals("- one\n|", type("- one\n- |", "\n", md))
    }

    @Test
    fun bracketsAndQuotesAutoClose() {
        assertEquals("f(|)", type("f|", "("))
        assertEquals("val s = \"|\"", type("val s = |", "\""))
        assertNull(type("|foo", "("))
        assertNull(type("don|", "'"))
        assertNull(type("# notes|", "(", CodeLanguages.PlainText))
    }

    @Test
    fun typingAClosingCharacterStepsOverIt() {
        assertEquals("f()|", type("f(|)", ")"))
        assertEquals("\"a\"|", type("\"a|\"", "\""))
    }

    @Test
    fun typingOverASelectionWrapsIt() {
        assertEquals("f([a + b])", type("f[a + b]", "("))
        assertEquals("\"[word]\"", type("[word]", "\""))
    }

    @Test
    fun closingBraceLinesUpWithItsOpener() {
        assertEquals("if (x) {\n    foo()\n}|", type("if (x) {\n    foo()\n    |", "}"))
        assertNull(type("if (x) {\n    foo()\n}|", "}"))
    }

    @Test
    fun backspaceDeletesEmptyPairsAndIndentLevels() {
        assertEquals("f|", backspace("f(|)"))
        assertEquals("    |", backspace("        |"))
        assertEquals("    |", backspace("      |", indent = IndentStyle(false, 2)))
        assertNull(backspace("       |", indent = IndentStyle(false, 2)))
        assertNull(backspace("ab|"))
    }

    @Test
    fun indentAndOutdent() {
        assertEquals("a |b", command("a|b") { t, s, e -> CodeEditing.indent(t, s, e, IndentStyle(false, 2)) })
        assertEquals("  |b", command("|b") { t, s, e -> CodeEditing.indent(t, s, e, IndentStyle(false, 2)) })
        assertEquals("[    a\n    b]\nc", command("[a\nb]\nc") { t, s, e -> CodeEditing.indent(t, s, e, spaces4) })
        assertEquals("[a\nb]", command("[    a\n  b]") { t, s, e -> CodeEditing.outdent(t, s, e, spaces4) })
        assertEquals("x|", command("    x|") { t, s, e -> CodeEditing.outdent(t, s, e, spaces4) })
        assertNull(command("x|") { t, s, e -> CodeEditing.outdent(t, s, e, spaces4) })
    }

    @Test
    fun toggleLineComments() {
        assertEquals("    // a|", command("    a|") { t, s, e -> CodeEditing.toggleComment(t, s, e, kotlin) })
        assertEquals("    a|", command("    // a|") { t, s, e -> CodeEditing.toggleComment(t, s, e, kotlin) })
        assertEquals("[  # a\n\n  #   b]", command("[  a\n\n    b]") { t, s, e -> CodeEditing.toggleComment(t, s, e, CodeLanguages.Python) })
        assertEquals("/* color: red; */|", command("color: red;|") { t, s, e -> CodeEditing.toggleComment(t, s, e, CodeLanguages.Css) })
        assertEquals("color: red;|", command("/* color: red; */|") { t, s, e -> CodeEditing.toggleComment(t, s, e, CodeLanguages.Css) })
    }

    @Test
    fun lineOperations() {
        assertEquals("a\nb\nb|\nc", command("a\nb|\nc") { t, s, e -> CodeEditing.duplicateLines(t, s, e) })
        assertEquals("a\n|c", command("a\nb|\nc") { t, s, e -> CodeEditing.deleteLines(t, s, e) })
        assertEquals("a\n|b", command("a\nb\nc|") { t, s, e -> CodeEditing.deleteLines(t, s, e) })
        assertEquals("b|\na\nc", command("a\nb|\nc") { t, s, e -> CodeEditing.moveLines(t, s, e, down = false) })
        assertEquals("a\nc\nb|", command("a\nb|\nc") { t, s, e -> CodeEditing.moveLines(t, s, e, down = true) })
        assertNull(command("a|\nb") { t, s, e -> CodeEditing.moveLines(t, s, e, down = false) })
    }

    @Test
    fun bracketMatchingSkipsStrings() {
        val text = "f(\")\", g(1))"
        val tokens = highlight(text, kotlin)
        assertEquals(1 to 11, CodeEditing.matchingBracket(text, 2, tokens))
        assertEquals(11 to 1, CodeEditing.matchingBracket(text, 12, tokens))
        assertEquals(8 to 10, CodeEditing.matchingBracket(text, 9, tokens))
        assertNull(CodeEditing.matchingBracket("abc", 1, null))
    }

    @Test
    fun indentDetection() {
        assertEquals(IndentStyle(false, 2), CodeEditing.detectIndent("a:\n  b:\n    c: 1\n  d: 2\n", CodeLanguages.Yaml))
        assertEquals(IndentStyle(false, 4), CodeEditing.detectIndent("fun a() {\n    b()\n}\n", CodeLanguages.Kotlin))
        assertEquals(IndentStyle(true, 4), CodeEditing.detectIndent("func a() {\n\tb()\n\tc()\n}\n", CodeLanguages.Go))
        assertEquals(IndentStyle.of(CodeLanguages.JavaScript), CodeEditing.detectIndent("a();\nb();\n", CodeLanguages.JavaScript))
    }

    @Test
    fun lineIndex() {
        val index = LineIndex("ab\n\ncd")
        assertEquals(3, index.lineCount)
        assertEquals(0, index.lineOf(2))
        assertEquals(1, index.lineOf(3))
        assertEquals(2, index.lineOf(6))
        assertEquals(4, index.startOf(2))
        assertEquals(2, index.endOf(0))
        assertEquals(6, index.endOf(2))
    }
}
