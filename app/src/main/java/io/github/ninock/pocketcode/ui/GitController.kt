package io.github.ninock.pocketcode.ui

import io.github.ninock.pocketcode.model.ChangeItem
import io.github.ninock.pocketcode.model.DiffLine
import io.github.ninock.pocketcode.model.DiffLineType
import io.github.ninock.pocketcode.runtime.GitCommit
import io.github.ninock.pocketcode.runtime.GitFileChange
import io.github.ninock.pocketcode.runtime.GitParsing
import io.github.ninock.pocketcode.runtime.GitPullRequest
import io.github.ninock.pocketcode.runtime.GitResult
import io.github.ninock.pocketcode.runtime.GitStatus
import io.github.ninock.pocketcode.runtime.cleanDraft
import io.github.ninock.pocketcode.runtime.humanizeBranchName
import io.github.ninock.pocketcode.runtime.isValidGitBranchName
import io.github.ninock.pocketcode.runtime.toPatchText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class GitBranch(val name: String, val current: Boolean, val remoteOnly: Boolean)

/** What the agent is asked to do with a file's diff dropped into the chat from the Git tab. */
enum class AgentDiffAction(val label: String) {
    REVIEW("Review this change"),
    EXPLAIN("Explain this"),
    RESOLVE_CONFLICTS("Resolve the conflicts");

    fun prompt(path: String): String = when (this) {
        REVIEW -> "Review my change to `$path` (diff attached). Point out bugs, edge cases it misses and anything that could be simpler. Be specific and refer to lines."
        EXPLAIN -> "Explain what this change to `$path` does (diff attached), and why it might have been made."
        RESOLVE_CONFLICTS -> "Resolve the merge conflicts in `$path` (diff attached). Keep the intent of both sides, remove every conflict marker, check the result still makes sense, then stage the file with `git add`."
    }
}

/** The "Create pull request" sheet. */
data class PullRequestDraft(
    val base: String,
    val title: String,
    val body: String = "",
    val isDraft: Boolean = false,
    /** True while the description is being written. */
    val drafting: Boolean = false,
    val commits: Int = 0,
)

/** The repository's GitHub side: its default branch and open pull requests. */
data class GitHubState(
    val defaultBranch: String? = null,
    /** Open pull requests; null until loaded. */
    val pullRequests: List<GitPullRequest>? = null,
    val loading: Boolean = false,
    /** Why pull requests can't be shown (GitHub CLI not signed in, network error…). */
    val error: String? = null,
)

/** What the Git tab shows for the active project. */
data class GitPanelState(
    val projectId: String? = null,
    val loading: Boolean = false,
    val isRepo: Boolean = true,
    val status: GitStatus? = null,
    /** Label of the git operation in progress (commit, push…); null when idle. */
    val busy: String? = null,
    val error: String? = null,
    val commitMessage: String = "",
    val fileDiffs: Map<String, List<DiffLine>> = emptyMap(),
    val branchBase: String? = null,
    val branchNote: String? = null,
    val branchCommits: List<GitCommit> = emptyList(),
    val branchChanges: List<ChangeItem>? = null,
    val history: List<GitCommit>? = null,
    val openCommit: GitCommit? = null,
    val openCommitChanges: List<ChangeItem>? = null,
    val branches: List<GitBranch>? = null,
    /** Set when a commit is waiting for the user to enter their name and email. */
    val identityPrompt: Pair<String, String>? = null,
    /** True while a commit message is being drafted from the diff. */
    val draftingMessage: Boolean = false,
    /** Set when origin is a GitHub repository. */
    val github: GitHubState? = null,
    val pullRequestDraft: PullRequestDraft? = null,
) {
    /** The open pull request for the checked-out branch, if any. */
    val currentPullRequest: GitPullRequest?
        get() = status?.branch?.let { branch -> github?.pullRequests?.firstOrNull { it.headBranch == branch } }

    /** A pull request can be opened from this branch: it's on GitHub, has commits and isn't the default branch. */
    val canOpenPullRequest: Boolean
        get() {
            val status = status ?: return false
            val github = github ?: return false
            return status.hasCommits && !status.detached && github.error == null && github.pullRequests != null &&
                status.branch != (github.defaultBranch ?: "main") && currentPullRequest == null
        }
}

/**
 * Runs the Git panel's operations in the project's Linux environment. One operation runs at a
 * time; results are dropped if the user has moved to another project meanwhile.
 */
internal class GitController(
    private val scope: CoroutineScope,
    private val activeProjectId: () -> String?,
    private val run: (projectId: String, args: List<String>, network: Boolean) -> GitResult,
    private val read: () -> GitPanelState,
    private val update: ((GitPanelState) -> GitPanelState) -> Unit,
    private val toast: (String) -> Unit,
    /** Why the worktree must not be touched right now (e.g. the agent is editing it); null if it may. */
    private val worktreeBlockedReason: () -> String?,
    private val defaultIdentity: () -> Pair<String, String>,
    private val onWorktreeChanged: () -> Unit,
    /** Runs the GitHub CLI in the project folder. */
    private val gh: (projectId: String, args: List<String>) -> GitResult = { _, _ -> GitResult(1, "GitHub CLI unavailable") },
    /** Runs any command in the project folder; used to read new files in one go. */
    private val exec: (projectId: String, command: List<String>) -> GitResult = { _, _ -> GitResult(1, "unavailable") },
    /** Why the GitHub CLI can't be used right now, or null when it can. */
    private val githubBlockedReason: () -> String? = { "GitHub CLI unavailable" },
    /** Asks the model for a short piece of text; throws with a readable message on failure. */
    private val draft: suspend (projectId: String, instructions: String, input: String) -> String = { _, _, _ -> error("Drafting is unavailable") },
    private val openUrl: (String) -> Unit = {},
    /** Drops a file's diff into the chat with a prompt for [AgentDiffAction]. */
    private val onAskAgent: (path: String, patch: String, action: AgentDiffAction) -> Unit = { _, _, _ -> },
) {
    private val mutex = Mutex()
    private var pendingCommit: Pair<String, Boolean>? = null

    /** Clears everything shown for another project. */
    fun ensureProject() {
        val projectId = activeProjectId()
        if (read().projectId != projectId) update { GitPanelState(projectId = projectId) }
    }

    fun refresh() = launch(label = null, loading = true) { projectId ->
        reload(projectId)
        if (read().isRepo) loadGitHub(projectId)
    }

    /** Reloads the open pull requests and their checks. */
    fun refreshPullRequests() = launch(label = null, exclusive = false) { projectId -> loadGitHub(projectId) }

    fun setCommitMessage(message: String) = update { it.copy(commitMessage = message) }

    fun loadFileDiff(file: GitFileChange) = launch(label = null) { projectId ->
        val status = read().status ?: return@launch
        val lines = when {
            file.conflicted -> listOf(DiffLine(DiffLineType.INFO, "This file has merge conflicts. Resolve them in the editor or terminal, then stage it."))
            file.untracked -> patchLines(git(projectId, "diff", "--no-color", "--no-ext-diff", "--no-index", "--", "/dev/null", file.path))
            !status.hasCommits -> patchLines(git(projectId, "diff", "--no-color", "--no-ext-diff", "--cached", "--", file.path))
            else -> patchLines(git(projectId, *buildList {
                addAll(listOf("diff", "--no-color", "--no-ext-diff", "-M", "HEAD", "--", file.path))
                file.origPath?.let(::add)
            }.toTypedArray()))
        }
        updateFor(projectId) { it.copy(fileDiffs = it.fileDiffs + (file.path to lines)) }
    }

    fun stage(files: List<GitFileChange>) = launch("Staging…") { projectId ->
        val paths = files.flatMap { listOfNotNull(it.path, it.origPath) }
        if (paths.isEmpty()) git(projectId, "add", "-A").orReport("Couldn't stage changes")
        else git(projectId, "add", "-A", "--", *paths.toTypedArray()).orReport("Couldn't stage changes")
        reload(projectId)
    }

    fun unstage(files: List<GitFileChange>) = launch("Unstaging…") { projectId ->
        val hasCommits = read().status?.hasCommits != false
        val paths = files.flatMap { listOfNotNull(it.path, it.origPath) }.toTypedArray()
        val result = when {
            hasCommits && paths.isEmpty() -> git(projectId, "reset", "-q")
            hasCommits -> git(projectId, "reset", "-q", "HEAD", "--", *paths)
            paths.isEmpty() -> git(projectId, "rm", "-r", "-q", "--cached", ".")
            else -> git(projectId, "rm", "-q", "--cached", "--", *paths)
        }
        result.orReport("Couldn't unstage changes")
        reload(projectId)
    }

    fun discard(file: GitFileChange) = launch("Discarding…", touchesWorktree = true) { projectId ->
        val ok = when {
            file.untracked -> git(projectId, "clean", "-f", "-q", "--", file.path).orReport("Couldn't delete the file")
            file.index == 'A' -> git(projectId, "rm", "-f", "-q", "--", file.path).orReport("Couldn't remove the file")
            file.origPath != null -> {
                git(projectId, "reset", "-q", "HEAD", "--", file.path, file.origPath).orReport("Couldn't undo the rename") &&
                    git(projectId, "checkout", "HEAD", "--", file.origPath).orReport("Couldn't restore ${file.origPath}") &&
                    git(projectId, "clean", "-f", "-q", "--", file.path).orReport("Couldn't remove ${file.path}")
            }
            else -> git(projectId, "checkout", "HEAD", "--", file.path).orReport("Couldn't discard the changes")
        }
        if (ok) toast("Discarded changes to ${file.path.substringAfterLast('/')}")
        reload(projectId)
    }

    fun commit(includeAll: Boolean) {
        val message = read().commitMessage.trim()
        if (message.isEmpty()) {
            toast("Write a commit message first")
            return
        }
        launch("Committing…") { projectId ->
            val name = git(projectId, "config", "user.name").output.trim()
            val email = git(projectId, "config", "user.email").output.trim()
            if (name.isEmpty() || email.isEmpty()) {
                pendingCommit = message to includeAll
                val (defaultName, defaultEmail) = defaultIdentity()
                updateFor(projectId) { it.copy(identityPrompt = name.ifEmpty { defaultName } to email.ifEmpty { defaultEmail }) }
                return@launch
            }
            commitNow(projectId, message, includeAll)
        }
    }

    fun saveIdentity(name: String, email: String) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        if (cleanName.isEmpty() || cleanEmail.isEmpty() || '@' !in cleanEmail) {
            toast("Enter a name and an email address")
            return
        }
        update { it.copy(identityPrompt = null) }
        launch("Committing…") { projectId ->
            val saved = git(projectId, "config", "--global", "user.name", cleanName).orReport("Couldn't save your name") &&
                git(projectId, "config", "--global", "user.email", cleanEmail).orReport("Couldn't save your email")
            val pending = pendingCommit
            pendingCommit = null
            if (saved && pending != null) commitNow(projectId, pending.first, pending.second)
        }
    }

    fun dismissIdentity() {
        pendingCommit = null
        update { it.copy(identityPrompt = null) }
    }

    fun push() = launch("Pushing…") { projectId ->
        val status = read().status ?: return@launch
        if (status.detached) {
            toast("Switch to a branch before pushing")
            return@launch
        }
        val result = if (status.upstream == null) git(projectId, "push", "-u", "origin", "HEAD", network = true)
        else git(projectId, "push", network = true)
        if (result.orReport("Push failed")) toast("Pushed ${status.branch}")
        reload(projectId)
        loadGitHub(projectId)
    }

    fun pull() = launch("Pulling…", touchesWorktree = true) { projectId ->
        val status = read().status ?: return@launch
        if (status.upstream == null) {
            toast("This branch isn't tracking a remote branch yet")
            return@launch
        }
        if (git(projectId, "pull", "--ff-only", network = true).orReport("Pull failed")) toast("Up to date with ${status.upstream}")
        reload(projectId)
        onWorktreeChanged()
    }

    fun fetch() = launch("Fetching…") { projectId ->
        if (git(projectId, "fetch", "--prune", network = true).orReport("Fetch failed")) toast("Fetched from remote")
        reload(projectId)
    }

    fun initRepository() = launch("Creating repository…", touchesWorktree = true) { projectId ->
        if (git(projectId, "init", "-q").orReport("Couldn't create a repository")) {
            // Git 2.25 has no `init -b`; point the unborn HEAD at main instead of master.
            git(projectId, "symbolic-ref", "HEAD", "refs/heads/main")
        }
        reload(projectId)
        onWorktreeChanged()
    }

    fun loadBranches() = launch(label = null) { projectId ->
        val result = git(projectId, "branch", "-a", "--format=%(HEAD)%(refname:short)")
        if (!result.ok) {
            updateFor(projectId) { it.copy(branches = emptyList()) }
            return@launch
        }
        val names = result.output.lines().map(String::trim).filter(String::isNotEmpty)
        val local = names.filter { !it.startsWith("origin/") && !it.startsWith("*origin/") && it != "origin" }
        val locals = local.map { GitBranch(it.removePrefix("*"), current = it.startsWith("*"), remoteOnly = false) }
        val localNames = locals.mapTo(mutableSetOf()) { it.name }
        val remotes = names.filter { it.startsWith("origin/") && it != "origin/HEAD" }
            .map { it.removePrefix("origin/") }
            .filter { it !in localNames }
            .map { GitBranch(it, current = false, remoteOnly = true) }
        updateFor(projectId) { it.copy(branches = locals.sortedByDescending(GitBranch::current) + remotes) }
    }

    fun switchBranch(name: String) = launch("Switching to $name…", touchesWorktree = true) { projectId ->
        if (git(projectId, "switch", name).orReport("Couldn't switch branch")) toast("Switched to $name")
        updateFor(projectId) { it.copy(branches = null) }
        reload(projectId)
        onWorktreeChanged()
    }

    fun createBranch(name: String) {
        val clean = name.trim()
        if (!isValidGitBranchName(clean)) {
            toast("Use letters, numbers, dots, dashes, underscores and slashes")
            return
        }
        launch("Creating $clean…") { projectId ->
            if (git(projectId, "switch", "-c", clean).orReport("Couldn't create branch")) toast("Now on $clean")
            updateFor(projectId) { it.copy(branches = null) }
            reload(projectId)
        }
    }

    fun loadBranchDiff() = launch(label = null, loading = true) { projectId ->
        val status = read().status ?: return@launch
        val current = status.branch
        val defaultRef = git(projectId, "symbolic-ref", "--short", "refs/remotes/origin/HEAD").output.trim()
            .takeIf { it.startsWith("origin/") }
            ?: listOf("origin/main", "origin/master", "main", "master")
                .firstOrNull { git(projectId, "rev-parse", "--verify", "-q", "$it^{commit}").ok }
        val defaultName = defaultRef?.removePrefix("origin/")
        val (base, note) = when {
            !status.hasCommits -> null to "Make a first commit to compare branches."
            defaultRef != null && defaultName != current -> defaultRef to null
            status.upstream != null -> status.upstream to "Showing commits not pushed to ${status.upstream} yet."
            else -> null to "This is the main branch and it doesn't track a remote, so there's nothing to compare with."
        }
        if (base == null) {
            updateFor(projectId) { it.copy(branchBase = null, branchNote = note, branchCommits = emptyList(), branchChanges = emptyList()) }
            return@launch
        }
        val commits = GitParsing.parseLog(git(projectId, "log", LogFormat, "--max-count=100", "$base..HEAD").output)
        val changes = GitParsing.parsePatch(git(projectId, "diff", "--no-color", "--no-ext-diff", "-M", "$base...HEAD").output)
        updateFor(projectId) { it.copy(branchBase = base, branchNote = note, branchCommits = commits, branchChanges = changes) }
    }

    fun loadHistory() = launch(label = null, loading = true) { projectId ->
        val commits = if (read().status?.hasCommits == false) emptyList()
        else GitParsing.parseLog(git(projectId, "log", LogFormat, "--max-count=60").output)
        updateFor(projectId) { it.copy(history = commits) }
    }

    fun openCommit(commit: GitCommit) {
        update { it.copy(openCommit = commit, openCommitChanges = null) }
        launch(label = null) { projectId ->
            val patch = git(projectId, "show", "--no-color", "--no-ext-diff", "-M", "-m", "--first-parent", "--format=", commit.hash).output
            updateFor(projectId) { if (it.openCommit?.hash == commit.hash) it.copy(openCommitChanges = GitParsing.parsePatch(patch)) else it }
        }
    }

    fun closeCommit() = update { it.copy(openCommit = null, openCommitChanges = null) }

    /**
     * Writes a commit message from the staged diff, or from every change when nothing is staged
     * (that's what the Commit button commits then).
     */
    fun draftCommitMessage() {
        if (read().draftingMessage) return
        launch(label = null, exclusive = false) { projectId ->
            val status = read().status ?: return@launch
            if (status.files.isEmpty()) {
                toast("There's nothing to commit")
                return@launch
            }
            updateFor(projectId) { it.copy(draftingMessage = true) }
            try {
                val stagedOnly = status.staged.isNotEmpty()
                val diffArgs = buildList {
                    addAll(listOf("diff", "--no-color", "--no-ext-diff", "-M"))
                    if (stagedOnly || !status.hasCommits) add("--cached") else add("HEAD")
                }
                val stat = git(projectId, *(diffArgs + "--stat").toTypedArray()).output.trim()
                val diff = git(projectId, *diffArgs.toTypedArray()).output
                val newFiles = if (stagedOnly) "" else untrackedPatches(projectId, status.files.filter { it.untracked }.map { it.path })
                val recent = if (status.hasCommits) git(projectId, "log", "--format=%s", "--max-count=10").output.trim() else ""
                val changes = diff + newFiles
                val input = buildString {
                    if (recent.isNotEmpty()) appendLine("Recent commits:").appendLine(recent).appendLine()
                    if (stat.isNotEmpty()) appendLine("Changed files:").appendLine(stat).appendLine()
                    appendLine("Diff:")
                    append(changes.take(MaxDraftDiffChars))
                    if (changes.length > MaxDraftDiffChars) append("\n… (diff truncated)")
                }
                val message = cleanDraft(draft(projectId, CommitInstructions, input))
                if (message.isBlank()) toast("The model didn't suggest a message")
                else updateFor(projectId) { it.copy(commitMessage = message) }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                toast(error.message ?: "Couldn't draft a commit message")
            } finally {
                updateFor(projectId) { it.copy(draftingMessage = false) }
            }
        }
    }

    /** Opens the "Create pull request" sheet and drafts its description from the branch. */
    fun openPullRequestDraft() {
        githubBlockedReason()?.let {
            toast(it)
            return
        }
        launch(label = null, exclusive = false) { projectId ->
            val state = read()
            val status = state.status ?: return@launch
            val branch = status.branch ?: return@launch
            val base = state.github?.defaultBranch ?: "main"
            val baseRef = resolveBaseRef(projectId, base)
            val range = baseRef?.let { "$it..HEAD" } ?: "HEAD"
            val commits = GitParsing.parseLog(git(projectId, "log", LogFormat, "--max-count=100", range).output)
            if (baseRef != null && commits.isEmpty()) {
                toast("$branch has no commits that aren't on $base yet")
                return@launch
            }
            val title = commits.singleOrNull()?.subject ?: humanizeBranchName(branch)
            updateFor(projectId) { it.copy(pullRequestDraft = PullRequestDraft(base = base, title = title, drafting = true, commits = commits.size)) }
            draftPullRequestBody(projectId, baseRef, range, keepTitle = commits.size <= 1)
        }
    }

    /** Drafts the description again, e.g. after the base branch changed. */
    fun redraftPullRequest() {
        val current = read().pullRequestDraft ?: return
        if (current.drafting) return
        launch(label = null, exclusive = false) { projectId ->
            val baseRef = resolveBaseRef(projectId, current.base)
            updateFor(projectId) { it.copy(pullRequestDraft = it.pullRequestDraft?.copy(drafting = true)) }
            draftPullRequestBody(projectId, baseRef, baseRef?.let { "$it..HEAD" } ?: "HEAD", keepTitle = true)
        }
    }

    fun updatePullRequestDraft(change: (PullRequestDraft) -> PullRequestDraft) =
        update { state -> state.copy(pullRequestDraft = state.pullRequestDraft?.let(change)) }

    fun dismissPullRequestDraft() = update { it.copy(pullRequestDraft = null) }

    /** Pushes the branch if needed, opens the pull request with the GitHub CLI, then shows it. */
    fun createPullRequest() {
        val draft = read().pullRequestDraft ?: return
        if (draft.title.isBlank()) {
            toast("Give the pull request a title")
            return
        }
        launch("Creating pull request…") { projectId ->
            val status = read().status ?: return@launch
            val branch = status.branch ?: return@launch
            if (status.upstream == null || status.ahead > 0) {
                val pushed = if (status.upstream == null) git(projectId, "push", "-u", "origin", "HEAD", network = true)
                else git(projectId, "push", network = true)
                if (!pushed.orReport("Push failed")) return@launch
            }
            val args = buildList {
                addAll(listOf("pr", "create", "--base", draft.base.trim(), "--head", branch, "--title", draft.title.trim(), "--body", draft.body.trim()))
                if (draft.isDraft) add("--draft")
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { gh(projectId, args) }.getOrElse { GitResult(1, "fatal: ${it.message ?: "GitHub CLI failed"}") }
            }
            if (!result.orReport("Couldn't create the pull request")) {
                reload(projectId)
                return@launch
            }
            val url = PullRequestUrl.findAll(result.output).lastOrNull()?.value
            updateFor(projectId) { it.copy(pullRequestDraft = null) }
            toast(url?.let { "Opened pull request #${it.substringAfterLast('/')}" } ?: "Pull request created")
            url?.let(openUrl)
            reload(projectId)
            loadGitHub(projectId)
        }
    }

    fun openPullRequest(pullRequest: GitPullRequest) = openUrl(pullRequest.url)

    /** Drops [file]'s diff into the chat for the agent to act on. */
    fun askAgent(file: GitFileChange, action: AgentDiffAction) = launch(label = null, exclusive = false) { projectId ->
        val status = read().status ?: return@launch
        val patch = when {
            file.untracked -> git(projectId, "diff", "--no-color", "--no-ext-diff", "--no-index", "--", "/dev/null", file.path).output
            file.conflicted -> git(projectId, "diff", "--no-color", "--no-ext-diff", "--", file.path).output
            !status.hasCommits -> git(projectId, "diff", "--no-color", "--no-ext-diff", "--cached", "--", file.path).output
            else -> git(projectId, *buildList {
                addAll(listOf("diff", "--no-color", "--no-ext-diff", "-M", "HEAD", "--", file.path))
                file.origPath?.let(::add)
            }.toTypedArray()).output
        }
        if (activeProjectId() == projectId) onAskAgent(file.path, patch, action)
    }

    /** Same as [askAgent] for a change from the branch comparison or a commit. */
    fun askAgent(change: ChangeItem, action: AgentDiffAction) = onAskAgent(change.path, change.toPatchText(), action)

    private suspend fun resolveBaseRef(projectId: String, base: String): String? =
        listOf("origin/$base", base).firstOrNull { git(projectId, "rev-parse", "--verify", "-q", "$it^{commit}").ok }

    private suspend fun draftPullRequestBody(projectId: String, baseRef: String?, range: String, keepTitle: Boolean) {
        try {
            val log = git(projectId, "log", "--format=- %s%n%w(0,2,2)%b", "--max-count=50", range).output.trim()
            val diffRange = baseRef?.let { "$it...HEAD" }
            val stat = diffRange?.let { git(projectId, "diff", "--no-color", "--stat", it).output.trim() }.orEmpty()
            val diff = diffRange?.let { git(projectId, "diff", "--no-color", "--no-ext-diff", "-M", it).output }.orEmpty()
            val draftState = read().pullRequestDraft ?: return
            val input = buildString {
                appendLine("Branch: ${read().status?.branch} into ${draftState.base}")
                appendLine().appendLine("Commits:").appendLine(log.ifBlank { "(none)" })
                if (stat.isNotEmpty()) appendLine().appendLine("Changed files:").appendLine(stat)
                if (diff.isNotEmpty()) {
                    appendLine().appendLine("Diff:")
                    append(diff.take(MaxDraftDiffChars))
                    if (diff.length > MaxDraftDiffChars) append("\n… (diff truncated)")
                }
            }
            val (title, body) = GitParsing.parsePullRequestDraft(cleanDraft(draft(projectId, PullRequestInstructions, input)))
            updateFor(projectId) { state ->
                state.copy(
                    pullRequestDraft = state.pullRequestDraft?.let { current ->
                        current.copy(
                            title = if (!keepTitle && title != null) title else current.title,
                            body = body.ifBlank { current.body },
                            drafting = false,
                        )
                    },
                )
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            toast(error.message ?: "Couldn't draft a description")
            updateFor(projectId) { it.copy(pullRequestDraft = it.pullRequestDraft?.copy(drafting = false)) }
        }
    }

    /** New files' contents as diffs, read in one process. */
    private suspend fun untrackedPatches(projectId: String, paths: List<String>): String {
        if (paths.isEmpty()) return ""
        return withContext(Dispatchers.IO) {
            runCatching { exec(projectId, listOf("sh", "-c", UntrackedDiffScript, "sh") + paths.take(40)).output }.getOrDefault("")
        }
    }

    /** Learns whether origin is on GitHub and, if so, its default branch and open pull requests. */
    private suspend fun loadGitHub(projectId: String) {
        val origin = git(projectId, "remote", "get-url", "origin").output.trim()
        if (!origin.contains("github.com")) {
            updateFor(projectId) { it.copy(github = null) }
            return
        }
        val defaultBranch = git(projectId, "symbolic-ref", "--short", "refs/remotes/origin/HEAD").output.trim()
            .takeIf { it.startsWith("origin/") }?.removePrefix("origin/")
            ?: listOf("main", "master").firstOrNull { git(projectId, "rev-parse", "--verify", "-q", "origin/$it^{commit}").ok }
        val blocked = githubBlockedReason()
        if (blocked != null) {
            updateFor(projectId) { it.copy(github = GitHubState(defaultBranch = defaultBranch, error = blocked)) }
            return
        }
        updateFor(projectId) { it.copy(github = (it.github ?: GitHubState()).copy(defaultBranch = defaultBranch, loading = true)) }
        val result = withContext(Dispatchers.IO) {
            runCatching { gh(projectId, PullRequestListArgs) }.getOrElse { GitResult(1, "fatal: ${it.message ?: "GitHub CLI failed"}") }
        }
        updateFor(projectId) {
            it.copy(
                github = GitHubState(
                    defaultBranch = defaultBranch ?: it.github?.defaultBranch,
                    pullRequests = if (result.ok) GitParsing.parsePullRequests(result.output) else it.github?.pullRequests,
                    error = if (result.ok) null else result.errorSummary("Couldn't load pull requests"),
                ),
            )
        }
    }

    private suspend fun commitNow(projectId: String, message: String, includeAll: Boolean) {
        if (includeAll && !git(projectId, "add", "-A").orReport("Couldn't stage changes")) return
        if (git(projectId, "commit", "-q", "-m", message).orReport("Commit failed")) {
            updateFor(projectId) { it.copy(commitMessage = "") }
            toast("Committed: ${message.lineSequence().first().take(60)}")
        }
        reload(projectId)
    }

    private suspend fun reload(projectId: String) {
        val inside = git(projectId, "rev-parse", "--is-inside-work-tree")
        if (!inside.ok || inside.output.trim() != "true") {
            updateFor(projectId) { it.copy(isRepo = false, status = null, loading = false) }
            return
        }
        val result = git(projectId, "status", "--porcelain=v1", "--branch", "-z", "--untracked-files=all")
        if (!result.ok) {
            updateFor(projectId) { it.copy(loading = false, error = result.errorSummary("Couldn't read the repository status")) }
            return
        }
        val status = GitParsing.parseStatus(result.output)
        updateFor(projectId) {
            it.copy(
                isRepo = true,
                status = status,
                loading = false,
                error = null,
                fileDiffs = emptyMap(),
                branchChanges = null,
                history = null,
            )
        }
    }

    /** Applies [change] only while [projectId] is still the project on screen. */
    private fun updateFor(projectId: String, change: (GitPanelState) -> GitPanelState) =
        update { if (it.projectId == projectId) change(it) else it }

    private fun patchLines(result: GitResult): List<DiffLine> {
        val item = GitParsing.parsePatch(result.output).firstOrNull()
        return when {
            item == null -> listOf(DiffLine(DiffLineType.INFO, if (result.output.isBlank()) "No textual changes" else result.errorSummary("No diff available")))
            item.binary -> listOf(DiffLine(DiffLineType.INFO, "Binary file changed"))
            item.diffLines.isEmpty() -> listOf(DiffLine(DiffLineType.INFO, "Mode or name change only"))
            else -> item.diffLines
        }
    }

    private suspend fun git(projectId: String, vararg args: String, network: Boolean = false): GitResult =
        withContext(Dispatchers.IO) {
            runCatching { run(projectId, args.toList(), network) }
                .getOrElse { GitResult(1, "fatal: ${it.message ?: it::class.java.simpleName}") }
        }

    private fun GitResult.orReport(fallback: String): Boolean {
        if (!ok) toast(errorSummary(fallback))
        return ok
    }

    /**
     * Runs [block] for the active project. [exclusive] work waits its turn behind other git
     * operations; read-only work that may take a while (the model, the network) doesn't.
     */
    private fun launch(
        label: String?,
        loading: Boolean = false,
        touchesWorktree: Boolean = false,
        exclusive: Boolean = true,
        block: suspend (projectId: String) -> Unit,
    ) {
        ensureProject()
        val projectId = activeProjectId() ?: return
        if (touchesWorktree) worktreeBlockedReason()?.let {
            toast(it)
            return
        }
        if (label != null && read().busy != null) return
        scope.launch {
            if (label != null || loading) update { it.copy(busy = label ?: it.busy, loading = loading || it.loading) }
            try {
                if (exclusive) {
                    mutex.withLock { if (activeProjectId() == projectId) block(projectId) }
                } else if (activeProjectId() == projectId) {
                    block(projectId)
                }
            } finally {
                // Background reads (drafts, pull requests) leave other operations' indicators alone.
                update {
                    if (it.projectId == projectId) {
                        it.copy(busy = if (label != null) null else it.busy, loading = if (exclusive || loading) false else it.loading)
                    } else it
                }
            }
        }
    }

    private companion object {
        const val LogFormat = "--format=%H%x1f%s%x1f%an%x1f%cr%x1e"
        const val MaxDraftDiffChars = 60_000
        val PullRequestUrl = Regex("""https://\S+/pull/\d+""")
        val PullRequestListArgs = listOf(
            "pr", "list", "--state", "open", "--limit", "50",
            "--json", "number,title,url,headRefName,baseRefName,author,isDraft,statusCheckRollup,reviewDecision",
        )

        /** Prints each new file named in "$@" as a diff against nothing. */
        const val UntrackedDiffScript =
            "for f in \"\$@\"; do git --no-pager -c core.quotepath=off diff --no-color --no-ext-diff --no-index -- /dev/null \"\$f\"; done; exit 0"

        const val CommitInstructions = """You write git commit messages. Write one for the changes the user sends.
Rules:
- First line: a summary in the imperative mood ("Add", "Fix", "Update"), at most 72 characters, no trailing period.
- If the change isn't obvious from the summary, add a blank line and a short body, wrapped at 72 characters, saying what changed and why. Skip the body for small changes.
- Follow the style of the recent commits if there are any (for example a "type(scope):" prefix).
- Reply with the commit message only: no quotes, no code fences, no commentary."""

        const val PullRequestInstructions = """You write GitHub pull requests. Write one for the branch the user describes.
Reply in exactly this format:
Title: <a clear title, at most 72 characters>

<the description in GitHub Markdown>

Open the description with one or two sentences on what the change does and why, then a "## Changes" list of the notable changes. Add a "## Testing" section only if the commits or diff show how it was tested. Don't invent anything that isn't in the input. Don't wrap the reply in a code fence."""
    }
}
