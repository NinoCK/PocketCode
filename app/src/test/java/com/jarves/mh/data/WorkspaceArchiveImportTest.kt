package com.jarves.mh.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkspaceArchiveImportTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("project").toFile()
        write("src/Main.kt", "fun main() {}")
        write(".claude/settings.json", "{\"keep\":true}")
    }

    private fun write(path: String, text: String) {
        File(root, path).apply { parentFile?.mkdirs() }.writeText(text)
    }

    private fun read(path: String) = File(root, path).readText()

    private fun zip(vararg entries: Pair<String, String?>): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name))
                    text?.let { zip.write(it.toByteArray()) }
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun extract(archive: ByteArray, parentDir: String, merge: Boolean, name: String = "demo.zip") =
        WorkspaceArchiveImport.extractIntoProject(
            root = root,
            parentDir = parentDir,
            archiveName = name,
            merge = merge,
            maxBytes = 1_000_000,
            maxEntries = 1_000,
            openStream = { archive.inputStream() },
        )

    @Test
    fun newFolderIsNamedAfterArchiveAndSkipsMacJunk() {
        val result = extract(zip("a.txt" to "A", "lib/b.txt" to "B", "__MACOSX/._a.txt" to "x", ".DS_Store" to "x"), "uploads", merge = false)

        assertEquals("uploads/demo", result.relativePath)
        assertEquals(2, result.fileCount)
        assertEquals("A", read("uploads/demo/a.txt"))
        assertEquals("B", read("uploads/demo/lib/b.txt"))
        assertFalse(File(root, "uploads/demo/__MACOSX").exists())
        assertFalse(File(root, ".pocketdev/staging").exists())
    }

    @Test
    fun singleTopLevelFolderIsNotNestedTwice() {
        val result = extract(zip("my-app/" to null, "my-app/index.js" to "x"), "", merge = false, name = "download (3).zip")

        assertEquals("my-app", result.relativePath)
        assertEquals("x", read("my-app/index.js"))
    }

    @Test
    fun existingFolderGetsSuffixInsteadOfBeingTouched() {
        write("uploads/demo/old.txt", "old")
        val result = extract(zip("a.txt" to "A"), "uploads", merge = false)

        assertEquals("uploads/demo-2", result.relativePath)
        assertEquals("old", read("uploads/demo/old.txt"))
        assertFalse(File(root, "uploads/demo/a.txt").exists())
    }

    @Test
    fun mergeReplacesSamePathFilesButNeverAgentState() {
        val result = extract(
            zip("src/Main.kt" to "new", "README.md" to "hi", ".claude/settings.json" to "{}"),
            "",
            merge = true,
        )

        assertEquals("", result.relativePath)
        assertEquals(1, result.replacedFiles)
        assertEquals("new", read("src/Main.kt"))
        assertEquals("hi", read("README.md"))
        assertEquals("{\"keep\":true}", read(".claude/settings.json"))
    }

    @Test
    fun mergeAbortsBeforeMovingWhenFileAndFolderCollide() {
        write("docs", "a file")
        assertThrows(IllegalArgumentException::class.java) {
            extract(zip("README.md" to "hi", "docs/guide.md" to "x"), "", merge = true)
        }
        assertFalse(File(root, "README.md").exists())
        assertEquals("a file", read("docs"))
    }

    @Test
    fun zipSlipAndOversizedArchivesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { extract(zip("../evil.txt" to "x"), "uploads", merge = false) }
        assertFalse(File(root.parentFile, "evil.txt").exists())

        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceArchiveImport.extractIntoProject(root, "uploads", "big.zip", false, maxBytes = 3, maxEntries = 10, openStream = {
                zip("big.txt" to "too big").inputStream()
            })
        }
        assertTrue(File(root, "uploads").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun destinationMustStayInsideTheProjectAndOutOfMetadata() {
        assertEquals("assets/imported", WorkspaceArchiveImport.normalizeRelativeDir(" /assets//imported/ "))
        assertEquals("", WorkspaceArchiveImport.normalizeRelativeDir("./"))
        assertThrows(IllegalArgumentException::class.java) { WorkspaceArchiveImport.normalizeRelativeDir("../outside") }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceArchiveImport.normalizeRelativeDir(".claude") }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceArchiveImport.normalizeRelativeDir(".pocketdev/imports") }
    }

    @Test
    fun emptyArchiveIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { extract(zip("empty/" to null), "uploads", merge = false) }
    }
}
