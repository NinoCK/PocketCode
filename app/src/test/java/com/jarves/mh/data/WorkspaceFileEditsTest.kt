package com.jarves.mh.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class WorkspaceFileEditsTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("workspace").toFile()
        write("app/src/Main.kt", "fun main() {}\n")
        write("config/app.json", "{}")
        write(".claude.json", "{}")
    }

    private fun write(path: String, text: String) = File(root, path).apply { parentFile?.mkdirs() }.writeText(text)

    private fun writeBytes(path: String, bytes: ByteArray) = File(root, path).apply { parentFile?.mkdirs() }.writeBytes(bytes)

    private fun leftoverTemporaryFiles(): List<String> =
        root.walkTopDown().filter { it.name.startsWith(".mh-write-") }.map { it.name }.toList()

    // --- Reading and saving text ---------------------------------------------------------------

    @Test
    fun plainTextIsEditableAndSavesBack() {
        val file = WorkspaceFileOps.readTextFile(root, "app/src/Main.kt")
        assertEquals("fun main() {}\n", file.text)
        assertNull(file.readOnlyReason)
        assertFalse(file.crlf)

        WorkspaceFileOps.writeTextFile(root, "app/src/Main.kt", "fun main() { println(1) }\n", file.crlf)
        assertEquals("fun main() { println(1) }\n", File(root, "app/src/Main.kt").readText())
        assertTrue(leftoverTemporaryFiles().isEmpty())
    }

    @Test
    fun windowsLineEndingsAreEditedAsNewlinesAndRestoredOnSave() {
        writeBytes("notes.txt", "one\r\ntwo\r\n".toByteArray())
        val file = WorkspaceFileOps.readTextFile(root, "notes.txt")
        assertTrue(file.crlf)
        assertEquals("one\ntwo\n", file.text)

        WorkspaceFileOps.writeTextFile(root, "notes.txt", file.text + "three\n", file.crlf)
        assertArrayEquals("one\r\ntwo\r\nthree\r\n".toByteArray(), File(root, "notes.txt").readBytes())
    }

    @Test
    fun mixedLineEndingsRoundTripUnchanged() {
        val original = "a\r\nb\nc\r\n".toByteArray()
        writeBytes("mixed.txt", original)
        val file = WorkspaceFileOps.readTextFile(root, "mixed.txt")
        assertFalse(file.crlf)

        WorkspaceFileOps.writeTextFile(root, "mixed.txt", file.text, file.crlf)
        assertArrayEquals(original, File(root, "mixed.txt").readBytes())
    }

    @Test
    fun binaryFilesAreNotShownOrEditable() {
        writeBytes("icon.png", byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 0, 0, 1, 2))
        val file = WorkspaceFileOps.readTextFile(root, "icon.png")
        assertTrue(file.binary)
        assertEquals("", file.text)
        assertNotNull(file.readOnlyReason)
    }

    @Test
    fun invalidUtf8IsReadOnly() {
        writeBytes("latin1.txt", byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte()))
        val file = WorkspaceFileOps.readTextFile(root, "latin1.txt")
        assertFalse(file.binary)
        assertNotNull(file.readOnlyReason)
    }

    @Test
    fun largeFilesAreCutOffAndReadOnly() {
        write("big.txt", "x".repeat(100))
        val file = WorkspaceFileOps.readTextFile(root, "big.txt", maxBytes = 40)
        assertEquals("x".repeat(40), file.text)
        assertNotNull(file.readOnlyReason)
    }

    @Test
    fun stampChangesWhenTheFileChangesOnDisk() {
        val loaded = WorkspaceFileOps.readTextFile(root, "config/app.json").stamp
        assertEquals(loaded, WorkspaceFileOps.stampOf(root, "config/app.json"))
        write("config/app.json", "{\"changed\": true}")
        assertTrue(loaded != WorkspaceFileOps.stampOf(root, "config/app.json"))
        File(root, "config/app.json").delete()
        assertNull(WorkspaceFileOps.stampOf(root, "config/app.json"))
    }

    @Test
    fun savingKeepsTheExecutableBit() {
        assumeTrue("POSIX permissions", "posix" in FileSystems.getDefault().supportedFileAttributeViews())
        write("gradlew", "#!/bin/sh\n")
        val path = File(root, "gradlew").toPath()
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))

        WorkspaceFileOps.writeTextFile(root, "gradlew", "#!/bin/sh\necho hi\n", crlf = false)
        assertEquals("rwxr-xr-x", PosixFilePermissions.toString(Files.getPosixFilePermissions(path)))
    }

    @Test
    fun agentMetadataAndOutsidePathsCannotBeWritten() {
        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.writeTextFile(root, ".claude.json", "x", false) }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.writeTextFile(root, "../escape.txt", "x", false) }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.writeTextFile(root, "app/../.claude.json", "x", false) }
        assertEquals("{}", File(root, ".claude.json").readText())
    }

    // --- Rename, create, delete -----------------------------------------------------------------

    @Test
    fun renameMovesWithinTheFolder() {
        assertEquals("app/src/App.kt", WorkspaceFileOps.rename(root, "app/src/Main.kt", " App.kt "))
        assertFalse(File(root, "app/src/Main.kt").exists())
        assertEquals("fun main() {}\n", File(root, "app/src/App.kt").readText())

        assertEquals("module", WorkspaceFileOps.rename(root, "app", "module"))
        assertTrue(File(root, "module/src/App.kt").isFile)
    }

    @Test
    fun renameRefusesTakenInvalidAndReservedNames() {
        write("app/src/Other.kt", "other")
        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.rename(root, "app/src/Main.kt", "Other.kt") }
        assertEquals("other", File(root, "app/src/Other.kt").readText())

        listOf("", "  ", ".", "..", "a/b", "a\\b", "x\u0000y", "n".repeat(300)).forEach { name ->
            assertThrows(name, IllegalArgumentException::class.java) { WorkspaceFileOps.rename(root, "app/src/Main.kt", name) }
        }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.rename(root, "config", ".claude") }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.rename(root, "", "project") }
        assertTrue(File(root, "app/src/Main.kt").isFile)
    }

    @Test
    fun createMakesEmptyFilesAndFolders() {
        assertEquals("app/src/New.kt", WorkspaceFileOps.create(root, "app/src", "New.kt", isDirectory = false))
        assertEquals(0L, File(root, "app/src/New.kt").length())
        assertEquals("assets", WorkspaceFileOps.create(root, "", "assets", isDirectory = true))
        assertTrue(File(root, "assets").isDirectory)

        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.create(root, "app/src", "Main.kt", isDirectory = false) }
        assertEquals("fun main() {}\n", File(root, "app/src/Main.kt").readText())
        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.create(root, "missing", "a.txt", isDirectory = false) }
    }

    @Test
    fun deleteRemovesFoldersWithContentsAndSkipsCoveredPaths() {
        val result = WorkspaceFileOps.delete(root, listOf("app/src/Main.kt", "app", "config/app.json"))
        assertEquals(listOf("app", "config/app.json"), result.deleted)
        assertTrue(result.failed.isEmpty())
        assertFalse(File(root, "app").exists())
        assertTrue(File(root, "config").isDirectory)

        // Already gone counts as deleted; the root and agent metadata never are.
        assertEquals(listOf("app"), WorkspaceFileOps.delete(root, listOf("app")).deleted)
        assertEquals(listOf(".claude.json"), WorkspaceFileOps.delete(root, listOf(".claude.json")).failed)
        assertTrue(WorkspaceFileOps.delete(root, listOf("")).deleted.isEmpty())
        assertTrue(File(root, ".claude.json").isFile)
        assertTrue(root.isDirectory)
    }

    @Test
    fun deletingAFolderNeverFollowsLinksOutOfTheProject() {
        val outside = Files.createTempDirectory("outside").toFile()
        File(outside, "keep.txt").writeText("precious")
        File(root, "dir").mkdirs()
        val linked = runCatching { Files.createSymbolicLink(File(root, "dir/link").toPath(), outside.toPath()) }.isSuccess
        assumeTrue("symlinks", linked)

        assertEquals(listOf("dir"), WorkspaceFileOps.delete(root, listOf("dir")).deleted)
        assertFalse(File(root, "dir").exists())
        assertEquals("precious", File(outside, "keep.txt").readText())
    }

    // --- Uploads ----------------------------------------------------------------------------------

    @Test
    fun uploadNamesAreSanitizedAndMadeUnique() {
        assertEquals("report.pdf", WorkspaceFileOps.sanitizeFileName("Download/report.pdf"))
        assertEquals("evil.sh", WorkspaceFileOps.sanitizeFileName("..\\..\\evil.sh"))
        assertEquals("upload", WorkspaceFileOps.sanitizeFileName(".."))
        assertEquals("upload", WorkspaceFileOps.sanitizeFileName(""))

        val folder = File(root, "config")
        assertEquals("other.json", WorkspaceFileOps.uniqueName(folder, "other.json"))
        assertEquals("app-2.json", WorkspaceFileOps.uniqueName(folder, "app.json"))
        assertEquals("app-3.json", WorkspaceFileOps.uniqueName(folder, "app.json", taken = setOf("app-2.json")))
        write(".gitignore", "build/")
        assertEquals(".gitignore-2", WorkspaceFileOps.uniqueName(root, ".gitignore"))
    }

    @Test
    fun uploadsLandInTheChosenFolderAndReplaceAtomically() {
        val folder = WorkspaceFileOps.uploadFolder(root, "app/build/output")
        assertTrue(folder.isDirectory)
        val target = WorkspaceFileOps.childForWrite(root, "app/build/output", "app.apk")
        WorkspaceFileOps.writeStream(target, ByteArrayInputStream("APK".toByteArray()))
        assertEquals("APK", File(root, "app/build/output/app.apk").readText())

        // A failed upload leaves the file it was replacing untouched and no temporary file behind.
        val failing = object : InputStream() {
            private var sent = 0
            override fun read(): Int = if (sent++ < 10) 'x'.code else throw IOException("connection lost")
        }
        assertThrows(IOException::class.java) { WorkspaceFileOps.writeStream(target, failing) }
        assertEquals("APK", target.readText())
        assertTrue(leftoverTemporaryFiles().isEmpty())

        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.childForWrite(root, "", ".claude.json") }
        assertThrows(IllegalArgumentException::class.java) { WorkspaceFileOps.childForWrite(root, "", "../x") }
    }

    @Test
    fun inProgressWritesAreHiddenFromListings() {
        File(root, "config/.mh-write-1234abcd.tmp").writeText("partial")
        val names = WorkspaceFileOps.listDirectory(root, "config").map { it.name }
        assertEquals(listOf("app.json"), names)
    }
}
