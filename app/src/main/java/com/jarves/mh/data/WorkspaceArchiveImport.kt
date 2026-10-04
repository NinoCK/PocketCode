package com.jarves.mh.data

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Safe ZIP extraction for project imports and for archives attached in chat. Every entry is
 * confined to its destination (zip-slip paths are refused), macOS metadata is skipped, and the
 * entry count and extracted size are capped so a hostile archive cannot fill the device.
 */
object WorkspaceArchiveImport {

    /** Where an archive extracted into an existing project landed. [relativePath] is root-relative ("" = root). */
    data class ExtractResult(val relativePath: String, val fileCount: Int, val totalBytes: Long, val replacedFiles: Int)

    data class UnzipStats(val fileCount: Int, val totalBytes: Long)

    /** Folder (relative to the project root) that receives chat-imported archives by default. */
    const val UPLOADS_DIR = "uploads"

    /**
     * Streams [input] as a ZIP into [destination]. Throws on unsafe paths, more than [maxEntries]
     * entries, more than [maxBytes] extracted bytes, or an archive with no files. Partially
     * written output is left for the caller to clean up.
     */
    fun unzipInto(
        input: InputStream,
        destination: File,
        maxBytes: Long,
        maxEntries: Int,
        onProgress: (UnzipStats) -> Unit = {},
    ): UnzipStats {
        destination.mkdirs()
        val destinationPath = destination.canonicalFile.toPath()
        var extractedBytes = 0L
        var entries = 0
        var files = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= maxEntries) { "The ZIP contains too many files" }
                val entryName = entry.name.replace('\\', '/').trimStart('/')
                require(entryName.isNotBlank() && '\u0000' !in entryName) { "The ZIP contains an invalid path" }
                if (isArchiveJunk(entryName)) {
                    zip.closeEntry()
                    continue
                }
                val target = File(destination, entryName).canonicalFile
                require(target.toPath().startsWith(destinationPath)) { "The ZIP contains an unsafe path" }
                if (target.toPath() == destinationPath) {
                    zip.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            extractedBytes += count
                            require(extractedBytes <= maxBytes) { "The archive is too large for available storage" }
                            output.write(buffer, 0, count)
                        }
                    }
                    if (entry.time > 0) target.setLastModified(entry.time)
                    files++
                    if (files % PROGRESS_EVERY_FILES == 0) onProgress(UnzipStats(files, extractedBytes))
                }
                zip.closeEntry()
            }
        }
        require(files > 0) { "The ZIP does not contain any files" }
        return UnzipStats(files, extractedBytes).also(onProgress)
    }

    /**
     * Extracts an archive into the project at [root], under the root-relative folder [parentDir].
     *
     * Without [merge] the files go into a new folder inside [parentDir], named after the archive's
     * single top-level folder when it has one (so `app.zip` holding `app/` does not become
     * `app/app/`) or otherwise after [archiveName]; an existing name gets a `-2`, `-3`… suffix.
     * Nothing already in the project is touched.
     *
     * With [merge] the archive's paths are laid directly over [parentDir] exactly as stored, and
     * files with the same path are replaced. A path that is a file on one side and a folder on the
     * other aborts the merge before anything is moved.
     */
    fun extractIntoProject(
        root: File,
        parentDir: String,
        archiveName: String,
        merge: Boolean,
        maxBytes: Long,
        maxEntries: Int,
        openStream: () -> InputStream,
        onProgress: (UnzipStats) -> Unit = {},
    ): ExtractResult {
        val canonicalRoot = root.canonicalFile
        val parentRelative = normalizeRelativeDir(parentDir)
        val parent = WorkspaceFileOps.resolveInside(canonicalRoot, parentRelative)?.canonicalFile
            ?: throw IllegalArgumentException("The destination folder is outside the project")
        require(!parent.exists() || parent.isDirectory) { "$parentRelative is a file, not a folder" }
        parent.mkdirs()

        val staging = File(canonicalRoot, "$STAGING_DIR/extract-${UUID.randomUUID()}")
        try {
            val stats = openStream().use { unzipInto(it, staging, maxBytes, maxEntries, onProgress) }
            return if (merge) {
                val replaced = mergeTree(staging, parent) { relative ->
                    isProtectedPath(if (parentRelative.isEmpty()) relative else "$parentRelative/$relative")
                }
                ExtractResult(parentRelative, stats.fileCount, stats.totalBytes, replaced)
            } else {
                val visible = staging.listFiles().orEmpty()
                val singleFolder = visible.singleOrNull()?.takeIf(File::isDirectory)
                val content = singleFolder ?: staging
                val baseName = sanitizeFolderName(singleFolder?.name ?: archiveName.substringBeforeLast('.'))
                val target = uniqueChild(parent, baseName)
                Files.move(content.toPath(), target.toPath())
                ExtractResult(target.relativeTo(canonicalRoot).invariantSeparatorsPath, stats.fileCount, stats.totalBytes, 0)
            }
        } finally {
            staging.deleteRecursively()
            File(canonicalRoot, STAGING_DIR).takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
        }
    }

    /**
     * Normalizes a user-typed, root-relative folder. Rejects parent references and the app's own
     * metadata folders so an import cannot land in agent state.
     */
    fun normalizeRelativeDir(path: String): String {
        val segments = path.replace('\\', '/').split('/').map(String::trim).filter { it.isNotEmpty() && it != "." }
        require(segments.none { it == ".." }) { "The destination folder cannot contain .." }
        val normalized = segments.joinToString("/")
        require(!isProtectedPath(normalized)) { "Choose a project folder outside the app's metadata" }
        return normalized
    }

    private fun isProtectedPath(rootRelative: String): Boolean =
        WorkspaceFileOps.isRuntimeMetadata(rootRelative) || rootRelative == ".pocketdev" || rootRelative.startsWith(".pocketdev/")

    internal fun isArchiveJunk(entryName: String): Boolean =
        entryName.startsWith("__MACOSX/") || entryName == ".DS_Store" || entryName.endsWith("/.DS_Store")

    internal fun sanitizeFolderName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().trim('.').take(80).ifBlank { "archive" }

    private fun uniqueChild(parent: File, name: String): File {
        var candidate = File(parent, name)
        var suffix = 2
        while (candidate.exists()) candidate = File(parent, "$name-${suffix++}")
        return candidate
    }

    /**
     * Moves everything under [source] onto [target], replacing same-path files, except paths
     * [isProtected] flags (agent state the archive must not overwrite). Returns how many were replaced.
     */
    private fun mergeTree(source: File, target: File, isProtected: (String) -> Boolean): Int {
        fun relativeOf(file: File) = file.relativeTo(source).invariantSeparatorsPath
        val items = source.walkTopDown()
            .onEnter { it == source || !isProtected(relativeOf(it)) }
            .filter { it != source && !isProtected(relativeOf(it)) }
            .toList()
        items.forEach { item ->
            val relative = relativeOf(item)
            val destination = File(target, relative)
            if (item.isDirectory) {
                require(!destination.exists() || destination.isDirectory) { "$relative already exists as a file" }
            } else {
                require(!destination.isDirectory) { "$relative already exists as a folder" }
            }
            // A parent that exists as a file would make the move fail halfway through.
            var ancestor = destination.parentFile
            while (ancestor != null && ancestor != target) {
                require(!ancestor.isFile) { "${ancestor.relativeTo(target).invariantSeparatorsPath} already exists as a file" }
                ancestor = ancestor.parentFile
            }
        }
        var replaced = 0
        items.filter(File::isFile).forEach { file ->
            val destination = File(target, relativeOf(file))
            destination.parentFile?.mkdirs()
            if (destination.exists()) replaced++
            Files.move(file.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        items.filter(File::isDirectory).forEach { File(target, relativeOf(it)).mkdirs() }
        return replaced
    }

    private const val STAGING_DIR = ".pocketdev/staging"
    private const val PROGRESS_EVERY_FILES = 25
}
