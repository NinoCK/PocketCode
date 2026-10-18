package io.github.ninock.pocketcode.runtime

import io.github.ninock.pocketcode.model.DiffLineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitParsingTest {
    @Test
    fun parsesBranchHeaderAndFiles() {
        val output = "## main...origin/main [ahead 2, behind 1]\u0000 M src/app.kt\u0000M  README.md\u0000?? new file.txt\u0000R  b.txt\u0000a.txt\u0000UU conflict.kt\u0000"
        val status = GitParsing.parseStatus(output)
        assertEquals("main", status.branch)
        assertEquals("origin/main", status.upstream)
        assertEquals(2, status.ahead)
        assertEquals(1, status.behind)
        assertTrue(status.hasCommits)
        assertEquals(listOf("b.txt", "conflict.kt", "new file.txt", "README.md", "src/app.kt"), status.files.map { it.path })
        val rename = status.files.first { it.path == "b.txt" }
        assertEquals("a.txt", rename.origPath)
        assertTrue(rename.staged)
        assertFalse(rename.unstaged)
        assertTrue(status.files.first { it.path == "new file.txt" }.untracked)
        assertTrue(status.files.first { it.path == "conflict.kt" }.conflicted)
        assertEquals(listOf("README.md", "b.txt").sorted(), status.staged.map { it.path }.sorted())
    }

    @Test
    fun parsesUnbornAndUntrackedBranches() {
        val unborn = GitParsing.parseStatus("## No commits yet on main\u0000?? a\u0000")
        assertEquals("main", unborn.branch)
        assertFalse(unborn.hasCommits)
        val local = GitParsing.parseStatus("## feature/x\u0000")
        assertEquals("feature/x", local.branch)
        assertNull(local.upstream)
        val detached = GitParsing.parseStatus("## HEAD (no branch)\u0000")
        assertTrue(detached.detached)
    }

    @Test
    fun parsesMultiFilePatch() {
        val patch = """
            diff --git a/one.txt b/one.txt
            index 1..2 100644
            --- a/one.txt
            +++ b/one.txt
            @@ -1,2 +1,2 @@
             same
            -old
            +new
            diff --git a/img.png b/img.png
            Binary files a/img.png and b/img.png differ
            diff --git a/gone.txt b/gone.txt
            deleted file mode 100644
            --- a/gone.txt
            +++ /dev/null
            @@ -1 +0,0 @@
            -bye
        """.trimIndent()
        val files = GitParsing.parsePatch(patch)
        assertEquals(listOf("one.txt", "img.png", "gone.txt"), files.map { it.path })
        assertEquals(1, files[0].additions)
        assertEquals(1, files[0].deletions)
        val added = files[0].diffLines.first { it.type == DiffLineType.ADDITION }
        assertEquals("new", added.text)
        assertEquals(2, added.newLine)
        assertTrue(files[1].binary)
        assertEquals(1, files[2].deletions)
    }

    @Test
    fun parsesLog() {
        val log = "abcdef1234\u001ffix: thing\u001fAda\u001f2 hours ago\u001e\nfedcba9876\u001finit\u001fBob\u001f3 days ago\u001e\n"
        val commits = GitParsing.parseLog(log)
        assertEquals(2, commits.size)
        assertEquals("abcdef1", commits[0].shortHash)
        assertEquals("init", commits[1].subject)
    }

    @Test
    fun validatesBranchNames() {
        assertTrue(isValidGitBranchName("feature/login-v2"))
        assertFalse(isValidGitBranchName("-bad"))
        assertFalse(isValidGitBranchName("a..b"))
        assertFalse(isValidGitBranchName("has space"))
    }
}
