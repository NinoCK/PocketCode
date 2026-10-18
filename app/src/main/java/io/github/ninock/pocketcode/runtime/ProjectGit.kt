package io.github.ninock.pocketcode.runtime

import io.github.ninock.pocketcode.model.ChangeItem
import io.github.ninock.pocketcode.model.DiffLine
import io.github.ninock.pocketcode.model.DiffLineType
import org.json.JSONArray
import org.json.JSONObject

/** One entry of `git status`: [index] and [worktree] are the two porcelain status letters. */
data class GitFileChange(
    val path: String,
    val index: Char,
    val worktree: Char,
    val origPath: String? = null,
) {
    val untracked: Boolean get() = index == '?'
    val conflicted: Boolean get() = index == 'U' || worktree == 'U' || (index == 'A' && worktree == 'A') || (index == 'D' && worktree == 'D')
    val staged: Boolean get() = !untracked && !conflicted && index != ' '
    val unstaged: Boolean get() = untracked || conflicted || worktree != ' '

    /** The letter shown in the list: what happened to the file overall. */
    val badge: Char get() = when {
        conflicted -> 'U'
        untracked -> 'A'
        index != ' ' -> index
        else -> worktree
    }
}

data class GitStatus(
    val branch: String?,
    val upstream: String?,
    val ahead: Int,
    val behind: Int,
    val hasCommits: Boolean,
    val files: List<GitFileChange>,
) {
    val detached: Boolean get() = branch == null
    val staged: List<GitFileChange> get() = files.filter { it.staged }
    val unstaged: List<GitFileChange> get() = files.filter { it.unstaged }
}

data class GitCommit(
    val hash: String,
    val subject: String,
    val author: String,
    val relativeDate: String,
) {
    val shortHash: String get() = hash.take(7)
}

enum class ChecksState { NONE, PENDING, PASSING, FAILING }

/** CI checks on a pull request's latest commit, summed up. */
data class ChecksSummary(val passed: Int = 0, val failed: Int = 0, val pending: Int = 0, val skipped: Int = 0) {
    val total: Int get() = passed + failed + pending + skipped
    val state: ChecksState get() = when {
        failed > 0 -> ChecksState.FAILING
        pending > 0 -> ChecksState.PENDING
        passed > 0 || skipped > 0 -> ChecksState.PASSING
        else -> ChecksState.NONE
    }

    /** "2 of 3 checks failed", "1 check running", "4 checks passed". */
    val label: String get() {
        fun checks(n: Int) = if (n == 1) "check" else "checks"
        return when (state) {
            ChecksState.NONE -> "No checks"
            ChecksState.FAILING -> "$failed of $total ${checks(total)} failed"
            ChecksState.PENDING -> "$pending ${checks(pending)} running"
            ChecksState.PASSING -> "${passed + skipped} ${checks(passed + skipped)} passed"
        }
    }
}

data class GitPullRequest(
    val number: Int,
    val title: String,
    val url: String,
    val headBranch: String,
    val baseBranch: String,
    val author: String,
    val isDraft: Boolean,
    val checks: ChecksSummary,
    /** APPROVED, CHANGES_REQUESTED, REVIEW_REQUIRED, or null. */
    val reviewDecision: String?,
)

data class GitResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0

    /** The most useful line of a failed command, for a toast or an inline error. */
    fun errorSummary(fallback: String): String {
        val lines = output.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val line = lines.lastOrNull { it.startsWith("fatal:") || it.startsWith("error:") }
            ?: lines.lastOrNull { !it.startsWith("hint:") }
            ?: return fallback
        return line.removePrefix("fatal: ").removePrefix("error: ").take(240)
    }
}

/** Parsing for the machine-readable git output the Git panel relies on. */
internal object GitParsing {
    private val AheadBehind = Regex("""\[(?:ahead (\d+))?(?:, )?(?:behind (\d+))?]""")

    /** Parses `git status --porcelain=v1 --branch -z`. */
    fun parseStatus(output: String): GitStatus {
        val entries = output.split('\u0000')
        var branch: String? = null
        var upstream: String? = null
        var ahead = 0
        var behind = 0
        var hasCommits = true
        val files = mutableListOf<GitFileChange>()
        var index = 0
        while (index < entries.size) {
            val entry = entries[index]
            index++
            if (entry.isEmpty()) continue
            if (entry.startsWith("## ")) {
                val header = entry.removePrefix("## ")
                when {
                    header.startsWith("No commits yet on ") -> {
                        hasCommits = false
                        branch = header.removePrefix("No commits yet on ").substringBefore("...").trim()
                    }
                    header.startsWith("Initial commit on ") -> {
                        hasCommits = false
                        branch = header.removePrefix("Initial commit on ").substringBefore("...").trim()
                    }
                    header.startsWith("HEAD (no branch)") -> branch = null
                    else -> {
                        val names = header.substringBefore(" [")
                        branch = names.substringBefore("...")
                        upstream = names.substringAfter("...", "").takeIf(String::isNotBlank)
                    }
                }
                AheadBehind.find(header)?.let { match ->
                    ahead = match.groupValues[1].toIntOrNull() ?: 0
                    behind = match.groupValues[2].toIntOrNull() ?: 0
                }
                continue
            }
            if (entry.length < 4) continue
            val x = entry[0]
            val y = entry[1]
            val path = entry.substring(3)
            // Renames and copies carry the original path as the next NUL-separated field.
            val orig = if (x == 'R' || x == 'C') entries.getOrNull(index)?.also { index++ } else null
            if (x == '!') continue
            files += GitFileChange(path = path, index = x, worktree = y, origPath = orig)
        }
        return GitStatus(branch, upstream, ahead, behind, hasCommits, files.sortedBy { it.path.lowercase() })
    }

    /** Parses `git log --format=%H%x1f%s%x1f%an%x1f%cr%x1e`. */
    fun parseLog(output: String): List<GitCommit> =
        output.split('\u001e').mapNotNull { record ->
            val fields = record.trim('\n', '\r', ' ').split('\u001f')
            if (fields.size < 4 || fields[0].length < 7) null
            else GitCommit(hash = fields[0], subject = fields[1], author = fields[2], relativeDate = fields[3])
        }

    /** Splits a unified, possibly multi-file patch into per-file changes. */
    fun parsePatch(patch: String, maxLinesPerFile: Int = 3_000): List<ChangeItem> {
        val files = mutableListOf<ChangeItem>()
        var path: String? = null
        var lines = mutableListOf<DiffLine>()
        var additions = 0
        var deletions = 0
        var binary = false
        var truncated = false
        var oldLine = 0
        var newLine = 0
        var inHunk = false

        fun flush() {
            val current = path ?: return
            if (truncated) lines += DiffLine(DiffLineType.INFO, "… diff truncated")
            files += ChangeItem(current, additions, deletions, lines, binary)
        }

        for (raw in patch.lineSequence()) {
            val line = raw.removeSuffix("\r")
            if (line.startsWith("diff --git ")) {
                flush()
                path = line.removePrefix("diff --git ").let(::pathFromDiffHeader)
                lines = mutableListOf()
                additions = 0
                deletions = 0
                binary = false
                truncated = false
                inHunk = false
                continue
            }
            if (path == null) continue
            when {
                line.startsWith("@@") -> {
                    inHunk = true
                    val match = HunkHeader.find(line)
                    oldLine = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    newLine = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    add(lines, DiffLine(DiffLineType.INFO, line), maxLinesPerFile) { truncated = true }
                }
                !inHunk -> {
                    when {
                        line.startsWith("+++ ") -> line.removePrefix("+++ ").takeUnless { it == "/dev/null" }
                            ?.let { path = it.removePrefix("b/") }
                        line.startsWith("Binary files ") || line == "GIT binary patch" -> binary = true
                        line.startsWith("rename from ") || line.startsWith("rename to ") ||
                            line.startsWith("new file mode") || line.startsWith("deleted file mode") ->
                            add(lines, DiffLine(DiffLineType.INFO, line), maxLinesPerFile) { truncated = true }
                    }
                }
                line.startsWith("+") -> {
                    additions++
                    add(lines, DiffLine(DiffLineType.ADDITION, line.substring(1), newLine = newLine++), maxLinesPerFile) { truncated = true }
                }
                line.startsWith("-") -> {
                    deletions++
                    add(lines, DiffLine(DiffLineType.DELETION, line.substring(1), oldLine = oldLine++), maxLinesPerFile) { truncated = true }
                }
                line.startsWith(" ") -> add(
                    lines,
                    DiffLine(DiffLineType.CONTEXT, line.substring(1), oldLine = oldLine++, newLine = newLine++),
                    maxLinesPerFile,
                ) { truncated = true }
                line.startsWith("\\") -> add(lines, DiffLine(DiffLineType.INFO, line), maxLinesPerFile) { truncated = true }
            }
        }
        flush()
        return files
    }

    private inline fun add(lines: MutableList<DiffLine>, line: DiffLine, max: Int, onTruncated: () -> Unit) {
        if (lines.size < max) lines += line else onTruncated()
    }

    /** `a/old b/new` → `new`, coping with spaces in names (quoted paths are left as git printed them). */
    private fun pathFromDiffHeader(header: String): String {
        val split = header.indexOf(" b/")
        return if (split >= 0) header.substring(split + 3) else header.substringAfterLast(' ').removePrefix("b/")
    }

    /** Parses `gh pr list --json number,title,url,headRefName,baseRefName,author,isDraft,statusCheckRollup,reviewDecision`. */
    fun parsePullRequests(output: String): List<GitPullRequest> {
        val array = runCatching { JSONArray(output.substring(output.indexOf('[').coerceAtLeast(0))) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val number = item.optInt("number", -1).takeIf { it > 0 } ?: return@mapNotNull null
            GitPullRequest(
                number = number,
                title = item.optString("title"),
                url = item.optString("url"),
                headBranch = item.optString("headRefName"),
                baseBranch = item.optString("baseRefName"),
                author = item.optJSONObject("author")?.optString("login").orEmpty(),
                isDraft = item.optBoolean("isDraft"),
                checks = summarizeChecks(item.optJSONArray("statusCheckRollup")),
                reviewDecision = item.optString("reviewDecision").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }

    /** Check runs (GitHub Actions and apps) and commit statuses both count. */
    fun summarizeChecks(rollup: JSONArray?): ChecksSummary {
        if (rollup == null) return ChecksSummary()
        var passed = 0
        var failed = 0
        var pending = 0
        var skipped = 0
        for (index in 0 until rollup.length()) {
            val check = rollup.optJSONObject(index) ?: continue
            if (check.has("state") && !check.has("conclusion")) {
                when (check.optString("state").uppercase()) {
                    "SUCCESS" -> passed++
                    "FAILURE", "ERROR" -> failed++
                    else -> pending++
                }
                continue
            }
            if (check.optString("status").uppercase() != "COMPLETED") {
                pending++
                continue
            }
            when (check.optString("conclusion").uppercase()) {
                "SUCCESS" -> passed++
                "NEUTRAL", "SKIPPED" -> skipped++
                "" -> pending++
                else -> failed++
            }
        }
        return ChecksSummary(passed, failed, pending, skipped)
    }

    /** Splits a drafted pull request into its title and Markdown body. */
    fun parsePullRequestDraft(draft: String): Pair<String?, String> {
        val lines = draft.trim().lines()
        val titleIndex = lines.indexOfFirst { it.isNotBlank() }
        val first = lines.getOrNull(titleIndex)?.trim().orEmpty()
        val titleMatch = Regex("""^(?:\*\*)?title:?(?:\*\*)?\s*(.+)$""", RegexOption.IGNORE_CASE).find(first)
        if (titleMatch == null) return null to draft.trim()
        val title = titleMatch.groupValues[1].trim().trim('"', '`').take(200).takeIf(String::isNotBlank)
        return title to lines.drop(titleIndex + 1).joinToString("\n").trim()
    }

    private val HunkHeader = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
}

/** Branch names git would accept, kept to a conservative subset. */
internal fun isValidGitBranchName(name: String): Boolean =
    name.isNotBlank() &&
        name.length <= 200 &&
        Regex("""^[A-Za-z0-9._/-]+$""").matches(name) &&
        !name.startsWith("-") && !name.startsWith("/") && !name.endsWith("/") && !name.endsWith(".") &&
        !name.endsWith(".lock") && ".." !in name && "//" !in name && "@{" !in name

/** "feature/add-dark-mode" → "Add dark mode", the title gh would suggest for a multi-commit branch. */
internal fun humanizeBranchName(branch: String): String {
    val words = branch.substringAfterLast('/').replace(Regex("[-_]+"), " ").trim()
    return words.replaceFirstChar { it.uppercase() }.ifBlank { branch }
}

/** Rebuilds unified diff text from parsed lines, for handing a change to the agent. */
internal fun ChangeItem.toPatchText(): String = buildString {
    appendLine("--- a/$path")
    appendLine("+++ b/$path")
    if (binary) appendLine("Binary file changed")
    diffLines.forEach { line ->
        when (line.type) {
            DiffLineType.ADDITION -> append('+')
            DiffLineType.DELETION -> append('-')
            DiffLineType.CONTEXT -> append(' ')
            DiffLineType.INFO -> Unit
        }
        appendLine(line.text)
    }
}
