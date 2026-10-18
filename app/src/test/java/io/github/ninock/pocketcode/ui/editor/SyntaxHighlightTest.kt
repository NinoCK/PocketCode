package io.github.ninock.pocketcode.ui.editor

import io.github.ninock.pocketcode.ui.editor.TokenKind.ANNOTATION
import io.github.ninock.pocketcode.ui.editor.TokenKind.ATTRIBUTE
import io.github.ninock.pocketcode.ui.editor.TokenKind.COMMENT
import io.github.ninock.pocketcode.ui.editor.TokenKind.CONSTANT
import io.github.ninock.pocketcode.ui.editor.TokenKind.ESCAPE
import io.github.ninock.pocketcode.ui.editor.TokenKind.FUNCTION
import io.github.ninock.pocketcode.ui.editor.TokenKind.HEADING
import io.github.ninock.pocketcode.ui.editor.TokenKind.KEYWORD
import io.github.ninock.pocketcode.ui.editor.TokenKind.META
import io.github.ninock.pocketcode.ui.editor.TokenKind.NUMBER
import io.github.ninock.pocketcode.ui.editor.TokenKind.PROPERTY
import io.github.ninock.pocketcode.ui.editor.TokenKind.REGEX
import io.github.ninock.pocketcode.ui.editor.TokenKind.STRING
import io.github.ninock.pocketcode.ui.editor.TokenKind.STRONG
import io.github.ninock.pocketcode.ui.editor.TokenKind.TAG
import io.github.ninock.pocketcode.ui.editor.TokenKind.TYPE
import io.github.ninock.pocketcode.ui.editor.TokenKind.VARIABLE
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlightTest {
    /** Every token as `KIND:text`, in order. */
    private fun tokens(language: CodeLanguage, text: String): List<String> {
        val list = highlight(text, language)
        return (0 until list.size).map { "${list.kind(it)}:${text.substring(list.start(it), list.end(it))}" }
    }

    private fun kindOf(language: CodeLanguage, text: String, fragment: String, occurrence: Int = 0): TokenKind? {
        var at = -1
        repeat(occurrence + 1) { at = text.indexOf(fragment, at + 1) }
        check(at >= 0) { "$fragment not in $text" }
        return highlight(text, language).kindAt(at)
    }

    @Test
    fun kotlinDeclarationsCallsAndTemplates() {
        val text = "fun main() {\n    val s = \"hi \$name and \${user.id}\"\n    println(s)\n}"
        val result = tokens(CodeLanguages.Kotlin, text)
        assertEquals(
            listOf(
                "KEYWORD:fun", "FUNCTION:main", "KEYWORD:val", "STRING:\"hi ", "VARIABLE:\$name", "STRING: and ",
                "KEYWORD:\${", "KEYWORD:}", "STRING:\"", "FUNCTION:println",
            ),
            result,
        )
    }

    @Test
    fun kotlinSoftKeywordsOnlyBeforeAnotherWord() {
        assertEquals(KEYWORD, kindOf(CodeLanguages.Kotlin, "data class User(val id: Int)", "data"))
        assertEquals(TYPE, kindOf(CodeLanguages.Kotlin, "data class User(val id: Int)", "User"))
        assertEquals(null, kindOf(CodeLanguages.Kotlin, "val data = load()", "data"))
        assertEquals(null, kindOf(CodeLanguages.Kotlin, "show(data)", "data"))
        assertEquals(KEYWORD, kindOf(CodeLanguages.Kotlin, "private val x = 1", "private"))
    }

    @Test
    fun kotlinAnnotationsCommentsAndConstants() {
        val text = "@Composable fun Foo() = null /* a /* nested */ still */ MAX_SIZE"
        assertEquals(ANNOTATION, kindOf(CodeLanguages.Kotlin, text, "@Composable"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Kotlin, text, "Foo"))
        assertEquals(CONSTANT, kindOf(CodeLanguages.Kotlin, text, "null"))
        assertEquals(COMMENT, kindOf(CodeLanguages.Kotlin, text, "still"))
        assertEquals(CONSTANT, kindOf(CodeLanguages.Kotlin, text, "MAX_SIZE"))
    }

    @Test
    fun extensionFunctionReceiverIsAType() {
        val text = "fun String.shout(): String = uppercase()"
        assertEquals(TYPE, kindOf(CodeLanguages.Kotlin, text, "String"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Kotlin, text, "shout"))
    }

    @Test
    fun genericFunctionNameIsNotTheTypeParameter() {
        val text = "fun <T> List<T>.second(): T = this[1]"
        assertEquals(TYPE, kindOf(CodeLanguages.Kotlin, text, "T"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Kotlin, text, "second"))
    }

    @Test
    fun javaScriptRegexVersusDivision() {
        assertEquals(REGEX, kindOf(CodeLanguages.JavaScript, "const r = /a+b\\/c/gi;", "/a+b"))
        val division = "const x = a / b / c;"
        assertFalse(tokens(CodeLanguages.JavaScript, division).any { it.startsWith("REGEX") })
        assertEquals(REGEX, kindOf(CodeLanguages.JavaScript, "return /x/.test(s)", "/x/"))
    }

    @Test
    fun javaScriptTemplateLiteralInterpolatesCode() {
        val text = "`total: \${count + 1} items`"
        assertEquals(
            listOf("STRING:`total: ", "KEYWORD:\${", "NUMBER:1", "KEYWORD:}", "STRING: items`"),
            tokens(CodeLanguages.JavaScript, text),
        )
    }

    @Test
    fun jsxTagsAttributesAndExpressions() {
        val text = "return <Card className=\"big\" onClick={() => go(1)}>Hi {name}</Card>;"
        assertEquals(TYPE, kindOf(CodeLanguages.JavaScript, text, "Card"))
        assertEquals(ATTRIBUTE, kindOf(CodeLanguages.JavaScript, text, "className"))
        assertEquals(STRING, kindOf(CodeLanguages.JavaScript, text, "\"big\""))
        assertEquals(FUNCTION, kindOf(CodeLanguages.JavaScript, text, "go"))
        assertEquals(null, kindOf(CodeLanguages.JavaScript, text, "Hi"))
        assertEquals(TYPE, kindOf(CodeLanguages.JavaScript, text, "Card", occurrence = 1))
        assertFalse(tokens(CodeLanguages.JavaScript, "if (a < b && c > d) {}").any { it.startsWith("TAG") })
    }

    @Test
    fun typeScriptTypesAndSoftKeywords() {
        val text = "type Id = string; const type = 1; interface Props { id: Id }"
        assertEquals(KEYWORD, kindOf(CodeLanguages.TypeScript, text, "type"))
        assertEquals(null, kindOf(CodeLanguages.TypeScript, text, "type", occurrence = 1))
        assertEquals(TYPE, kindOf(CodeLanguages.TypeScript, text, "string"))
        assertEquals(TYPE, kindOf(CodeLanguages.TypeScript, text, "Props"))
    }

    @Test
    fun memberAccessIsNotAKeyword() {
        assertEquals(FUNCTION, kindOf(CodeLanguages.JavaScript, "promise.catch(handle)", "catch"))
        assertEquals(null, kindOf(CodeLanguages.JavaScript, "options.default", "default"))
    }

    @Test
    fun pythonStringsDecoratorsAndDefs() {
        val text = "@app.route(\"/\")\ndef index(self, n=None):\n    \"\"\"Doc\n    more\"\"\"\n    return f\"n={n!r} {{x}}\"\n"
        assertEquals(ANNOTATION, kindOf(CodeLanguages.Python, text, "@app.route"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Python, text, "index"))
        assertEquals(CONSTANT, kindOf(CodeLanguages.Python, text, "self"))
        assertEquals(CONSTANT, kindOf(CodeLanguages.Python, text, "None"))
        assertEquals(STRING, kindOf(CodeLanguages.Python, text, "more"))
        assertEquals(KEYWORD, kindOf(CodeLanguages.Python, text, "{n"))
        assertEquals(ESCAPE, kindOf(CodeLanguages.Python, text, "{{"))
        assertEquals(null, kindOf(CodeLanguages.Python, "x = a @ b", "@"))
    }

    @Test
    fun shellVariablesCommentsAndHeredocs() {
        val text = "NAME=\"\$HOME/app\" # where\necho \${NAME} a#b\ncat <<EOF > out\nhello \$x\nEOF\nls"
        assertEquals(VARIABLE, kindOf(CodeLanguages.Shell, text, "NAME"))
        assertEquals(VARIABLE, kindOf(CodeLanguages.Shell, text, "\$HOME"))
        assertEquals(COMMENT, kindOf(CodeLanguages.Shell, text, "# where"))
        assertEquals(VARIABLE, kindOf(CodeLanguages.Shell, text, "\${NAME}"))
        assertEquals(null, kindOf(CodeLanguages.Shell, text, "#b"))
        assertEquals(STRING, kindOf(CodeLanguages.Shell, text, "hello"))
        assertEquals(STRING, kindOf(CodeLanguages.Shell, text, "EOF", occurrence = 1))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Shell, text, "ls"))
    }

    @Test
    fun rustLifetimesMacrosAndRawStrings() {
        val text = "fn first<'a>(x: &'a str) -> char { println!(\"{}\", r#\"raw \"q\"\"#); 'c' }"
        assertEquals(FUNCTION, kindOf(CodeLanguages.Rust, text, "first"))
        assertEquals(ANNOTATION, kindOf(CodeLanguages.Rust, text, "'a"))
        assertEquals(TYPE, kindOf(CodeLanguages.Rust, text, "str"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Rust, text, "println!"))
        assertEquals(STRING, kindOf(CodeLanguages.Rust, text, "raw"))
        assertEquals(STRING, kindOf(CodeLanguages.Rust, text, "'c'"))
    }

    @Test
    fun goReceiverMethodsAndRawStrings() {
        val text = "func (r *Repo) Load(id int) error { return `raw\nline` }"
        assertEquals(null, kindOf(CodeLanguages.Go, text, "r "))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Go, text, "Load"))
        assertEquals(TYPE, kindOf(CodeLanguages.Go, text, "error"))
        assertEquals(STRING, kindOf(CodeLanguages.Go, text, "line"))
    }

    @Test
    fun cPreprocessorAndIncludes() {
        val text = "#include <stdio.h>\nint main(void) { return 0; }"
        assertEquals(META, kindOf(CodeLanguages.C, text, "#include"))
        assertEquals(STRING, kindOf(CodeLanguages.C, text, "<stdio.h>"))
        assertEquals(TYPE, kindOf(CodeLanguages.C, text, "int"))
    }

    @Test
    fun unterminatedStringStopsAtLineEnd() {
        val text = "val a = \"oops\nval b = 2"
        assertEquals(KEYWORD, kindOf(CodeLanguages.Kotlin, text, "val", occurrence = 1))
        assertEquals(NUMBER, kindOf(CodeLanguages.Kotlin, text, "2"))
    }

    @Test
    fun jsonKeysValuesAndLiterals() {
        val text = "{\"name\": \"app\\n\", \"n\": -1.5e3, \"ok\": true, // note\n \"x\": null}"
        assertEquals(PROPERTY, kindOf(CodeLanguages.Json, text, "\"name\""))
        assertEquals(STRING, kindOf(CodeLanguages.Json, text, "\"app"))
        assertEquals(ESCAPE, kindOf(CodeLanguages.Json, text, "\\n"))
        assertEquals(NUMBER, kindOf(CodeLanguages.Json, text, "-1.5e3"))
        assertEquals(CONSTANT, kindOf(CodeLanguages.Json, text, "true"))
        assertEquals(COMMENT, kindOf(CodeLanguages.Json, text, "// note"))
    }

    @Test
    fun yamlKeysScalarsAndBlocks() {
        val text = "# config\nname: \"web\"\nreplicas: 3\nenabled: yes\nitems:\n  - first: a # note\n  - b\nscript: |\n  echo hi\n  done\nnext: [1, two]\n"
        assertEquals(COMMENT, kindOf(CodeLanguages.Yaml, text, "# config"))
        assertEquals(PROPERTY, kindOf(CodeLanguages.Yaml, text, "name"))
        assertEquals(STRING, kindOf(CodeLanguages.Yaml, text, "\"web\""))
        assertEquals(NUMBER, kindOf(CodeLanguages.Yaml, text, "3"))
        assertEquals(CONSTANT, kindOf(CodeLanguages.Yaml, text, "yes"))
        assertEquals(KEYWORD, kindOf(CodeLanguages.Yaml, text, "- first"))
        assertEquals(PROPERTY, kindOf(CodeLanguages.Yaml, text, "first"))
        assertEquals(COMMENT, kindOf(CodeLanguages.Yaml, text, "# note"))
        assertEquals(STRING, kindOf(CodeLanguages.Yaml, text, "echo hi"))
        assertEquals(PROPERTY, kindOf(CodeLanguages.Yaml, text, "next"))
        assertEquals(NUMBER, kindOf(CodeLanguages.Yaml, text, "1,"))
        assertEquals(STRING, kindOf(CodeLanguages.Yaml, text, "two"))
    }

    @Test
    fun htmlWithEmbeddedScriptAndStyle() {
        val text = "<!-- c --><div class=\"a\" hidden>&amp;</div><script>let x = 1</script><style>p { color: red }</style>"
        assertEquals(COMMENT, kindOf(CodeLanguages.Html, text, "<!-- c -->"))
        assertEquals(TAG, kindOf(CodeLanguages.Html, text, "div"))
        assertEquals(ATTRIBUTE, kindOf(CodeLanguages.Html, text, "class"))
        assertEquals(STRING, kindOf(CodeLanguages.Html, text, "\"a\""))
        assertEquals(ATTRIBUTE, kindOf(CodeLanguages.Html, text, "hidden"))
        assertEquals(TokenKind.ESCAPE, kindOf(CodeLanguages.Html, text, "&amp;"))
        assertEquals(KEYWORD, kindOf(CodeLanguages.Html, text, "let"))
        assertEquals(NUMBER, kindOf(CodeLanguages.Html, text, "1"))
        assertEquals(ATTRIBUTE, kindOf(CodeLanguages.Html, text, "color"))
        assertEquals(TAG, kindOf(CodeLanguages.Html, text, "script", occurrence = 1))
    }

    @Test
    fun cssSelectorsPropertiesAndValues() {
        val text = "@media (max-width: 600px) {\n  .card:hover > a { color: #fff; margin: 0 10px !important; }\n}\n:root { --gap: 4px; padding: var(--gap); }"
        assertEquals(KEYWORD, kindOf(CodeLanguages.Css, text, "@media"))
        assertEquals(ATTRIBUTE, kindOf(CodeLanguages.Css, text, "max-width"))
        assertEquals(NUMBER, kindOf(CodeLanguages.Css, text, "600px"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Css, text, ".card"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Css, text, ":hover"))
        assertEquals(TAG, kindOf(CodeLanguages.Css, text, "a {"))
        assertEquals(ATTRIBUTE, kindOf(CodeLanguages.Css, text, "color"))
        assertEquals(NUMBER, kindOf(CodeLanguages.Css, text, "#fff"))
        assertEquals(NUMBER, kindOf(CodeLanguages.Css, text, "10px"))
        assertEquals(KEYWORD, kindOf(CodeLanguages.Css, text, "!important"))
        assertEquals(VARIABLE, kindOf(CodeLanguages.Css, text, "--gap"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Css, text, "var"))
    }

    @Test
    fun scssNestedRulesAreSelectors() {
        val text = ".a {\n  color: red;\n  &:hover { color: blue; }\n  .b { margin: 0; }\n}"
        assertEquals(KEYWORD, kindOf(CodeLanguages.Scss, text, "&"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Scss, text, ".b"))
        assertEquals(ATTRIBUTE, kindOf(CodeLanguages.Scss, text, "margin"))
    }

    @Test
    fun markdownBlocksInlineAndFencedCode() {
        val text = "---\ntitle: Notes\n---\n# Title\nSome **bold** and `code` and [link](http://x.y).\n- item\n```kotlin\nval x = 1\n```\nsnake_case_word"
        assertEquals(PROPERTY, kindOf(CodeLanguages.Markdown, text, "title"))
        assertEquals(HEADING, kindOf(CodeLanguages.Markdown, text, "# Title"))
        assertEquals(STRONG, kindOf(CodeLanguages.Markdown, text, "**bold**"))
        assertEquals(STRING, kindOf(CodeLanguages.Markdown, text, "`code`"))
        assertEquals(TokenKind.LINK, kindOf(CodeLanguages.Markdown, text, "[link]"))
        assertEquals(KEYWORD, kindOf(CodeLanguages.Markdown, text, "- item"))
        assertEquals(KEYWORD, kindOf(CodeLanguages.Markdown, text, "val"))
        assertEquals(null, kindOf(CodeLanguages.Markdown, text, "_case"))
    }

    @Test
    fun tomlIniAndProperties() {
        val toml = "[package]\nname = \"app\" # c\nversion = 3\ndeps = { serde = \"1\" }\n"
        assertEquals(TYPE, kindOf(CodeLanguages.Toml, toml, "[package]"))
        assertEquals(PROPERTY, kindOf(CodeLanguages.Toml, toml, "name"))
        assertEquals(STRING, kindOf(CodeLanguages.Toml, toml, "\"app\""))
        assertEquals(COMMENT, kindOf(CodeLanguages.Toml, toml, "# c"))
        assertEquals(NUMBER, kindOf(CodeLanguages.Toml, toml, "3"))
        assertEquals(PROPERTY, kindOf(CodeLanguages.Toml, toml, "serde"))
        val env = "export API_URL=\${HOST}/v1\nDEBUG=true"
        assertEquals(KEYWORD, kindOf(CodeLanguages.Env, env, "export"))
        assertEquals(PROPERTY, kindOf(CodeLanguages.Env, env, "API_URL"))
        assertEquals(VARIABLE, kindOf(CodeLanguages.Env, env, "\${HOST}"))
        assertEquals(CONSTANT, kindOf(CodeLanguages.Env, env, "true"))
        assertEquals(PROPERTY, kindOf(CodeLanguages.Properties, "org.gradle.jvmargs=-Xmx3g", "org.gradle.jvmargs"))
    }

    @Test
    fun diffLines() {
        val text = "--- a/x\n+++ b/x\n@@ -1 +1 @@\n-old\n+new\n same"
        assertEquals(TokenKind.DELETED, kindOf(CodeLanguages.Diff, text, "-old"))
        assertEquals(TokenKind.INSERTED, kindOf(CodeLanguages.Diff, text, "+new"))
        assertEquals(META, kindOf(CodeLanguages.Diff, text, "@@"))
        assertEquals(null, kindOf(CodeLanguages.Diff, text, "same"))
    }

    @Test
    fun makefileAndDockerfile() {
        val make = "CC := gcc\nbuild: main.o\n\t\$(CC) -o app \$@\n"
        assertEquals(PROPERTY, kindOf(CodeLanguages.Makefile, make, "CC"))
        assertEquals(FUNCTION, kindOf(CodeLanguages.Makefile, make, "build"))
        assertEquals(VARIABLE, kindOf(CodeLanguages.Makefile, make, "\$(CC)"))
        assertEquals(VARIABLE, kindOf(CodeLanguages.Makefile, make, "\$@"))
        val docker = "FROM node:20 AS build\nRUN npm run build # compile\n"
        assertEquals(KEYWORD, kindOf(CodeLanguages.Dockerfile, docker, "FROM"))
        assertEquals(KEYWORD, kindOf(CodeLanguages.Dockerfile, docker, "RUN"))
        assertEquals(null, kindOf(CodeLanguages.Dockerfile, docker, "run "))
        assertEquals(COMMENT, kindOf(CodeLanguages.Dockerfile, docker, "# compile"))
    }

    @Test
    fun languageDetection() {
        assertSame(CodeLanguages.Kotlin, CodeLanguages.forFile("app/build.gradle.kts"))
        assertSame(CodeLanguages.Groovy, CodeLanguages.forFile("build.gradle"))
        assertSame(CodeLanguages.Dockerfile, CodeLanguages.forFile("docker/Dockerfile"))
        assertSame(CodeLanguages.Dockerfile, CodeLanguages.forFile("Dockerfile.dev"))
        assertSame(CodeLanguages.Makefile, CodeLanguages.forFile("Makefile"))
        assertSame(CodeLanguages.CMake, CodeLanguages.forFile("CMakeLists.txt"))
        assertSame(CodeLanguages.Env, CodeLanguages.forFile(".env.local"))
        assertSame(CodeLanguages.Ignore, CodeLanguages.forFile(".gitignore"))
        assertSame(CodeLanguages.Markdown, CodeLanguages.forFile("README.MD"))
        assertSame(CodeLanguages.Tsx, CodeLanguages.forFile("src/App.tsx"))
        assertSame(CodeLanguages.Python, CodeLanguages.forFile("bin/tool", "#!/usr/bin/env python3\nprint(1)"))
        assertSame(CodeLanguages.Shell, CodeLanguages.forFile("run", "#!/bin/bash\necho"))
        assertSame(CodeLanguages.PlainText, CodeLanguages.forFile("notes.txt"))
        assertSame(CodeLanguages.PlainText, CodeLanguages.forFile("LICENSE"))
        assertSame(CodeLanguages.Kotlin, CodeLanguages.forFence("kotlin title=\"x\""))
        assertSame(CodeLanguages.Shell, CodeLanguages.forFence("bash"))
        assertEquals(null, CodeLanguages.forFence(""))
    }

    @Test
    fun plainTextHasNoTokens() {
        assertEquals(0, highlight("fun main() {}", CodeLanguages.PlainText).size)
    }

    /** Half-typed code is the editor's normal state: no lexer may throw or emit bad ranges on any input. */
    @Test
    fun lexersSurviveArbitraryInput() {
        val alphabet = "\"'`\$#{}()[]<>/\\*\n\t -=:;@!?abcXYZ019_.,|&%^~rfb"
        val samples = listOf(
            "fun f() { val s = \"\${a}\" }", "<div a=\"b\">{x}</div>", "```py\nx = f'{a}'\n```", "key: [1, {a: b}]",
            "#!/bin/sh\ncat <<-EOF\n\tx\n\tEOF", "a /* b", "r#\"x", "R\"(abc", "--[[ x", "=begin\nx", "\$(foo \${bar",
        )
        val random = Random(42)
        for (language in CodeLanguages.all) {
            val inputs = samples.flatMap { sample -> sample.indices.map { sample.substring(0, it) } } +
                List(1500) { String(CharArray(random.nextInt(0, 160)) { alphabet[random.nextInt(alphabet.length)] }) }
            for (input in inputs) {
                val list = try {
                    highlight(input, language)
                } catch (e: Throwable) {
                    throw AssertionError("${language.id} failed on ${input.replace("\n", "\\n")}", e)
                }
                var previousEnd = 0
                for (i in 0 until list.size) {
                    assertTrue("${language.id}: bad range on $input", list.start(i) >= previousEnd && list.end(i) > list.start(i) && list.end(i) <= input.length)
                    previousEnd = list.end(i)
                }
            }
        }
    }
}
