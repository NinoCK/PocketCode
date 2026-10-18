package io.github.ninock.pocketcode.ui.editor

/**
 * A language the editor knows: how to colour it, how its comments look (for "Toggle comment"),
 * its usual indentation, and which typed characters open indented blocks or get auto-closed.
 */
class CodeLanguage internal constructor(
    val id: String,
    val displayName: String,
    internal val lexer: Lexer?,
    val lineComment: String? = null,
    val blockComment: Pair<String, String>? = null,
    val indentSize: Int = 4,
    val indentWithTabs: Boolean = false,
    /** Characters after which Enter indents the new line one level deeper. */
    val blockOpeners: String = "{[(",
    /** Quote characters closed automatically when typed. */
    val autoCloseQuotes: String = "\"'",
    /** Markdown: Enter continues `-` and `1.` lists. */
    val continuesLists: Boolean = false,
) {
    override fun toString() = displayName
}

private fun words(vararg groups: String): Set<String> = groups.flatMap { it.split(' ') }.filter { it.isNotEmpty() }.toHashSet()

private val CStrings = listOf(StringRule("\""), StringRule("'"))
private val CComment = "/*" to "*/"

/** All languages, and lookup by file name, shebang or Markdown fence. */
object CodeLanguages {
    val PlainText = CodeLanguage("text", "Plain text", null, blockOpeners = "", autoCloseQuotes = "")

    val Kotlin = CodeLanguage(
        "kotlin", "Kotlin",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "as break class continue do else for fun if in interface is object package return super this throw try",
                    "typealias typeof val var when while by catch constructor finally import init where",
                ),
                softKeywords = words(
                    "data enum sealed inner open value annotation companion inline infix operator tailrec lateinit external",
                    "suspend reified crossinline noinline vararg override abstract final private protected public internal const",
                    "expect actual out get set",
                ),
                constants = words("true false null"),
                strings = listOf(
                    StringRule("\"\"\"", multiline = true, escapes = false, interpolation = Interpolation.DOLLAR),
                    StringRule("\"", interpolation = Interpolation.DOLLAR),
                    StringRule("'"),
                ),
                functionKeywords = words("fun"),
                typeKeywords = words("class interface object typealias"),
                annotations = true,
                nestedComments = true,
            ),
        ),
        lineComment = "//", blockComment = CComment,
    )

    val Java = CodeLanguage(
        "java", "Java",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "abstract assert break case catch class const continue default do else enum extends final finally for goto",
                    "if implements import instanceof interface native new package private protected public return static strictfp",
                    "super switch synchronized this throw throws transient try volatile while record sealed permits yield",
                ),
                softKeywords = words("var module requires exports opens"),
                types = words("boolean byte char double float int long short void"),
                constants = words("true false null"),
                strings = listOf(StringRule("\"\"\"", multiline = true)) + CStrings,
                typeKeywords = words("class interface enum record"),
                annotations = true,
            ),
        ),
        lineComment = "//", blockComment = CComment,
    )

    val Groovy = CodeLanguage(
        "groovy", "Groovy",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "def as assert break case catch class const continue default do else enum extends final finally for goto if",
                    "implements import in instanceof interface new package return super switch this throw throws trait try while var",
                ),
                types = words("boolean byte char double float int long short void"),
                constants = words("true false null"),
                strings = listOf(
                    StringRule("'''", multiline = true),
                    StringRule("\"\"\"", multiline = true, interpolation = Interpolation.DOLLAR),
                    StringRule("\"", interpolation = Interpolation.DOLLAR),
                    StringRule("'"),
                ),
                typeKeywords = words("class interface enum trait"),
                annotations = true,
            ),
        ),
        lineComment = "//", blockComment = CComment,
    )

    private val JsKeywords = words(
        "break case catch class const continue debugger default delete do else export extends finally for function if",
        "import in instanceof let new return super switch this throw try typeof var void while with yield await static",
    )
    private val JsSoftKeywords = words("of from as get set async")
    private val JsStrings = listOf(
        StringRule("`", multiline = true, interpolation = Interpolation.DOLLAR_BRACE),
        StringRule("\""),
        StringRule("'"),
    )

    private fun javaScriptSpec(jsx: Boolean) = CodeSpec(
        keywords = JsKeywords,
        softKeywords = JsSoftKeywords,
        constants = words("true false null undefined NaN Infinity"),
        strings = JsStrings,
        functionKeywords = words("function"),
        typeKeywords = words("class"),
        annotations = true,
        regexLiterals = true,
        jsx = jsx,
        identifierExtras = "$",
    )

    private fun typeScriptSpec(jsx: Boolean) = CodeSpec(
        keywords = JsKeywords + words("enum implements interface private protected public"),
        softKeywords = JsSoftKeywords + words(
            "type namespace module declare abstract readonly keyof infer is satisfies asserts override unique accessor",
        ),
        types = words("string number boolean any unknown never void object symbol bigint"),
        constants = words("true false null undefined NaN Infinity"),
        strings = JsStrings,
        functionKeywords = words("function"),
        typeKeywords = words("class interface type enum namespace"),
        annotations = true,
        regexLiterals = true,
        jsx = jsx,
        identifierExtras = "$",
    )

    val JavaScript = CodeLanguage("javascript", "JavaScript", CodeLexer(javaScriptSpec(jsx = true)), "//", CComment, indentSize = 2, autoCloseQuotes = "\"'`")
    val TypeScript = CodeLanguage("typescript", "TypeScript", CodeLexer(typeScriptSpec(jsx = false)), "//", CComment, indentSize = 2, autoCloseQuotes = "\"'`")
    val Tsx = CodeLanguage("tsx", "TypeScript JSX", CodeLexer(typeScriptSpec(jsx = true)), "//", CComment, indentSize = 2, autoCloseQuotes = "\"'`")

    val Python = CodeLanguage(
        "python", "Python",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "and as assert async await break class continue def del elif else except finally for from global if import",
                    "in is lambda nonlocal not or pass raise return try while with yield",
                ),
                softKeywords = words("match case type"),
                types = words("int float str bool list dict set tuple bytes bytearray object complex frozenset memoryview"),
                constants = words("True False None self cls NotImplemented Ellipsis __name__ __file__"),
                builtins = words(
                    "print len range open abs all any bin callable chr dir divmod enumerate eval exec filter format getattr",
                    "globals hasattr hash help hex id input isinstance issubclass iter locals map max min next oct ord pow repr",
                    "reversed round setattr sorted sum super vars zip __import__",
                ),
                lineComments = listOf("#"),
                blockComment = null,
                strings = listOf(
                    StringRule("\"\"\"", multiline = true),
                    StringRule("'''", multiline = true),
                    StringRule("\""),
                    StringRule("'"),
                ),
                stringPrefixes = mapOf(
                    "r" to StringPrefix(raw = true),
                    "u" to StringPrefix(),
                    "b" to StringPrefix(),
                    "br" to StringPrefix(raw = true),
                    "rb" to StringPrefix(raw = true),
                    "f" to StringPrefix(interpolation = Interpolation.BRACE),
                    "fr" to StringPrefix(raw = true, interpolation = Interpolation.BRACE),
                    "rf" to StringPrefix(raw = true, interpolation = Interpolation.BRACE),
                ),
                functionKeywords = words("def"),
                typeKeywords = words("class"),
                annotations = true,
                decoratorsAtLineStart = true,
            ),
        ),
        lineComment = "#", blockOpeners = "{[(:",
    )

    val Go = CodeLanguage(
        "go", "Go",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "break case chan const continue default defer else fallthrough for func go goto if import interface map",
                    "package range return select struct switch type var",
                ),
                types = words(
                    "bool byte complex64 complex128 error float32 float64 int int8 int16 int32 int64 rune string uint uint8",
                    "uint16 uint32 uint64 uintptr any comparable",
                ),
                constants = words("true false nil iota"),
                builtins = words("append cap clear close complex copy delete imag len make max min new panic print println real recover"),
                strings = listOf(StringRule("`", multiline = true, escapes = false)) + CStrings,
                functionKeywords = words("func"),
                typeKeywords = words("type"),
                capitalizedTypes = false,
            ),
        ),
        lineComment = "//", blockComment = CComment, indentWithTabs = true,
    )

    val Rust = CodeLanguage(
        "rust", "Rust",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "as async await break const continue crate dyn else enum extern fn for if impl in let loop match mod move mut",
                    "pub ref return self Self static struct super trait type unsafe use where while union macro_rules",
                ),
                types = words("i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str"),
                constants = words("true false"),
                strings = listOf(StringRule("\"", multiline = true), StringRule("'")),
                stringPrefixes = mapOf("b" to StringPrefix()),
                functionKeywords = words("fn"),
                typeKeywords = words("struct enum trait type union"),
                nestedComments = true,
                rust = true,
            ),
        ),
        lineComment = "//", blockComment = CComment, autoCloseQuotes = "\"",
    )

    private val CKeywords = words(
        "auto break case const continue default do else enum extern for goto if inline register restrict return sizeof",
        "static struct switch typedef union volatile while _Alignas _Alignof _Atomic _Bool _Complex _Generic _Noreturn",
        "_Static_assert _Thread_local",
    )
    private val CTypes = words(
        "char double float int long short signed unsigned void bool size_t ssize_t ptrdiff_t wchar_t int8_t int16_t",
        "int32_t int64_t uint8_t uint16_t uint32_t uint64_t intptr_t uintptr_t",
    )

    val C = CodeLanguage(
        "c", "C",
        CodeLexer(
            CodeSpec(
                keywords = CKeywords,
                types = CTypes,
                constants = words("true false NULL"),
                strings = CStrings,
                typeKeywords = words("struct enum union"),
                preprocessor = true,
            ),
        ),
        lineComment = "//", blockComment = CComment,
    )

    val Cpp = CodeLanguage(
        "cpp", "C++",
        CodeLexer(
            CodeSpec(
                keywords = CKeywords + words(
                    "alignas alignof and asm bitand bitor catch class compl concept consteval constexpr constinit const_cast",
                    "co_await co_return co_yield decltype delete dynamic_cast explicit export friend mutable namespace new",
                    "noexcept not operator or override final private protected public reinterpret_cast requires static_assert",
                    "static_cast template this thread_local throw try typeid typename using virtual xor",
                ),
                types = CTypes + words("char8_t char16_t char32_t"),
                constants = words("true false nullptr NULL"),
                strings = CStrings,
                typeKeywords = words("class struct enum union namespace"),
                preprocessor = true,
                cppRawStrings = true,
            ),
        ),
        lineComment = "//", blockComment = CComment,
    )

    val CSharp = CodeLanguage(
        "csharp", "C#",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "abstract as base break case catch checked class const continue default delegate do else enum event explicit",
                    "extern finally fixed for foreach goto if implicit in interface internal is lock namespace new operator out",
                    "override params private protected public readonly ref return sealed sizeof stackalloc static struct switch",
                    "this throw try typeof unchecked unsafe using virtual volatile while async await partial record",
                ),
                softKeywords = words("var get set init value when where yield required global nameof dynamic"),
                types = words("bool byte char decimal double float int long object sbyte short string uint ulong ushort void nint nuint"),
                constants = words("true false null"),
                strings = listOf(
                    StringRule("\"\"\"", multiline = true, escapes = false),
                    StringRule("$@\"", "\"", multiline = true, escapes = false, interpolation = Interpolation.BRACE, doubledClose = true),
                    StringRule("@$\"", "\"", multiline = true, escapes = false, interpolation = Interpolation.BRACE, doubledClose = true),
                    StringRule("@\"", "\"", multiline = true, escapes = false, doubledClose = true),
                    StringRule("$\"", "\"", interpolation = Interpolation.BRACE),
                ) + CStrings,
                typeKeywords = words("class struct interface enum record"),
                preprocessor = true,
            ),
        ),
        lineComment = "//", blockComment = CComment,
    )

    val Swift = CodeLanguage(
        "swift", "Swift",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "associatedtype class deinit enum extension fileprivate func import init inout internal let open operator",
                    "private precedencegroup protocol public rethrows static struct subscript typealias var break case catch",
                    "continue default defer do else fallthrough for guard if in repeat return throw switch where while as is",
                    "self Self super throws try async await actor some any mutating nonmutating override final lazy weak unowned",
                    "convenience required optional dynamic indirect nonisolated",
                ),
                constants = words("true false nil"),
                strings = listOf(
                    StringRule("\"\"\"", multiline = true, interpolation = Interpolation.SWIFT),
                    StringRule("\"", interpolation = Interpolation.SWIFT),
                ),
                functionKeywords = words("func"),
                typeKeywords = words("class struct enum protocol extension actor typealias"),
                annotations = true,
                preprocessor = true,
                nestedComments = true,
            ),
        ),
        lineComment = "//", blockComment = CComment, autoCloseQuotes = "\"",
    )

    val Dart = CodeLanguage(
        "dart", "Dart",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "abstract as assert async await break case catch class const continue covariant default deferred do dynamic",
                    "else enum export extends extension external factory final finally for Function get hide if implements import",
                    "in interface is late library mixin new on operator part required rethrow return set show static super switch",
                    "sync this throw try typedef var void while with yield sealed base when",
                ),
                types = words("int double num bool"),
                constants = words("true false null"),
                strings = listOf(
                    StringRule("'''", multiline = true, interpolation = Interpolation.DOLLAR),
                    StringRule("\"\"\"", multiline = true, interpolation = Interpolation.DOLLAR),
                    StringRule("'", interpolation = Interpolation.DOLLAR),
                    StringRule("\"", interpolation = Interpolation.DOLLAR),
                ),
                stringPrefixes = mapOf("r" to StringPrefix(raw = true, interpolation = Interpolation.NONE)),
                typeKeywords = words("class enum mixin extension typedef"),
                annotations = true,
            ),
        ),
        lineComment = "//", blockComment = CComment, indentSize = 2,
    )

    val Php = CodeLanguage(
        "php", "PHP",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "abstract and array as break callable case catch class clone const continue declare default do echo else",
                    "elseif empty enddeclare endfor endforeach endif endswitch endwhile enum extends final finally fn for foreach",
                    "function global goto if implements include include_once instanceof insteadof interface isset list match",
                    "namespace new or print private protected public readonly require require_once return static switch throw",
                    "trait try unset use var while xor yield",
                ),
                constants = words("true false null TRUE FALSE NULL"),
                lineComments = listOf("//", "#"),
                strings = listOf(
                    StringRule("\"", multiline = true, interpolation = Interpolation.PHP),
                    StringRule("'", multiline = true),
                ),
                functionKeywords = words("function"),
                typeKeywords = words("class interface trait enum"),
                dollarVariables = Interpolation.PHP,
                phpTags = true,
            ),
        ),
        lineComment = "//", blockComment = CComment,
    )

    val Ruby = CodeLanguage(
        "ruby", "Ruby",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "BEGIN END alias and begin break case class def defined do else elsif end ensure for if in module next not",
                    "or redo rescue retry return super then undef unless until when while yield lambda proc loop",
                ),
                constants = words("true false nil self __FILE__ __LINE__"),
                builtins = words("puts print p require require_relative include extend attr_accessor attr_reader attr_writer raise private protected public"),
                lineComments = listOf("#"),
                blockComment = null,
                strings = listOf(
                    StringRule("\"", multiline = true, interpolation = Interpolation.HASH_BRACE),
                    StringRule("'", multiline = true),
                    StringRule("`", multiline = true, interpolation = Interpolation.HASH_BRACE),
                ),
                functionKeywords = words("def"),
                typeKeywords = words("class module"),
                rubySigils = true,
                regexLiterals = true,
                rubyBlockComments = true,
            ),
        ),
        lineComment = "#", indentSize = 2,
    )

    val Lua = CodeLanguage(
        "lua", "Lua",
        CodeLexer(
            CodeSpec(
                keywords = words("and break do else elseif end for function goto if in local not or repeat return then until while"),
                constants = words("true false nil"),
                builtins = words("print pairs ipairs require type tostring tonumber setmetatable getmetatable error assert pcall select next rawget rawset"),
                lineComments = listOf("--"),
                blockComment = null,
                functionKeywords = words("function"),
                luaLongBrackets = true,
                capitalizedTypes = false,
            ),
        ),
        lineComment = "--", blockComment = "--[[" to "]]", indentSize = 2,
    )

    val Sql = CodeLanguage(
        "sql", "SQL",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "select from where and or not insert into values update set delete create table drop alter add column primary",
                    "key foreign references index unique default join inner left right full outer cross on as group by order asc",
                    "desc having limit offset distinct union all exists in between like ilike is case when then else end begin",
                    "commit rollback transaction view trigger procedure function returns return declare if while database schema",
                    "grant revoke with recursive replace returning conflict do nothing constraint check cascade autoincrement",
                    "auto_increment top fetch next rows only pragma explain analyze vacuum use show describe truncate",
                ),
                types = words(
                    "int integer bigint smallint tinyint decimal numeric float real double precision char varchar text blob",
                    "boolean bool date time timestamp timestamptz datetime interval serial bigserial uuid json jsonb bytea",
                ),
                constants = words("null true false"),
                lineComments = listOf("--"),
                strings = listOf(
                    StringRule("'", multiline = true, escapes = false, doubledClose = true),
                    StringRule("\"", escapes = false),
                    StringRule("`", escapes = false),
                ),
                caseInsensitive = true,
                capitalizedTypes = false,
            ),
        ),
        lineComment = "--", blockComment = CComment, blockOpeners = "(",
    )

    private val ShellKeywords = words(
        "if then else elif fi for while until do done case esac in function select return local export readonly declare",
        "typeset unset shift break continue exit source alias time trap",
    )
    private val ShellCommands = words(
        "echo printf read cd pwd test eval exec let getopts wait kill sleep cat ls grep egrep sed awk find xargs mkdir rmdir",
        "rm cp mv ln chmod chown touch head tail sort uniq wc cut tr tee curl wget git npm npx node yarn pnpm python python3",
        "pip pip3 apt apt-get sudo tar gzip unzip zip ssh scp rsync make gradle java kotlinc go cargo docker set env",
    )
    private val ShellStrings = listOf(
        StringRule("\"", multiline = true, interpolation = Interpolation.SHELL),
        StringRule("'", multiline = true, escapes = false),
        StringRule("`", multiline = true, interpolation = Interpolation.SHELL),
    )

    val Shell = CodeLanguage(
        "shell", "Shell",
        CodeLexer(
            CodeSpec(
                keywords = ShellKeywords,
                constants = words("true false"),
                builtins = ShellCommands,
                lineComments = listOf("#"),
                blockComment = null,
                strings = ShellStrings,
                functionKeywords = words("function"),
                dollarVariables = Interpolation.SHELL,
                capitalizedTypes = false,
                identifierExtras = "-",
                wordComments = true,
                heredocs = true,
                assignments = true,
            ),
        ),
        lineComment = "#", indentSize = 2, blockOpeners = "{[(",
    )

    val PowerShell = CodeLanguage(
        "powershell", "PowerShell",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "begin break catch class continue data do dynamicparam else elseif end enum exit filter finally for foreach",
                    "from function hidden if in param process return static switch throw trap try until using var while",
                ),
                lineComments = listOf("#"),
                blockComment = "<#" to "#>",
                strings = listOf(
                    StringRule("\"", multiline = true, escapes = false, interpolation = Interpolation.SHELL),
                    StringRule("'", multiline = true, escapes = false, doubledClose = true),
                ),
                functionKeywords = words("function filter"),
                dollarVariables = Interpolation.SHELL,
                caseInsensitive = true,
                capitalizedTypes = false,
                identifierExtras = "-",
            ),
        ),
        lineComment = "#", blockComment = "<#" to "#>",
    )

    val Dockerfile = CodeLanguage(
        "dockerfile", "Dockerfile",
        CodeLexer(
            CodeSpec(
                keywords = words(
                    "from run cmd label maintainer expose env add copy entrypoint volume user workdir arg onbuild stopsignal",
                    "healthcheck shell",
                ),
                lineComments = listOf("#"),
                blockComment = null,
                strings = ShellStrings,
                dollarVariables = Interpolation.SHELL,
                caseInsensitive = true,
                keywordsAtLineStart = true,
                capitalizedTypes = false,
                identifierExtras = "-",
                wordComments = true,
                heredocs = true,
                assignments = true,
            ),
        ),
        lineComment = "#", blockOpeners = "[",
    )

    val Makefile = CodeLanguage(
        "makefile", "Makefile",
        CodeLexer(
            CodeSpec(
                keywords = words("ifeq ifneq ifdef ifndef else endif include define endef export override unexport vpath sinclude"),
                lineComments = listOf("#"),
                blockComment = null,
                strings = listOf(StringRule("\"", interpolation = Interpolation.MAKE), StringRule("'", escapes = false)),
                dollarVariables = Interpolation.MAKE,
                capitalizedTypes = false,
                identifierExtras = "-",
                makeRules = true,
            ),
        ),
        lineComment = "#", indentWithTabs = true, blockOpeners = "",
    )

    val CMake = CodeLanguage(
        "cmake", "CMake",
        CodeLexer(
            CodeSpec(
                keywords = words("if elseif else endif foreach endforeach while endwhile function endfunction macro endmacro return break continue"),
                constants = words("on off true false yes no"),
                lineComments = listOf("#"),
                blockComment = null,
                strings = listOf(StringRule("\"", multiline = true, interpolation = Interpolation.SHELL)),
                dollarVariables = Interpolation.SHELL,
                caseInsensitive = true,
            ),
        ),
        lineComment = "#", indentSize = 2, blockOpeners = "(",
    )

    /** `.gitignore`-style files: only `#` comments. */
    val Ignore = CodeLanguage(
        "ignore", "Ignore file",
        CodeLexer(
            CodeSpec(
                keywords = emptySet(),
                lineComments = listOf("#"),
                blockComment = null,
                strings = emptyList(),
                capitalizedTypes = false,
            ),
        ),
        lineComment = "#", blockOpeners = "", autoCloseQuotes = "",
    )

    val Html = CodeLanguage("html", "HTML", MarkupLexer(html = true), blockComment = "<!--" to "-->", indentSize = 2, blockOpeners = "{[(")
    val Xml = CodeLanguage("xml", "XML", MarkupLexer(html = false), blockComment = "<!--" to "-->", indentSize = 2, blockOpeners = "")
    val Css = CodeLanguage("css", "CSS", CssLexer(lineComments = false), blockComment = CComment, indentSize = 2)
    val Scss = CodeLanguage("scss", "SCSS", CssLexer(lineComments = true), "//", CComment, indentSize = 2)
    val Less = CodeLanguage("less", "Less", CssLexer(lineComments = true), "//", CComment, indentSize = 2)
    val Json = CodeLanguage("json", "JSON", JsonLexer, "//", CComment, indentSize = 2, autoCloseQuotes = "\"")
    val Yaml = CodeLanguage("yaml", "YAML", YamlLexer, "#", indentSize = 2, blockOpeners = ":[{")
    val Toml = CodeLanguage("toml", "TOML", IniLexer(toml = true, properties = false), "#", blockOpeners = "[{")
    val Ini = CodeLanguage("ini", "INI", IniLexer(toml = false, properties = false), "#", blockOpeners = "")
    val Properties = CodeLanguage("properties", "Properties", IniLexer(toml = false, properties = true), "#", blockOpeners = "", autoCloseQuotes = "")
    val Env = CodeLanguage("env", "Environment", IniLexer(toml = false, properties = false), "#", blockOpeners = "")
    val Markdown = CodeLanguage(
        "markdown", "Markdown", MarkdownLexer, blockComment = "<!--" to "-->", indentSize = 2,
        blockOpeners = "", autoCloseQuotes = "", continuesLists = true,
    )
    val Diff = CodeLanguage("diff", "Diff", DiffLexer, blockOpeners = "", autoCloseQuotes = "")

    val all: List<CodeLanguage> = listOf(
        PlainText, Kotlin, Java, Groovy, JavaScript, TypeScript, Tsx, Python, Go, Rust, C, Cpp, CSharp, Swift, Dart, Php,
        Ruby, Lua, Sql, Shell, PowerShell, Dockerfile, Makefile, CMake, Ignore, Html, Xml, Css, Scss, Less, Json, Yaml,
        Toml, Ini, Properties, Env, Markdown, Diff,
    )

    private val byExtension: Map<String, CodeLanguage> = buildMap {
        fun map(language: CodeLanguage, vararg extensions: String) = extensions.forEach { put(it, language) }
        map(Kotlin, "kt", "kts")
        map(Java, "java")
        map(Groovy, "groovy", "gradle", "gvy")
        map(JavaScript, "js", "mjs", "cjs", "jsx")
        map(TypeScript, "ts", "mts", "cts")
        map(Tsx, "tsx")
        map(Python, "py", "pyw", "pyi")
        map(Go, "go")
        map(Rust, "rs")
        map(C, "c", "h")
        map(Cpp, "cpp", "cc", "cxx", "hpp", "hh", "hxx", "ino")
        map(CSharp, "cs", "csx")
        map(Swift, "swift")
        map(Dart, "dart")
        map(Php, "php", "phtml")
        map(Ruby, "rb", "rake", "gemspec", "ru")
        map(Lua, "lua")
        map(Sql, "sql")
        map(Shell, "sh", "bash", "zsh", "ksh", "fish", "command")
        map(PowerShell, "ps1", "psm1", "psd1")
        map(Dockerfile, "dockerfile")
        map(Makefile, "mk", "mak")
        map(CMake, "cmake")
        map(Html, "html", "htm", "xhtml", "vue", "svelte", "astro", "ejs", "hbs")
        map(Xml, "xml", "svg", "plist", "xsd", "xsl", "xslt", "xaml", "csproj", "fxml", "storyboard", "xib", "rss", "atom")
        map(Css, "css")
        map(Scss, "scss", "sass")
        map(Less, "less")
        map(Json, "json", "jsonc", "json5", "webmanifest", "geojson", "har", "ipynb")
        map(Yaml, "yaml", "yml")
        map(Toml, "toml")
        map(Ini, "ini", "cfg", "conf", "editorconfig", "gitconfig", "service", "desktop")
        map(Properties, "properties")
        map(Env, "env")
        map(Markdown, "md", "markdown", "mdx", "mkd")
        map(Diff, "diff", "patch")
    }

    private val byFileName: Map<String, CodeLanguage> = buildMap {
        fun map(language: CodeLanguage, vararg names: String) = names.forEach { put(it, language) }
        map(Dockerfile, "dockerfile", "containerfile")
        map(Makefile, "makefile", "gnumakefile")
        map(CMake, "cmakelists.txt")
        map(Ruby, "gemfile", "rakefile", "podfile", "vagrantfile", "brewfile", "fastfile", "appfile", "guardfile")
        map(Groovy, "jenkinsfile")
        map(Shell, ".bashrc", ".bash_profile", ".bash_logout", ".zshrc", ".zprofile", ".profile", ".bash_aliases", "pkgbuild")
        map(Ignore, ".gitignore", ".dockerignore", ".npmignore", ".eslintignore", ".prettierignore", ".gitattributes", "codeowners", "requirements.txt")
        map(Json, ".prettierrc", ".eslintrc", ".babelrc", ".swcrc", "composer.lock")
        map(Ini, ".npmrc", ".yarnrc", ".gitmodules", ".flake8", ".pylintrc", "setup.cfg", "tox.ini")
        map(Toml, "cargo.lock", "pipfile", "poetry.lock")
        map(Yaml, ".clang-format", ".prettierrc.yml")
        map(Properties, "gradle.properties", "local.properties", "gradle-wrapper.properties")
    }

    /** The language for a file, by name and extension, or by its shebang line for scripts without one. */
    fun forFile(fileName: String, content: CharSequence? = null): CodeLanguage {
        val name = fileName.substringAfterLast('/').lowercase()
        byFileName[name]?.let { return it }
        if (name.startsWith(".env")) return Env
        if (name.startsWith("dockerfile")) return Dockerfile
        if (name.startsWith("makefile")) return Makefile
        val extension = name.substringAfterLast('.', "")
        if (extension.isNotEmpty() && name != ".$extension") byExtension[extension]?.let { return it }
        if (content != null && content.startsWithAt("#!", 0)) {
            val shebang = content.subSequence(0, content.lineEndFrom(0, minOf(content.length, 200))).toString()
            return when {
                "python" in shebang -> Python
                "node" in shebang || "deno" in shebang || "bun" in shebang -> JavaScript
                "ruby" in shebang -> Ruby
                "php" in shebang -> Php
                "lua" in shebang -> Lua
                "pwsh" in shebang -> PowerShell
                "sh" in shebang -> Shell
                else -> PlainText
            }
        }
        return PlainText
    }

    private val fenceAliases: Map<String, CodeLanguage> = buildMap {
        fun map(language: CodeLanguage, vararg names: String) = names.forEach { put(it, language) }
        map(Kotlin, "kotlin", "kt", "kts")
        map(Java, "java")
        map(Groovy, "groovy", "gradle")
        map(JavaScript, "js", "javascript", "jsx", "mjs", "cjs", "node")
        map(TypeScript, "ts", "typescript")
        map(Tsx, "tsx")
        map(Python, "py", "python", "python3", "py3")
        map(Go, "go", "golang")
        map(Rust, "rs", "rust")
        map(C, "c", "h")
        map(Cpp, "cpp", "c++", "cc", "cxx", "hpp")
        map(CSharp, "cs", "csharp", "c#")
        map(Swift, "swift")
        map(Dart, "dart")
        map(Php, "php")
        map(Ruby, "rb", "ruby")
        map(Lua, "lua")
        map(Sql, "sql", "mysql", "postgres", "postgresql", "psql", "sqlite", "plsql")
        map(Shell, "sh", "bash", "shell", "zsh", "console", "terminal", "shellsession")
        map(PowerShell, "ps1", "powershell", "pwsh")
        map(Dockerfile, "dockerfile", "docker")
        map(Makefile, "make", "makefile", "mk")
        map(CMake, "cmake")
        map(Html, "html", "htm", "xhtml", "vue", "svelte")
        map(Xml, "xml", "svg", "plist")
        map(Css, "css")
        map(Scss, "scss", "sass")
        map(Less, "less")
        map(Json, "json", "jsonc", "json5")
        map(Yaml, "yaml", "yml")
        map(Toml, "toml")
        map(Ini, "ini", "cfg", "conf")
        map(Properties, "properties")
        map(Env, "env", "dotenv")
        map(Markdown, "md", "markdown")
        map(Diff, "diff", "patch")
    }

    /** The language named by a Markdown code fence's info string (` ```kotlin title="x" `). */
    fun forFence(info: String): CodeLanguage? {
        val name = info.trim().substringBefore(' ').substringBefore('{').trim().lowercase()
        return if (name.isEmpty()) null else fenceAliases[name]
    }
}
