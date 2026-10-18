package io.github.ninock.pocketcode.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class WorkspaceFileOpsTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("workspace").toFile()
        write("settings.gradle.kts", "include(\":app\")")
        write("app/src/main/kotlin/Main.kt", "fun main() {}")
        write("app/build/outputs/apk/debug/app-debug.apk", "APK".repeat(1000))
        write("app/build/intermediates/classes/Foo.class", "class")
        write(".gradle/8.10/cache.bin", "cache")
        write(".git/HEAD", "ref: refs/heads/main")
        write("node_modules/pkg/index.js", "module.exports = 1")
        write(".claude/settings.json", "{}")
        write(".claude.json", "{}")
        File(root, "empty-dir").mkdirs()
    }

    private fun write(path: String, text: String) {
        File(root, path).apply { parentFile?.mkdirs() }.writeText(text)
    }

    private fun zipEntries(plan: WorkspaceFileOps.ExportPlan, prefix: String = "demo"): Map<String, String> {
        val bytes = ByteArrayOutputStream().also { WorkspaceFileOps.writeZip(plan, it, prefix) }.toByteArray()
        val entries = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = if (entry.isDirectory) "" else zip.readBytes().decodeToString()
            }
        }
        return entries
    }

    @Test
    fun wholeProjectExportContainsSourceOnly() {
        write("release/app-release.apk", "APK")
        val entries = zipEntries(WorkspaceFileOps.planExport(root, null))

        assertEquals("fun main() {}", entries["demo/app/src/main/kotlin/Main.kt"])
        assertTrue("demo/settings.gradle.kts" in entries)
        assertTrue("demo/empty-dir/" in entries)
        assertFalse(entries.keys.any { it.startsWith("demo/app/build") })
        assertFalse(entries.keys.any { it.startsWith("demo/.gradle") })
        assertFalse(entries.keys.any { it.startsWith("demo/.git/") })
        assertFalse(entries.keys.any { it.startsWith("demo/node_modules") })
        assertFalse(entries.keys.any { it.startsWith("demo/.claude") })
        assertFalse(entries.keys.any { it.endsWith(".apk") })
    }

    @Test
    fun includingBuildFilesExportsEverythingButRuntimeMetadata() {
        val entries = zipEntries(WorkspaceFileOps.planExport(root, null, skipBuildFiles = false))

        assertTrue("demo/app/build/outputs/apk/debug/app-debug.apk" in entries)
        assertTrue("demo/.gradle/8.10/cache.bin" in entries)
        assertTrue("demo/node_modules/pkg/index.js" in entries)
        assertFalse(entries.keys.any { it.startsWith("demo/.claude") })

        val selected = zipEntries(WorkspaceFileOps.planExport(root, listOf("app"), skipBuildFiles = false)).keys
        assertTrue("demo/app/build/intermediates/classes/Foo.class" in selected)
    }

    @Test
    fun selectedFolderSkipsBuildFoldersInside() {
        val files = zipEntries(WorkspaceFileOps.planExport(root, listOf("app"))).keys.filterNot { it.endsWith("/") }
        assertEquals(listOf("demo/app/src/main/kotlin/Main.kt"), files)
    }

    @Test
    fun explicitlySelectedBuildPathsAreExported() {
        val plan = WorkspaceFileOps.planExport(root, listOf("app/build/outputs", "settings.gradle.kts", "app/build/outputs/apk"))
        val files = zipEntries(plan).keys.filterNot { it.endsWith("/") }

        assertEquals(
            listOf("demo/app/build/outputs/apk/debug/app-debug.apk", "demo/settings.gradle.kts"),
            files.sorted(),
        )
        assertEquals(2, plan.fileCount)

        val withParent = WorkspaceFileOps.planExport(root, listOf("app", "app/build"))
        val parentFiles = zipEntries(withParent).keys.filterNot { it.endsWith("/") }.sorted()
        assertEquals(
            listOf(
                "demo/app/build/intermediates/classes/Foo.class",
                "demo/app/build/outputs/apk/debug/app-debug.apk",
                "demo/app/src/main/kotlin/Main.kt",
            ),
            parentFiles,
        )
        assertEquals(3, withParent.fileCount)
    }

    @Test
    fun selectionNormalizationDropsPathsCoveredByAncestors() {
        assertEquals(
            listOf("app", "app/build/x", "readme.md"),
            WorkspaceFileOps.normalizeSelection(listOf("app/src", "app", "/readme.md/", "app/build/x", "app")),
        )
        assertEquals("app", WorkspaceFileOps.coveringSelection("app/src/main", setOf("app")))
        assertEquals(null, WorkspaceFileOps.coveringSelection("app/build/outputs", setOf("app")))
        assertEquals("app/build", WorkspaceFileOps.coveringSelection("app/build/outputs", setOf("app", "app/build")))
    }

    @Test
    fun escapingPathsAreRejected() {
        assertEquals(null, WorkspaceFileOps.resolveInside(root, "../outside"))
        assertTrue(WorkspaceFileOps.planExport(root, listOf("../")).items.isEmpty())
    }

    @Test
    fun listingIsLazyAndHidesRuntimeMetadata() {
        val rootNames = WorkspaceFileOps.listDirectory(root, "").map { it.name }.toSet()
        assertFalse(".claude" in rootNames)
        assertFalse(".claude.json" in rootNames)
        assertTrue("app" in rootNames)

        val app = WorkspaceFileOps.listDirectory(root, "").single { it.name == "app" }
        assertEquals(2, app.childCount)
        val build = WorkspaceFileOps.listDirectory(root, "app").single { it.name == "build" }
        assertEquals("app/build", build.path)
        assertEquals(1, build.depth)
    }

    @Test
    fun buildArtifactsAreDiscovered() {
        val artifacts = WorkspaceFileOps.findBuildArtifacts(root)
        assertEquals(listOf("app/build/outputs/apk/debug/app-debug.apk"), artifacts.map { it.path })
    }

    @Test
    fun singleFileIsCopiedVerbatim() {
        val apk = File(root, "app/build/outputs/apk/debug/app-debug.apk")
        val out = ByteArrayOutputStream()
        WorkspaceFileOps.writeSingleFile(apk, "app-debug.apk", out)
        assertEquals(apk.readText(), out.toByteArray().decodeToString())
    }
}
