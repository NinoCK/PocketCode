@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.ninock.pocketcode.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Commit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.ninock.pocketcode.runtime.ChecksState
import io.github.ninock.pocketcode.runtime.ChecksSummary
import io.github.ninock.pocketcode.runtime.GitPullRequest
import io.github.ninock.pocketcode.model.ChangeItem
import io.github.ninock.pocketcode.model.DiffLine
import io.github.ninock.pocketcode.runtime.GitCommit
import io.github.ninock.pocketcode.runtime.GitFileChange
import io.github.ninock.pocketcode.ui.theme.LocalPocketExtraColors

private enum class GitView(val label: String) { CHANGES("Changes"), BRANCH("Branch"), HISTORY("History"), PULL_REQUESTS("PRs") }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GitTab(state: GitPanelState, git: GitController) {
    LaunchedEffect(Unit) { git.refresh() }
    var view by rememberSaveable { mutableStateOf(GitView.CHANGES) }
    var branchSheet by rememberSaveable { mutableStateOf(false) }
    var discardTarget by remember { mutableStateOf<GitFileChange?>(null) }
    val status = state.status

    if (!state.isRepo) {
        ExpressiveEmptyState(
            icon = Icons.Rounded.AccountTree,
            title = "Not a Git repository",
            body = "Start tracking this project's history to commit, branch and push from here.",
            polygon = MaterialShapes.Cookie7Sided,
        ) {
            Button(onClick = git::initRepository, enabled = state.busy == null, shapes = ButtonDefaults.shapes()) {
                Text("Initialize repository")
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 4.dp)) {
            if (state.loading || state.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (status == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                        FilledTonalButton(onClick = git::refresh, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                    }
                } else {
                    LoadingIndicator()
                }
            }
            return@Column
        }
        BranchHeader(
            state = state,
            onBranches = {
                branchSheet = true
                git.loadBranches()
            },
            onPull = git::pull,
            onPush = git::push,
            onFetch = git::fetch,
            onRefresh = git::refresh,
            onCreatePullRequest = git::openPullRequestDraft,
            onOpenPullRequest = git::openPullRequest,
        )
        // Pull requests only make sense for a GitHub repository.
        val views = GitView.entries.filter { it != GitView.PULL_REQUESTS || state.github != null }
        if (view !in views) view = GitView.CHANGES
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            views.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = view == option,
                    onClick = { view = option },
                    shape = SegmentedButtonDefaults.itemShape(index, views.size),
                    // Four segments leave no room for the check mark on a phone.
                    icon = { if (views.size < 4) SegmentedButtonDefaults.Icon(view == option) },
                    label = {
                        val count = when (option) {
                            GitView.CHANGES -> status.files.size
                            GitView.PULL_REQUESTS -> state.github?.pullRequests?.size ?: 0
                            else -> 0
                        }
                        val compact = views.size >= 4
                        Text(
                            when {
                                count == 0 -> option.label
                                compact -> "${option.label} $count"
                                else -> "${option.label} · $count"
                            },
                            maxLines = 1,
                            softWrap = false,
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }
        when (view) {
            GitView.CHANGES -> WorkingTreeView(state, git, onDiscard = { discardTarget = it })
            GitView.BRANCH -> {
                LaunchedEffect(state.branchChanges == null) { if (state.branchChanges == null) git.loadBranchDiff() }
                BranchDiffView(state, git)
            }
            GitView.HISTORY -> {
                LaunchedEffect(state.history == null) { if (state.history == null) git.loadHistory() }
                HistoryView(state, git)
            }
            GitView.PULL_REQUESTS -> PullRequestsView(state, git)
        }
    }

    if (branchSheet) BranchSheet(state, git, onDismiss = { branchSheet = false })
    state.openCommit?.let { commit -> CommitSheet(commit, state.openCommitChanges, onDismiss = git::closeCommit, onAskAgent = { change, action -> git.closeCommit(); git.askAgent(change, action) }) }
    state.identityPrompt?.let { (name, email) -> IdentityDialog(name, email, git::saveIdentity, git::dismissIdentity) }
    state.pullRequestDraft?.let { draft -> PullRequestSheet(draft, state, git) }
    discardTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { discardTarget = null },
            icon = { Icon(Icons.AutoMirrored.Rounded.Undo, null) },
            title = { Text("Discard changes?") },
            text = {
                Text(
                    if (file.untracked || file.index == 'A') "${file.path} is a new file. Discarding deletes it. This can't be undone."
                    else "Your changes to ${file.path} will be lost. This can't be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        discardTarget = null
                        git.discard(file)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { discardTarget = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BranchHeader(
    state: GitPanelState,
    onBranches: () -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onFetch: () -> Unit,
    onRefresh: () -> Unit,
    onCreatePullRequest: () -> Unit,
    onOpenPullRequest: (GitPullRequest) -> Unit,
) {
    val status = state.status ?: return
    val idle = state.busy == null
    var menu by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = idle, onClick = onBranches).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.CallSplit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            status.branch ?: "Detached HEAD",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                !status.hasCommits -> "No commits yet"
                                status.upstream == null -> "Not tracking a remote branch"
                                status.ahead == 0 && status.behind == 0 -> "Up to date with ${status.upstream}"
                                else -> listOfNotNull(
                                    status.ahead.takeIf { it > 0 }?.let { "$it to push" },
                                    status.behind.takeIf { it > 0 }?.let { "$it to pull" },
                                ).joinToString(" · ") + " · ${status.upstream}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More Git actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Fetch") },
                            leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                            enabled = idle,
                            onClick = {
                                menu = false
                                onFetch()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = {
                                menu = false
                                onRefresh()
                            },
                        )
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onPull,
                    enabled = idle && status.upstream != null,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (status.behind > 0) "Pull ${status.behind}" else "Pull")
                }
                FilledTonalButton(
                    onClick = onPush,
                    enabled = idle && status.hasCommits && !status.detached,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            status.upstream == null -> "Publish"
                            status.ahead > 0 -> "Push ${status.ahead}"
                            else -> "Push"
                        },
                    )
                }
            }
            val pullRequest = state.currentPullRequest
            when {
                pullRequest != null -> Row(
                    Modifier
                        .padding(top = 8.dp, end = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onOpenPullRequest(pullRequest) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChecksIcon(pullRequest.checks)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "#${pullRequest.number} ${pullRequest.title}",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(if (pullRequest.isDraft) "Draft" else null, pullRequest.checks.label, reviewLabel(pullRequest.reviewDecision)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open pull request", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.canOpenPullRequest -> Button(
                    onClick = onCreatePullRequest,
                    enabled = idle,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(top = 8.dp, end = 12.dp).fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.MergeType, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create pull request")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WorkingTreeView(state: GitPanelState, git: GitController, onDiscard: (GitFileChange) -> Unit) {
    val status = state.status ?: return
    val staged = status.staged
    val unstaged = status.unstaged
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val idle = state.busy == null
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "commit") {
            Column(Modifier.padding(bottom = 12.dp)) {
                OutlinedTextField(
                    value = state.commitMessage,
                    onValueChange = git::setCommitMessage,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (state.draftingMessage) "Writing a message…" else "Commit message") },
                    minLines = 1,
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    trailingIcon = {
                        if (state.draftingMessage) {
                            LoadingIndicator(Modifier.size(28.dp))
                        } else {
                            IconButton(onClick = git::draftCommitMessage, enabled = status.files.isNotEmpty()) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    if (staged.isEmpty()) "Write a message for all changes" else "Write a message for the staged changes",
                                    tint = if (status.files.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.size(8.dp))
                val commitAll = staged.isEmpty()
                Button(
                    onClick = { git.commit(includeAll = commitAll) },
                    enabled = idle && state.commitMessage.isNotBlank() && status.files.isNotEmpty() && status.files.none { it.conflicted },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Commit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            state.busy == "Committing…" -> "Committing…"
                            commitAll -> "Commit all ${unstaged.size} ${if (unstaged.size == 1) "change" else "changes"}"
                            else -> "Commit ${staged.size} staged"
                        },
                    )
                }
                if (status.files.any { it.conflicted }) {
                    Text(
                        "Resolve merge conflicts before committing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                    )
                }
            }
        }
        if (status.files.isNotEmpty()) {
            item(key = "ask-hint") {
                Text(
                    "Long-press a file to ask the agent about it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
        }
        if (status.files.isEmpty()) {
            item(key = "clean") {
                Text(
                    "Nothing to commit. The working tree is clean.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        fileSection(
            key = "staged",
            title = "Staged",
            files = staged,
            bulkLabel = "Unstage all",
            onBulk = { git.unstage(emptyList()) },
            idle = idle,
            expanded = expanded,
            diffs = state.fileDiffs,
            onToggle = { file ->
                expanded = if (expanded == "staged:${file.path}") null else "staged:${file.path}".also { if (file.path !in state.fileDiffs) git.loadFileDiff(file) }
            },
            primary = { file -> FileAction(Icons.Rounded.Remove, "Unstage") { git.unstage(listOf(file)) } },
            secondary = { null },
            onAskAgent = git::askAgent,
        )
        fileSection(
            key = "unstaged",
            title = "Changes",
            files = unstaged,
            bulkLabel = "Stage all",
            onBulk = { git.stage(emptyList()) },
            idle = idle,
            expanded = expanded,
            diffs = state.fileDiffs,
            onToggle = { file ->
                expanded = if (expanded == "unstaged:${file.path}") null else "unstaged:${file.path}".also { if (file.path !in state.fileDiffs) git.loadFileDiff(file) }
            },
            primary = { file -> FileAction(Icons.Rounded.Add, "Stage") { git.stage(listOf(file)) } },
            secondary = { file -> if (file.conflicted) null else FileAction(Icons.AutoMirrored.Rounded.Undo, "Discard") { onDiscard(file) } },
            onAskAgent = git::askAgent,
        )
    }
}

private class FileAction(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val run: () -> Unit)

private fun LazyListScope.fileSection(
    key: String,
    title: String,
    files: List<GitFileChange>,
    bulkLabel: String,
    onBulk: () -> Unit,
    idle: Boolean,
    expanded: String?,
    diffs: Map<String, List<DiffLine>>,
    onToggle: (GitFileChange) -> Unit,
    primary: (GitFileChange) -> FileAction,
    secondary: (GitFileChange) -> FileAction?,
    onAskAgent: (GitFileChange, AgentDiffAction) -> Unit,
) {
    if (files.isEmpty()) return
    item(key = "$key-header") {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader("$title · ${files.size}", Modifier.weight(1f))
            TextButton(onClick = onBulk, enabled = idle) { Text(bulkLabel) }
        }
    }
    itemsIndexed(files, key = { _, file -> "$key:${file.path}" }) { index, file ->
        val isExpanded = expanded == "$key:${file.path}"
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = groupedShape(index, files.size),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.animateContentSize()) {
                var menu by remember { mutableStateOf(false) }
                val haptics = LocalHapticFeedback.current
                AskAgentMenu(expanded = menu, conflicted = file.conflicted, onDismiss = { menu = false }) { action -> onAskAgent(file, action) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onToggle(file) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            },
                            onLongClickLabel = "Ask the agent",
                        )
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge(file.badge)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.path.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val folder = file.path.substringBeforeLast('/', "")
                        val detail = listOfNotNull(
                            file.origPath?.let { "from ${it.substringAfterLast('/')}" },
                            folder.takeIf(String::isNotEmpty),
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(
                                detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    secondary(file)?.let { action ->
                        IconButton(onClick = action.run, enabled = idle) { Icon(action.icon, action.label) }
                    }
                    primary(file).let { action ->
                        IconButton(onClick = action.run, enabled = idle) { Icon(action.icon, action.label) }
                    }
                }
                if (isExpanded) {
                    DiffBlock(diffs[file.path])
                }
            }
        }
    }
}

/** Long-press menu on a changed file: hand its diff to the agent. */
@Composable
private fun AskAgentMenu(expanded: Boolean, conflicted: Boolean, onDismiss: () -> Unit, onSelect: (AgentDiffAction) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val actions = if (conflicted) listOf(AgentDiffAction.RESOLVE_CONFLICTS, AgentDiffAction.EXPLAIN)
        else listOf(AgentDiffAction.REVIEW, AgentDiffAction.EXPLAIN)
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = {
                    Icon(
                        when (action) {
                            AgentDiffAction.REVIEW -> Icons.Rounded.RateReview
                            AgentDiffAction.EXPLAIN -> Icons.Rounded.AutoAwesome
                            AgentDiffAction.RESOLVE_CONFLICTS -> Icons.AutoMirrored.Rounded.MergeType
                        },
                        null,
                    )
                },
                onClick = {
                    onDismiss()
                    onSelect(action)
                },
            )
        }
    }
}

@Composable
private fun StatusBadge(letter: Char) {
    val extra = LocalPocketExtraColors.current
    val (container, content) = when (letter) {
        'A' -> extra.successContainer to extra.onSuccessContainer
        'D' -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        'U' -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.toString(), color = content, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiffBlock(lines: List<DiffLine>?) {
    if (lines == null) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        lines.forEach { DiffLineRow(it) }
    }
}

@Composable
private fun ChangeList(changes: List<ChangeItem>, onAskAgent: (ChangeItem, AgentDiffAction) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        changes.forEachIndexed { index, change ->
            ChangeRow(change, index, changes.size, expanded == change.path, onAskAgent = { onAskAgent(change, it) }) {
                expanded = if (expanded == change.path) null else change.path
            }
        }
    }
}

@Composable
private fun ChangeRow(change: ChangeItem, index: Int, count: Int, expanded: Boolean, onAskAgent: (AgentDiffAction) -> Unit, onToggle: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.animateContentSize()) {
            AskAgentMenu(expanded = menu, conflicted = false, onDismiss = { menu = false }, onSelect = onAskAgent)
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                        onLongClickLabel = "Ask the agent",
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(change.path.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    change.path.substringBeforeLast('/', "").takeIf(String::isNotEmpty)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (change.binary) {
                    Text("binary", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("+${change.additions}", color = LocalPocketExtraColors.current.success, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Text("-${change.deletions}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (expanded) DiffBlock(change.diffLines.ifEmpty { listOf(DiffLine(io.github.ninock.pocketcode.model.DiffLineType.INFO, if (change.binary) "Binary file changed" else "Mode or name change only")) })
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BranchDiffView(state: GitPanelState, git: GitController) {
    val changes = state.branchChanges
    if (changes == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item(key = "summary") {
            Column(Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                state.branchBase?.let { base ->
                    Text(
                        "Compared with $base · ${state.branchCommits.size} ${if (state.branchCommits.size == 1) "commit" else "commits"} · ${changes.size} ${if (changes.size == 1) "file" else "files"}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                state.branchNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (state.branchCommits.isNotEmpty()) {
            item(key = "commits-header") { SectionHeader("Commits", Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) }
            itemsIndexed(state.branchCommits, key = { _, commit -> "c:${commit.hash}" }) { index, commit ->
                CommitRow(commit, index, state.branchCommits.size) { git.openCommit(commit) }
            }
        }
        if (changes.isNotEmpty()) {
            item(key = "files-header") { SectionHeader("Files", Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) }
            item(key = "files") { ChangeList(changes, onAskAgent = git::askAgent) }
        } else if (state.branchBase != null) {
            item(key = "same") {
                Text(
                    "No differences from ${state.branchBase}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryView(state: GitPanelState, git: GitController) {
    val history = state.history
    when {
        history == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        history.isEmpty() -> ExpressiveEmptyState(Icons.Rounded.Commit, "No commits yet", "Commit your changes to start the history.")
        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(history, key = { _, commit -> commit.hash }) { index, commit ->
                CommitRow(commit, index, history.size) { git.openCommit(commit) }
            }
        }
    }
}

@Composable
private fun CommitRow(commit: GitCommit, index: Int, count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = groupedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(commit.subject, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${commit.author} · ${commit.relativeDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(commit.shortHash, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommitSheet(commit: GitCommit, changes: List<ChangeItem>?, onDismiss: () -> Unit, onAskAgent: (ChangeItem, AgentDiffAction) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(commit.subject, style = MaterialTheme.typography.titleMedium)
            Text(
                "${commit.shortHash} · ${commit.author} · ${commit.relativeDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
        }
        if (changes == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                item { ChangeList(changes, onAskAgent = onAskAgent) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BranchSheet(state: GitPanelState, git: GitController, onDismiss: () -> Unit) {
    var newName by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Branches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.replace(' ', '-') },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("New branch name") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = {
                        git.createBranch(newName)
                        onDismiss()
                    },
                    enabled = newName.isNotBlank() && state.busy == null,
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Create") }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
        }
        val branches = state.branches
        if (branches == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 32.dp)) {
                items(branches, key = { (if (it.remoteOnly) "r:" else "l:") + it.name }) { branch ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !branch.current && state.busy == null) {
                                git.switchBranch(branch.name)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.CallSplit,
                            null,
                            tint = if (branch.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            branch.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (branch.current) FontWeight.SemiBold else null,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        when {
                            branch.current -> Text("Current", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            branch.remoteOnly -> Text("Remote", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityDialog(name: String, email: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var nameValue by rememberSaveable { mutableStateOf(name) }
    var emailValue by rememberSaveable { mutableStateOf(email) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Who's committing?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Git records a name and email with every commit. This is saved for all projects.")
                OutlinedTextField(value = nameValue, onValueChange = { nameValue = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = emailValue, onValueChange = { emailValue = it }, label = { Text("Email") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(nameValue, emailValue) }) { Text("Save and commit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun reviewLabel(decision: String?): String? = when (decision) {
    "APPROVED" -> "Approved"
    "CHANGES_REQUESTED" -> "Changes requested"
    "REVIEW_REQUIRED" -> "Review required"
    else -> null
}

/** CI state as an icon plus its own description, so it never relies on color alone. */
@Composable
private fun ChecksIcon(checks: ChecksSummary) {
    val extra = LocalPocketExtraColors.current
    val (icon, tint) = when (checks.state) {
        ChecksState.PASSING -> Icons.Rounded.CheckCircle to extra.success
        ChecksState.FAILING -> Icons.Rounded.Cancel to MaterialTheme.colorScheme.error
        ChecksState.PENDING -> Icons.Rounded.Schedule to MaterialTheme.colorScheme.tertiary
        ChecksState.NONE -> Icons.AutoMirrored.Rounded.MergeType to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, checks.label, Modifier.size(20.dp), tint = tint)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PullRequestsView(state: GitPanelState, git: GitController) {
    val github = state.github ?: return
    val pullRequests = github.pullRequests
    when {
        pullRequests == null && github.error != null -> ExpressiveEmptyState(
            icon = Icons.AutoMirrored.Rounded.MergeType,
            title = "Pull requests unavailable",
            body = github.error,
        ) {
            FilledTonalButton(onClick = git::refreshPullRequests, shapes = ButtonDefaults.shapes()) { Text("Try again") }
        }
        pullRequests == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item(key = "pr-header") {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        if (pullRequests.isEmpty()) "No open pull requests" else "Open · ${pullRequests.size}",
                        Modifier.weight(1f),
                    )
                    if (github.loading) LoadingIndicator(Modifier.size(28.dp))
                    else IconButton(onClick = git::refreshPullRequests) { Icon(Icons.Rounded.Refresh, "Refresh pull requests") }
                }
                github.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                }
            }
            itemsIndexed(pullRequests, key = { _, pr -> "pr:${pr.number}" }) { index, pr ->
                Surface(shape = groupedShape(index, pullRequests.size), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { git.openPullRequest(pr) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChecksIcon(pr.checks)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pr.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    "#${pr.number}",
                                    pr.author.takeIf(String::isNotBlank),
                                    "${pr.headBranch} → ${pr.baseBranch}",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(if (pr.isDraft) "Draft" else null, pr.checks.label, reviewLabel(pr.reviewDecision)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pr.checks.state == ChecksState.FAILING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PullRequestSheet(draft: PullRequestDraft, state: GitPanelState, git: GitController) {
    val creating = state.busy == "Creating pull request…"
    val status = state.status
    ModalBottomSheet(
        onDismissRequest = { if (!creating) git.dismissPullRequestDraft() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Create pull request", style = MaterialTheme.typography.titleLarge)
            Text(
                buildString {
                    append("${status?.branch} into ${draft.base}")
                    if (draft.commits > 0) append(" · ${draft.commits} ${if (draft.commits == 1) "commit" else "commits"}")
                    if (status != null && (status.upstream == null || status.ahead > 0)) append(" · pushes first")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.base,
                onValueChange = { value -> git.updatePullRequestDraft { it.copy(base = value.trim()) } },
                label = { Text("Base branch") },
                singleLine = true,
                enabled = !creating,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { value -> git.updatePullRequestDraft { it.copy(title = value) } },
                label = { Text("Title") },
                singleLine = true,
                enabled = !creating,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.body,
                onValueChange = { value -> git.updatePullRequestDraft { it.copy(body = value) } },
                label = { Text("Description") },
                placeholder = { if (draft.drafting) Text("Writing a description from your commits…") },
                minLines = 6,
                maxLines = 14,
                enabled = !creating,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (draft.drafting) {
                        LoadingIndicator(Modifier.size(28.dp))
                    } else {
                        IconButton(onClick = git::redraftPullRequest, enabled = !creating) {
                            Icon(Icons.Rounded.AutoAwesome, "Write the description again", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !creating) { git.updatePullRequestDraft { it.copy(isDraft = !it.isDraft) } }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = draft.isDraft, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
                Column {
                    Text("Open as a draft", style = MaterialTheme.typography.bodyLarge)
                    Text("Not ready for review yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = git::dismissPullRequestDraft, enabled = !creating, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = git::createPullRequest,
                    enabled = !creating && !draft.drafting && draft.title.isNotBlank() && draft.base.isNotBlank() && state.busy == null,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(2f),
                ) {
                    if (creating) LoadingIndicator(Modifier.size(20.dp)) else Icon(Icons.AutoMirrored.Rounded.MergeType, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (creating) "Creating…" else "Create pull request")
                }
            }
        }
    }
}
