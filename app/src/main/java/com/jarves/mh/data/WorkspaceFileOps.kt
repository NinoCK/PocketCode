package com.jarves.mh.data

import com.jarves.mh.model.WorkspaceEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * File-system operations behind the project Files tab: lazy directory listing, build-output
 * discovery, exporting (ZIP or raw single file) and the user's own edits (text editing, rename,
 * delete, new files and folders, uploads). Everything is confined to the project root: symlinks
 * and paths that canonicalise outside the root are never listed, exported or changed, and the
 * agent's runtime metadata can't be touched.
 *
 * By default exports carry the project's source only: build outputs, Gradle/IDE caches, VCS data
 * and dependency folders ([EXPORT_EXCLUDED_DIRS]) are skipped wherever they are found inside an
 * exported folder. A path the user selected explicitly is always exported, even an excluded one.
 * Passing `skipBuildFiles = false` exports everything except the agent's runtime metadata.
 */
object WorkspaceFileOps {

    /** One file or directory scheduled for export, with its root-relative path. */
    data class ExportItem(val file: File, val relativePath: String, val isDirectory: Boolean, val sizeBytes: Long)

    data class ExportPlan(val items: List<ExportItem>) {
        val fileCount: Int = items.count { !it.isDirectory }
        val totalBytes: Long = items.sumOf { if (it.isDirectory) 0L else it.sizeBytes }
    }

    data class ExportProgress(
        val filesDone: Int,
        val bytesDone: Long,
        val currentPath: String,
        val skippedFiles: Int,
    )

    /** Size and modification time of a file, used to notice when it changed on disk under the editor. */
    data class FileStamp(val sizeBytes: Long, val modifiedMillis: Long)

    /**
     * A file loaded for the viewer. [text] has `\n` line endings; [crlf] records that the file used
     * `\r\n` throughout, so saving restores them. [readOnlyReason] says why the file can't be
     * edited here (binary, too large to load whole, not valid UTF-8) and is null when it can.
     */
    data class TextFile(
        val text: String,
        val crlf: Boolean,
        val binary: Boolean,
        val readOnlyReason: String?,
        val stamp: FileStamp,
    )

    /** Paths actually removed by [delete] and the ones that could not be. */
    data class DeleteResult(val deleted: List<String>, val failed: List<String>)

    /** Agent runtime state that lives inside the workspace but is not part of the user's project. */
    fun isRuntimeMetadata(relativePath: String): Boolean =
        relativePath == ".claude" || relativePath == ".claude.json" || relativePath.startsWith(".claude/")

    /** True for folders (build, .gradle, .git, node_modules, ...) that exports skip by default. */
    fun isExportExcludedName(name: String): Boolean = name in EXPORT_EXCLUDED_DIRS

    /**
     * The selected ancestor whose export would include [path], or null. With [skipBuildFiles] an
     * excluded folder between the two (e.g. `app/build` under a selected `app`) breaks coverage,
     * because the export skips it.
     */
    fun coveringSelection(path: String, selected: Set<String>, skipBuildFiles: Boolean = true): String? {
        var node = path.trim('/')
        while (node.isNotEmpty()) {
            if (skipBuildFiles && isExportExcludedName(node.substringAfterLast('/'))) return null
            val parent = node.substringBeforeLast('/', "")
            if (parent.isEmpty()) return null
            if (parent in selected) return parent
            node = parent
        }
        return null
    }

    /** Lists the direct children of [relativeDir] ("" for the project root). */
    fun listDirectory(root: File, relativeDir: String): List<WorkspaceEntry> {
        val directory = resolveInside(root, relativeDir) ?: return emptyList()
        if (!directory.isDirectory) return emptyList()
        val depth = if (relativeDir.isEmpty()) 0 else relativeDir.count { it == '/' } + 1
        val rootPath = root.canonicalFile.toPath()
        return directory.listFiles().orEmpty()
            .asSequence()
            .filter { !Files.isSymbolicLink(it.toPath()) && !isTemporaryWrite(it.name) }
            .mapNotNull { file ->
                val relative = if (relativeDir.isEmpty()) file.name else "$relativeDir/${file.name}"
                if (isRuntimeMetadata(relative) || !isInside(rootPath, file)) return@mapNotNull null
                val isDirectory = file.isDirectory
                WorkspaceEntry(
                    path = relative,
                    name = file.name,
                    isDirectory = isDirectory,
                    depth = depth,
                    sizeBytes = if (isDirectory) 0L else file.length(),
                    childCount = if (isDirectory) countChildren(file, relative) else 0,
                    modifiedMillis = file.lastModified(),
                )
            }
            .toList()
    }

    /** Finds installable build outputs (APK/AAB) under `<module>/build/outputs`, newest first. */
    fun findBuildArtifacts(root: File, limit: Int = 6): List<WorkspaceEntry> {
        if (!root.isDirectory) return emptyList()
        val moduleCandidates = buildList {
            add(root)
            root.childDirectories().forEach { child ->
                add(child)
                addAll(child.childDirectories())
            }
        }.take(MAX_ARTIFACT_MODULE_CANDIDATES)
        return moduleCandidates.asSequence()
            .map { File(it, "build/outputs") }
            .filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) && isInside(root, it) }
            .flatMap { outputs ->
                outputs.walkTopDown()
                    .maxDepth(6)
                    .onEnter { !Files.isSymbolicLink(it.toPath()) }
                    .filter { it.isFile && it.extension.lowercase() in INSTALLABLE_EXTENSIONS }
            }
            .distinctBy { it.absolutePath }
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                WorkspaceEntry(
                    path = relative,
                    name = file.name,
                    isDirectory = false,
                    depth = relative.count { it == '/' },
                    sizeBytes = file.length(),
                    modifiedMillis = file.lastModified(),
                )
            }
            .sortedByDescending { it.modifiedMillis }
            .take(limit)
            .toList()
    }

    /** Drops duplicates and any path whose export is already covered by a selected ancestor. */
    fun normalizeSelection(paths: Collection<String>, skipBuildFiles: Boolean = true): List<String> {
        val cleaned = paths.map { it.trim('/') }.filter { it.isNotEmpty() }.distinct().sorted()
        if (cleaned.isEmpty()) return emptyList()
        val selected = cleaned.toSet()
        return cleaned.filter { path -> coveringSelection(path, selected, skipBuildFiles) == null }
    }

    /**
     * Collects every file and directory to export. [paths] null (or containing "") exports the whole
     * project; otherwise only the selected paths. With [skipBuildFiles] the [EXPORT_EXCLUDED_DIRS]
     * are skipped inside any exported folder and a whole-project export also leaves out loose
     * APK/AAB packages. [onScanned] receives the running file count so the UI can show scan progress.
     */
    fun planExport(
        root: File,
        paths: Collection<String>?,
        skipBuildFiles: Boolean = true,
        checkActive: () -> Unit = {},
        onScanned: (Int) -> Unit = {},
    ): ExportPlan {
        val wholeProject = paths == null || paths.any { it.trim('/').isEmpty() }
        val selection = if (wholeProject) listOf("") else normalizeSelection(paths.orEmpty(), skipBuildFiles)
        val rootPath = root.canonicalFile.toPath()
        val items = mutableListOf<ExportItem>()
        val seen = hashSetOf<String>()
        var files = 0
        selection.forEach { selected ->
            val start = resolveInside(root, selected) ?: return@forEach
            if (!start.exists() || Files.isSymbolicLink(start.toPath())) return@forEach
            start.walkTopDown()
                .onEnter { directory ->
                    directory == start || (
                        !(skipBuildFiles && isExportExcludedName(directory.name)) &&
                            !Files.isSymbolicLink(directory.toPath()) &&
                            !isRuntimeMetadata(directory.relativeTo(root).invariantSeparatorsPath) &&
                            isInside(rootPath, directory)
                        )
                }
                .forEach { file ->
                    if (file == root) return@forEach
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    if (isRuntimeMetadata(relative) || Files.isSymbolicLink(file.toPath()) || !isInside(rootPath, file)) return@forEach
                    val isDirectory = file.isDirectory
                    if (skipBuildFiles && wholeProject && !isDirectory && file.extension.lowercase() in INSTALLABLE_EXTENSIONS) return@forEach
                    if (!seen.add(relative)) return@forEach
                    items += ExportItem(file, relative, isDirectory, if (isDirectory) 0L else file.length())
                    if (!isDirectory) {
                        files++
                        if (files % 250 == 0) {
                            checkActive()
                            onScanned(files)
                        }
                    }
                }
        }
        onScanned(files)
        return ExportPlan(items)
    }

    /**
     * Writes [plan] as a ZIP whose entries live under [rootFolder]/. Files that vanish or cannot be
     * read mid-export are skipped and counted instead of failing the whole archive.
     */
    fun writeZip(
        plan: ExportPlan,
        output: OutputStream,
        rootFolder: String,
        checkActive: () -> Unit = {},
        onProgress: (ExportProgress) -> Unit = {},
    ) {
        var filesDone = 0
        var bytesDone = 0L
        var skipped = 0
        val prefix = rootFolder.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        ZipOutputStream(output.buffered(COPY_BUFFER_BYTES)).use { zip ->
            if (prefix.isNotEmpty()) {
                zip.putNextEntry(ZipEntry(prefix))
                zip.closeEntry()
            }
            plan.items.forEach { item ->
                checkActive()
                if (item.isDirectory) {
                    zip.putNextEntry(ZipEntry("$prefix${item.relativePath}/").apply { time = item.file.lastModified() })
                    zip.closeEntry()
                    return@forEach
                }
                val input = runCatching { item.file.inputStream() }.getOrNull()
                if (input == null) {
                    skipped++
                    bytesDone += item.sizeBytes
                    onProgress(ExportProgress(filesDone, bytesDone, item.relativePath, skipped))
                    return@forEach
                }
                zip.setLevel(if (item.file.extension.lowercase() in PRECOMPRESSED_EXTENSIONS) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
                zip.putNextEntry(ZipEntry("$prefix${item.relativePath}").apply { time = item.file.lastModified() })
                val startBytes = bytesDone
                input.use { stream ->
                    copyWithProgress(stream, zip, buffer, checkActive) { copied ->
                        bytesDone = startBytes + copied
                        onProgress(ExportProgress(filesDone, bytesDone, item.relativePath, skipped))
                    }
                }
                zip.closeEntry()
                filesDone++
                onProgress(ExportProgress(filesDone, bytesDone, item.relativePath, skipped))
            }
        }
    }

    /** Copies one file verbatim (used when exactly one file is exported). */
    fun writeSingleFile(
        file: File,
        relativePath: String,
        output: OutputStream,
        checkActive: () -> Unit = {},
        onProgress: (ExportProgress) -> Unit = {},
    ) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        output.buffered(COPY_BUFFER_BYTES).use { out ->
            file.inputStream().use { input ->
                copyWithProgress(input, out, buffer, checkActive) { copied ->
                    onProgress(ExportProgress(0, copied, relativePath, 0))
                }
            }
        }
        onProgress(ExportProgress(1, file.length(), relativePath, 0))
    }

    /** Resolves a root-relative path, refusing anything that escapes the root. */
    fun resolveInside(root: File, relativePath: String): File? {
        val trimmed = relativePath.trim('/')
        val file = if (trimmed.isEmpty()) root else File(root, trimmed)
        return file.takeIf { isInside(root, it) }
    }

    // ---------------------------------------------------------------------------------------------
    // Changing files by hand
    // ---------------------------------------------------------------------------------------------

    /** Why [name] can't be used for a file or folder, or null when it can. */
    fun nameError(name: String): String? = when {
        name.isBlank() -> "Enter a name"
        name == "." || name == ".." -> "“$name” is reserved"
        name.any { it == '/' || it == '\\' } -> "Names can't contain / or \\"
        name.any { it.isISOControl() } -> "Names can't contain control characters"
        name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> "That name is too long"
        else -> null
    }

    /**
     * Loads a file for the viewer and editor. Files over [maxBytes] are cut off and binary files
     * (a NUL byte near the start) come back empty. Those, and text that isn't valid UTF-8, are
     * read-only, so a save can never write back less than the file held or different bytes.
     */
    fun readTextFile(root: File, relativePath: String, maxBytes: Int = MAX_TEXT_FILE_BYTES): TextFile {
        val file = resolveInside(root, relativePath) ?: throw IllegalArgumentException("$relativePath is outside the project")
        require(file.isFile) { "${file.name} is not a file" }
        val stamp = FileStamp(file.length(), file.lastModified())
        val bytes = file.inputStream().use { readUpTo(it, maxBytes + 1) }
        val truncated = bytes.size > maxBytes
        val data = if (truncated) bytes.copyOf(maxBytes) else bytes
        if ((0 until minOf(data.size, BINARY_PROBE_BYTES)).any { data[it] == 0.toByte() }) {
            return TextFile("", crlf = false, binary = true, readOnlyReason = "Binary files can't be shown or edited here.", stamp = stamp)
        }
        val strict = if (truncated) null else decodeUtf8Strict(data)
        val raw = strict ?: String(data, Charsets.UTF_8)
        val crlf = strict != null && usesCrlfOnly(raw)
        return TextFile(
            text = if (crlf) raw.replace("\r\n", "\n") else raw,
            crlf = crlf,
            binary = false,
            readOnlyReason = when {
                truncated -> "Showing the first ${maxBytes / 1024} KB. Files this large can't be edited here."
                strict == null -> "This file isn't UTF-8 text, so editing it here could damage it."
                else -> null
            },
            stamp = stamp,
        )
    }

    /** The [FileStamp] of a project file, or null when it no longer exists as a file. */
    fun stampOf(root: File, relativePath: String): FileStamp? =
        resolveInside(root, relativePath)?.takeIf { it.isFile }?.let { FileStamp(it.length(), it.lastModified()) }

    /** Saves editor text, restoring `\r\n` line endings when [crlf]. See [writeAtomically]. */
    fun writeTextFile(root: File, relativePath: String, text: String, crlf: Boolean): FileStamp {
        val file = resolveChangeable(root, relativePath)
        val content = if (crlf) text.replace("\r\n", "\n").replace("\n", "\r\n") else text
        val bytes = content.toByteArray(Charsets.UTF_8)
        writeAtomically(file) { it.write(bytes) }
        return FileStamp(file.length(), file.lastModified())
    }

    /**
     * Renames the file or folder at [relativePath] within its folder and returns the new
     * root-relative path. Invalid names, names already in use and agent metadata are refused.
     */
    fun rename(root: File, relativePath: String, newName: String): String {
        val source = resolveChangeable(root, relativePath)
        require(Files.exists(source.toPath(), LinkOption.NOFOLLOW_LINKS)) { "${source.name} no longer exists" }
        val name = newName.trim()
        nameError(name)?.let { throw IllegalArgumentException(it) }
        val relative = relativePath.trim('/')
        if (name == source.name) return relative
        val parent = relative.substringBeforeLast('/', "")
        val targetRelative = if (parent.isEmpty()) name else "$parent/$name"
        val target = resolveChangeable(root, targetRelative)
        try {
            // Without REPLACE_EXISTING the move refuses to overwrite whatever already has the name.
            Files.move(source.toPath(), target.toPath())
        } catch (_: FileAlreadyExistsException) {
            throw IllegalArgumentException("Something named $name already exists here")
        }
        return targetRelative
    }

    /** Creates an empty file or folder called [name] in [parentDir] and returns its root-relative path. */
    fun create(root: File, parentDir: String, name: String, isDirectory: Boolean): String {
        val clean = name.trim()
        nameError(clean)?.let { throw IllegalArgumentException(it) }
        val parent = parentDir.trim('/')
        val relative = if (parent.isEmpty()) clean else "$parent/$clean"
        val target = resolveChangeable(root, relative)
        require(target.parentFile?.isDirectory == true) { "${parent.ifEmpty { "The project folder" }} no longer exists" }
        try {
            if (isDirectory) Files.createDirectory(target.toPath()) else Files.createFile(target.toPath())
        } catch (_: FileAlreadyExistsException) {
            throw IllegalArgumentException("Something named $clean already exists here")
        }
        return relative
    }

    /**
     * Permanently deletes [paths] (root-relative); a folder goes with everything inside it. Links
     * inside a deleted folder are removed, never followed, so nothing outside the project is
     * touched. A path that is already gone counts as deleted.
     */
    fun delete(root: File, paths: Collection<String>): DeleteResult {
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        normalizeSelection(paths, skipBuildFiles = false).forEach { relative ->
            val removed = runCatching {
                val file = resolveChangeable(root, relative)
                if (Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) deleteTree(file.toPath())
            }.isSuccess
            if (removed) deleted += relative else failed += relative
        }
        return DeleteResult(deleted, failed)
    }

    /** The folder uploads into [relativeDir] ("" = root) land in, recreated if it went missing. */
    fun uploadFolder(root: File, relativeDir: String): File {
        val folder = if (relativeDir.trim('/').isEmpty()) root else resolveChangeable(root, relativeDir)
        if (!folder.exists()) folder.mkdirs()
        require(folder.isDirectory) { "${folder.name} is not a folder" }
        return folder
    }

    /** The file [name] inside the root-relative folder [relativeDir], provided the user may write it. */
    fun childForWrite(root: File, relativeDir: String, name: String): File {
        nameError(name)?.let { throw IllegalArgumentException(it) }
        val dir = relativeDir.trim('/')
        return resolveChangeable(root, if (dir.isEmpty()) name else "$dir/$name")
    }

    /** A usable file name from a picked document's display name: its last segment, minus control characters. */
    fun sanitizeFileName(displayName: String): String {
        val name = displayName.replace('\\', '/').substringAfterLast('/').filterNot { it.isISOControl() }.trim()
        return name.takeIf { nameError(it) == null } ?: "upload"
    }

    /**
     * [name] when nothing in [folder] has it and it isn't in [taken]; otherwise the first free
     * `stem-2.ext`, `stem-3.ext`, ...
     */
    fun uniqueName(folder: File, name: String, taken: Set<String> = emptySet()): String {
        fun free(candidate: String) =
            candidate !in taken && !Files.exists(File(folder, candidate).toPath(), LinkOption.NOFOLLOW_LINKS)
        if (free(name)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        return generateSequence(2) { it + 1 }.map { "$stem-$it$extension" }.first(::free)
    }

    /** Streams [input] into [target] through [writeAtomically], reporting the bytes copied so far. */
    fun writeStream(target: File, input: InputStream, checkActive: () -> Unit = {}, onCopied: (Long) -> Unit = {}) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        writeAtomically(target) { output -> copyWithProgress(input, output, buffer, checkActive, onCopied) }
    }

    /**
     * Writes [target] through a temporary file in the same folder that replaces it only once
     * complete, so a failed or cancelled write never leaves half a file behind or loses the old
     * contents. An existing file keeps its permissions (e.g. the executable bit on `gradlew`).
     */
    fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
        require(!target.isDirectory) { "${target.name} is a folder" }
        val folder = target.parentFile ?: throw IOException("${target.name} has no parent folder")
        val temporary = File(folder, "$TEMP_PREFIX${UUID.randomUUID().toString().take(8)}$TEMP_SUFFIX")
        try {
            FileOutputStream(temporary).use { output ->
                write(output)
                output.flush()
                output.fd.sync()
            }
            if (target.exists()) {
                runCatching { Files.setPosixFilePermissions(temporary.toPath(), Files.getPosixFilePermissions(target.toPath())) }
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    /**
     * Resolves a root-relative path the user may change: never the root itself, the agent's
     * metadata, a path with empty, `.` or `..` segments, or a symlink.
     */
    private fun resolveChangeable(root: File, relativePath: String): File {
        val relative = relativePath.trim('/')
        require(relative.isNotEmpty()) { "The project folder itself can't be changed here" }
        require(relative.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Invalid path: $relative" }
        require(!isRuntimeMetadata(relative)) { "${relative.substringAfterLast('/')} belongs to the agent's settings" }
        val file = resolveInside(root, relative) ?: throw IllegalArgumentException("$relative is outside the project")
        require(!Files.isSymbolicLink(file.toPath())) { "${file.name} is a link and can't be changed here" }
        return file
    }

    /** Deletes [start] and, for a folder, everything below it, removing links instead of following them. */
    private fun deleteTree(start: Path) {
        Files.walkFileTree(
            start,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    /** A save or upload still being written by [writeAtomically]; kept out of listings. */
    private fun isTemporaryWrite(name: String): Boolean = name.startsWith(TEMP_PREFIX) && name.endsWith(TEMP_SUFFIX)

    private fun readUpTo(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(minOf(limit, COPY_BUFFER_BYTES))
        while (output.size() < limit) {
            val read = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** True when the text has line breaks and every one is `\r\n`, so they can be restored exactly. */
    private fun usesCrlfOnly(text: String): Boolean {
        var sawLineBreak = false
        for (index in text.indices) {
            if (text[index] != '\n') continue
            if (index == 0 || text[index - 1] != '\r') return false
            sawLineBreak = true
        }
        return sawLineBreak
    }

    private fun isInside(root: File, file: File): Boolean = isInside(root.canonicalFile.toPath(), file)

    private fun isInside(rootPath: Path, file: File): Boolean = runCatching {
        file.canonicalFile.toPath().startsWith(rootPath)
    }.getOrDefault(false)

    private fun countChildren(directory: File, relative: String): Int {
        val names = directory.list() ?: return 0
        return names.count { !isRuntimeMetadata("$relative/$it") }
    }

    private fun File.childDirectories(): List<File> =
        listFiles().orEmpty().filter {
            it.isDirectory && !Files.isSymbolicLink(it.toPath()) && it.name !in ARTIFACT_SEARCH_SKIP
        }

    private inline fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        buffer: ByteArray,
        checkActive: () -> Unit,
        onCopied: (Long) -> Unit,
    ) {
        var copied = 0L
        var sinceReport = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            copied += read
            sinceReport += read
            if (sinceReport >= PROGRESS_STEP_BYTES) {
                sinceReport = 0L
                checkActive()
                onCopied(copied)
            }
        }
        onCopied(copied)
    }

    /** Largest file the viewer loads whole; bigger files are shown cut off and read-only. */
    const val MAX_TEXT_FILE_BYTES = 512 * 1024
    private const val BINARY_PROBE_BYTES = 8 * 1024
    private const val MAX_NAME_BYTES = 255
    private const val TEMP_PREFIX = ".mh-write-"
    private const val TEMP_SUFFIX = ".tmp"
    private const val COPY_BUFFER_BYTES = 64 * 1024
    private const val PROGRESS_STEP_BYTES = 512L * 1024L
    private const val MAX_ARTIFACT_MODULE_CANDIDATES = 400
    private val INSTALLABLE_EXTENSIONS = setOf("apk", "aab")
    private val EXPORT_EXCLUDED_DIRS = setOf(
        "build", ".gradle", ".kotlin", ".cxx", ".externalNativeBuild", ".idea", ".git", ".claude",
        "node_modules", ".next", ".cache", ".venv", "venv", "__pycache__",
    )
    private val ARTIFACT_SEARCH_SKIP = setOf(".git", ".gradle", ".claude", "node_modules", "build", ".idea", ".venv", "venv")
    private val PRECOMPRESSED_EXTENSIONS = setOf(
        "apk", "aab", "jar", "aar", "zip", "gz", "tgz", "xz", "bz2", "7z", "zst", "br",
        "png", "jpg", "jpeg", "webp", "gif", "avif", "mp3", "mp4", "m4a", "ogg", "webm", "woff", "woff2",
    )
}
