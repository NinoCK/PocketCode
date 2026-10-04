package com.jarves.mh.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarves.mh.data.WorkspaceArchiveImport

/** Most files (photos or documents) one chat message can carry. */
internal const val MaxChatAttachments = 5

/** MIME types a ZIP may carry; many file providers label archives `application/octet-stream`. */
internal val ZipPickerMimeTypes = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-zip",
    "application/octet-stream",
)

private data class AttachSource(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/** What the composer's attach button offers: gallery images, any document, or a ZIP to extract. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachSourceSheet(
    canAddFiles: Boolean,
    onDismiss: () -> Unit,
    onPhotos: () -> Unit,
    onFiles: () -> Unit,
    onZip: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Add to chat",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
            )
            val limitNote = "Attachment limit reached for this message"
            val sources = listOf(
                AttachSource(Icons.Rounded.Photo, "Photos", if (canAddFiles) "Pick images from your gallery" else limitNote, canAddFiles, onPhotos),
                AttachSource(Icons.Rounded.Description, "Files", if (canAddFiles) "Any document from the system file picker" else limitNote, canAddFiles, onFiles),
                AttachSource(Icons.Rounded.FolderZip, "ZIP archive", "Extract into this project or open as new", true, onZip),
            )
            sources.forEachIndexed { index, source ->
                val contentAlpha = if (source.enabled) 1f else 0.45f
                ListItem(
                    headlineContent = { Text(source.title, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(source.subtitle) },
                    leadingContent = {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = contentAlpha)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(source.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = contentAlpha)) }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    ),
                    modifier = Modifier
                        .clip(groupedShape(index, sources.size, outer = 24.dp, inner = 6.dp))
                        .clickable(enabled = source.enabled, onClick = source.onClick),
                )
            }
        }
    }
}

private enum class ZipDestination { UPLOADS, FILES_FOLDER, ROOT, CUSTOM }

/**
 * Where a ZIP picked in chat goes. By default it lands in `uploads/<archive>/`; the folder open in
 * the Files tab, the project root or any typed folder can be chosen instead, and "merge" lays the
 * archive's files straight into that folder. The archive can also become a project of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZipImportSheet(
    uri: Uri,
    guestRoot: String,
    filesTabDir: String,
    canImportAsProject: Boolean,
    onDismiss: () -> Unit,
    onExtract: (parentDir: String, merge: Boolean) -> Unit,
    onImportAsProject: () -> Unit,
) {
    val context = LocalContext.current
    val archiveName = remember(uri) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(cursor::getString) else null
            }
        }.getOrNull() ?: "archive.zip"
    }
    val showFilesFolder = filesTabDir.isNotBlank() && filesTabDir != WorkspaceArchiveImport.UPLOADS_DIR
    var destination by rememberSaveable { mutableStateOf(ZipDestination.UPLOADS) }
    var customDir by rememberSaveable { mutableStateOf("") }
    var merge by rememberSaveable { mutableStateOf(false) }

    val rawParent = when (destination) {
        ZipDestination.UPLOADS -> WorkspaceArchiveImport.UPLOADS_DIR
        ZipDestination.FILES_FOLDER -> filesTabDir
        ZipDestination.ROOT -> ""
        ZipDestination.CUSTOM -> customDir
    }
    val parentResult = runCatching { WorkspaceArchiveImport.normalizeRelativeDir(rawParent) }
    val parent = parentResult.getOrNull()
    val previewPath = parent?.let { dir ->
        val base = if (dir.isEmpty()) guestRoot else "$guestRoot/$dir"
        if (merge) "$base/" else "$base/${WorkspaceArchiveImport.sanitizeFolderName(archiveName.substringBeforeLast('.'))}/"
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Extract ZIP", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
            Text(
                archiveName,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
            SectionHeader("Destination")
            val options = buildList {
                add(Triple(ZipDestination.UPLOADS, "Uploads folder", "${WorkspaceArchiveImport.UPLOADS_DIR}/ · keeps imports apart from your code"))
                if (showFilesFolder) add(Triple(ZipDestination.FILES_FOLDER, "Folder open in Files", "$filesTabDir/"))
                add(Triple(ZipDestination.ROOT, "Project root", "Next to the project's own files"))
                add(Triple(ZipDestination.CUSTOM, "Another folder", "Type a path inside the project"))
            }
            options.forEachIndexed { index, (option, title, subtitle) ->
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { RadioButton(selected = destination == option, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .clip(groupedShape(index, options.size))
                        .selectable(selected = destination == option, role = Role.RadioButton) { destination = option },
                )
            }
            AnimatedVisibility(visible = destination == ZipDestination.CUSTOM) {
                OutlinedTextField(
                    value = customDir,
                    onValueChange = { customDir = it },
                    label = { Text("Folder in project") },
                    placeholder = { Text("e.g. assets/imported") },
                    singleLine = true,
                    isError = parentResult.isFailure,
                    supportingText = parentResult.exceptionOrNull()?.message?.let { message -> { Text(message) } },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            SectionHeader("Layout")
            ListItem(
                headlineContent = { Text("Merge into the folder") },
                supportingContent = {
                    Text(
                        if (merge) "Files go straight in, keeping the archive's paths. Files with the same path are replaced."
                        else "Creates a new folder named after the archive. Nothing existing is changed.",
                    )
                },
                trailingContent = { Switch(checked = merge, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .toggleable(value = merge, role = Role.Switch) { merge = it },
            )
            if (previewPath != null) {
                Row(Modifier.padding(start = 8.dp, top = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        previewPath,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "The extracted folder is attached to your next message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 34.dp, top = 2.dp, end = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onImportAsProject, enabled = canImportAsProject) { Text("Open as new project") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { parent?.let { onExtract(it, merge) } }, enabled = parent != null) { Text("Extract") }
            }
        }
    }
}
