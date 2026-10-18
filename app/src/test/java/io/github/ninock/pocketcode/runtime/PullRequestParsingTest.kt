package io.github.ninock.pocketcode.runtime

import io.github.ninock.pocketcode.model.ChangeItem
import io.github.ninock.pocketcode.model.DiffLine
import io.github.ninock.pocketcode.model.DiffLineType
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PullRequestParsingTest {
    @Test
    fun parsesGhPrListWithChecks() {
        val output = """
            [{"author":{"login":"octo"},"baseRefName":"main","headRefName":"feature/x","isDraft":false,"number":12,
              "reviewDecision":"APPROVED","title":"Add X","url":"https://github.com/o/r/pull/12",
              "statusCheckRollup":[
                {"__typename":"CheckRun","name":"build","status":"COMPLETED","conclusion":"SUCCESS"},
                {"__typename":"CheckRun","name":"lint","status":"COMPLETED","conclusion":"SKIPPED"},
                {"__typename":"StatusContext","context":"ci/legacy","state":"SUCCESS"}
              ]},
             {"author":{"login":"me"},"baseRefName":"main","headRefName":"wip","isDraft":true,"number":13,
              "reviewDecision":"","title":"WIP","url":"https://github.com/o/r/pull/13","statusCheckRollup":[]}]
        """.trimIndent()
        val prs = GitParsing.parsePullRequests(output)
        assertEquals(2, prs.size)
        val first = prs[0]
        assertEquals(12, first.number)
        assertEquals("octo", first.author)
        assertEquals("feature/x", first.headBranch)
        assertEquals(ChecksState.PASSING, first.checks.state)
        assertEquals("3 checks passed", first.checks.label)
        assertEquals("APPROVED", first.reviewDecision)
        assertTrue(prs[1].isDraft)
        assertNull(prs[1].reviewDecision)
        assertEquals(ChecksState.NONE, prs[1].checks.state)
    }

    @Test
    fun failuresWinOverPendingAndPendingOverSuccess() {
        val failing = GitParsing.summarizeChecks(
            JSONArray("""[{"status":"COMPLETED","conclusion":"FAILURE"},{"status":"IN_PROGRESS","conclusion":""},{"status":"COMPLETED","conclusion":"SUCCESS"}]"""),
        )
        assertEquals(ChecksState.FAILING, failing.state)
        assertEquals("1 of 3 checks failed", failing.label)
        val pending = GitParsing.summarizeChecks(JSONArray("""[{"state":"PENDING"},{"status":"COMPLETED","conclusion":"SUCCESS"}]"""))
        assertEquals(ChecksState.PENDING, pending.state)
        assertEquals("1 check running", pending.label)
        assertEquals(ChecksState.FAILING, GitParsing.summarizeChecks(JSONArray("""[{"state":"ERROR"}]""")).state)
    }

    @Test
    fun ignoresNonJsonOutput() {
        assertEquals(emptyList<GitPullRequest>(), GitParsing.parsePullRequests("not logged in"))
    }

    @Test
    fun splitsDraftIntoTitleAndBody() {
        val (title, body) = GitParsing.parsePullRequestDraft("Title: Add dark mode\n\nAdds a theme.\n\n## Changes\n- Toggle")
        assertEquals("Add dark mode", title)
        assertEquals("Adds a theme.\n\n## Changes\n- Toggle", body)
        val (boldTitle, _) = GitParsing.parsePullRequestDraft("**Title:** Fix crash\n\nBody")
        assertEquals("Fix crash", boldTitle)
        val (noTitle, plain) = GitParsing.parsePullRequestDraft("Just a description")
        assertNull(noTitle)
        assertEquals("Just a description", plain)
    }

    @Test
    fun humanizesBranchNames() {
        assertEquals("Add dark mode", humanizeBranchName("feature/add-dark-mode"))
        assertEquals("Fix login bug", humanizeBranchName("fix_login_bug"))
    }

    @Test
    fun rebuildsPatchText() {
        val change = ChangeItem(
            "src/a.kt",
            additions = 1,
            deletions = 1,
            diffLines = listOf(
                DiffLine(DiffLineType.INFO, "@@ -1,2 +1,2 @@"),
                DiffLine(DiffLineType.CONTEXT, "keep"),
                DiffLine(DiffLineType.DELETION, "old"),
                DiffLine(DiffLineType.ADDITION, "new"),
            ),
        )
        assertEquals("--- a/src/a.kt\n+++ b/src/a.kt\n@@ -1,2 +1,2 @@\n keep\n-old\n+new\n", change.toPatchText().replace("\r\n", "\n"))
    }
}
