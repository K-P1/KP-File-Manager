/**
 * Path: mobile/app/src/main/java/com/kp/filemanager/export/ProtectedExportScreen.kt
 * Purpose: Responsive Material 3 protected media export with selection, detailed previews and live byte progress.
 * Last changed: 2026-11-24, explain direct undo and allow retry of pending restores.
 */
package com.kp.filemanager.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedExportScreen(
    state: ProtectedExportUiState,
    actions: ProtectedExportViewModel,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var confirmExport by rememberSaveable { mutableStateOf(false) }
    var undoRequest by remember { mutableStateOf<UndoRequest?>(null) }
    var confirmRecovery by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val bundles = remember(state.preview, search, filter) {
        val mediaType = if (filter == "Movies") "Movie" else "TV episode"
        state.preview?.bundles.orEmpty().filter {
            it.label.contains(search, ignoreCase = true) && (filter == "All" || it.detail == mediaType)
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        val horizontal = if (wide) 32.dp else 16.dp
        val contentWidth = if (wide) Modifier.fillMaxWidth(0.88f) else Modifier.fillMaxWidth()
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = horizontal, vertical = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(modifier = contentWidth, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.FolderSpecial, contentDescription = null,
                                modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Protected media export", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold)
                        }
                        Text("Move MovieBox downloads into your organized Movies and TV Shows library, with their subtitles.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    if (wide) {
                        Row(modifier = contentWidth, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccessCard(state, actions, Modifier.weight(1f))
                            RouteCard(Modifier.weight(1f))
                        }
                    } else {
                        Column(modifier = contentWidth, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AccessCard(state, actions)
                            RouteCard()
                        }
                    }
                }
                if (state.message != null || state.error != null) {
                    item {
                        StatusNotice(state.error ?: state.message.orEmpty(), error = state.error != null,
                            modifier = contentWidth, onDismiss = actions::clearMessage)
                    }
                }
                if (state.result != null) {
                    item { ResultCard(state.result, contentWidth) }
                }
                if (state.isWorking) {
                    item { ProgressCard(state.progress, actions::cancel, contentWidth) }
                }
                item {
                    OutlinedCard(modifier = contentWidth) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Export history", style = MaterialTheme.typography.titleMedium)
                            val active = state.history.flatMap { it.undoable }
                            Text("${state.history.size} sessions · ${active.size} undoable files · ${formatBytes(active.sumOf { it.size })}",
                                style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Hide history" else "View history") }
                                if (state.canUndo) TextButton(onClick = {
                                    state.history.firstOrNull { it.undoable.isNotEmpty() }?.let { session ->
                                        undoRequest = UndoRequest(session.id, session.undoable.map { it.id }.toSet(), "latest export")
                                    }
                                }, enabled = state.shizukuAuthorized && !state.isWorking) { Text("Undo last export") }
                            }
                            if (state.history.any { it.hasPending }) {
                                Text("Interrupted transfers need attention. Pending restores can be retried; existing files are not overwritten.", color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { confirmRecovery = true }, enabled = state.shizukuAuthorized && !state.isWorking) {
                                    Text("Recover interrupted transfers")
                                }
                            }
                        }
                    }
                }
                if (showHistory) {
                    if (state.history.isEmpty()) item { Text("No export history yet.", modifier = contentWidth) }
                    items(state.history, key = { "history-${it.id}" }) { session ->
                        HistoryCard(session, state.shizukuAuthorized && !state.isWorking,
                            onUndo = { undoRequest = it }, modifier = contentWidth)
                    }
                }
                if (!state.isWorking && state.shizukuAuthorized && state.preview == null) {
                    item {
                        OutlinedCard(modifier = contentWidth) {
                            Column(modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Discover downloaded media", style = MaterialTheme.typography.titleMedium)
                                }
                                Text("Scan the protected video and subtitle directories. Nothing is moved until you review and confirm.",
                                    style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = actions::scan) {
                                    Icon(Icons.Outlined.Search, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Scan protected folders")
                                }
                            }
                        }
                    }
                }
                if (state.preview != null) {
                    item {
                        Column(modifier = contentWidth, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Your media", style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = actions::scan, enabled = !state.isWorking) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Rescan")
                                }
                            }
                            Text("${state.preview.bundles.size} groups · ${state.preview.sourceCount} supported files · " +
                                "${state.preview.unmatched.size} unmatched files left untouched",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = actions::selectAll, label = { Text("Select eligible") }, enabled = !state.isWorking)
                                AssistChip(onClick = actions::clearSelection, label = { Text("Clear") }, enabled = !state.isWorking)
                            }
                            OutlinedTextField(
                                value = search, onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                label = { Text("Search detected titles") },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("All", "Movies", "TV shows").forEach { choice ->
                                    FilterChip(selected = filter == choice, onClick = { filter = choice },
                                        label = { Text(choice) })
                                }
                            }
                        }
                    }
                    items(bundles, key = { it.id }) { bundle ->
                        BundleCard(bundle, selected = bundle.id in state.selectedIds,
                            enabled = !state.isWorking, onSelected = { actions.setSelected(bundle.id, it) },
                            modifier = contentWidth)
                    }
                    if (bundles.isEmpty()) {
                        item {
                            Text("No media matches the current filter.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = contentWidth.padding(24.dp))
                        }
                    }
                    if (state.preview.unmatched.isNotEmpty()) {
                        item {
                            OutlinedCard(modifier = contentWidth) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Not included", style = MaterialTheme.typography.titleSmall)
                                    Text("${state.preview.unmatched.size} files could not be matched reliably. They will remain in the original folders.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            if (!state.isWorking && state.preview != null) {
                Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.preview != null) {
                            Column(Modifier.weight(1f)) {
                                Text("${state.selectedBundles.size} titles · ${state.selectedFiles} files",
                                    fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(formatBytes(state.selectedBytes), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { confirmExport = true }, enabled = state.canExport) {
                                Text("Export selected")
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
            title = { Text("Export selected media?") },
            text = { Text("${state.selectedFiles} video and subtitle files (${formatBytes(state.selectedBytes)}) will be moved where supported, or copied and verified. Transfer history records every original path so you can undo selected files later.") },
            confirmButton = {
                Button(onClick = { confirmExport = false; actions.export() }) { Text("Start export") }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("Review") } },
        )
    }
    undoRequest?.let { request ->
        val session = state.history.firstOrNull { it.id == request.sessionId }
        val reconstructed = session?.undoable.orEmpty().any { it.id in request.ids && it.method == LegacyMovieRecoveryMapping.METHOD }
        AlertDialog(
            onDismissRequest = { undoRequest = null },
            title = { Text("Undo ${request.label}?") },
            text = { Text("Restore ${request.ids.size} files to their ${if (reconstructed) "recorded recovery targets (not known original paths)" else "exact original paths"}. Existing original paths will not be overwritten. Undo moves the current file without checking metadata or comparing contents. Close MovieBox and continue only if these are the files you want to return.") },
            confirmButton = { Button(onClick = { undoRequest = null; actions.restore(request.sessionId, request.ids) },
                enabled = state.shizukuAuthorized && !state.isWorking) { Text("Restore files") } },
            dismissButton = { TextButton(onClick = { undoRequest = null }) { Text("Cancel") } },
        )
    }
    if (confirmRecovery) {
        AlertDialog(onDismissRequest = { confirmRecovery = false }, title = { Text("Recover interrupted transfers?") },
            text = { Text("For interrupted restores, recovery uses recorded path visibility only. If only the original is visible, it marks the restore complete without verifying contents. Review both locations before confirming. Earlier export recovery still uses its recorded verification; old restore partials are retained for manual review.") },
            confirmButton = { Button(onClick = { confirmRecovery = false; actions.recover() }, enabled = state.shizukuAuthorized && !state.isWorking) { Text("Recover") } },
            dismissButton = { TextButton(onClick = { confirmRecovery = false }) { Text("Cancel") } })
    }
}

private data class UndoRequest(val sessionId: String, val ids: Set<String>, val label: String)

@Composable
private fun HistoryCard(session: ExportSession, enabled: Boolean, onUndo: (UndoRequest) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val undoable = session.undoable
    val restored = session.records.count { it.status == ExportRecordStatus.RESTORED }
    val pending = session.records.count { it.status.pending }
    val date = if (session.startedAt == 0L) "Earlier exports (migrated)" else DateFormat.getDateTimeInstance().format(Date(session.startedAt))
    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${session.records.map { it.groupId }.distinct().size} groups · ${session.records.size} files · ${formatBytes(session.records.sumOf { it.size })}",
                style = MaterialTheme.typography.bodySmall)
            Text("${session.records.count { it.status == ExportRecordStatus.EXPORTED }} exported · $restored restored · $pending pending · ${session.records.count { it.status == ExportRecordStatus.NOT_MOVED }} not moved",
                style = MaterialTheme.typography.bodySmall)
            if (session.startedAt > 0 && session.finishedAt >= session.startedAt) {
                Text("Export duration: ${(session.finishedAt - session.startedAt) / 1000}s", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide files" else "View files") }
                if (undoable.isNotEmpty()) TextButton(onClick = { onUndo(UndoRequest(session.id, undoable.map { it.id }.toSet(), "session")) },
                    enabled = enabled) { Text("Undo session") }
            }
            if (expanded) {
                session.records.groupBy { it.groupId }.values.forEach { group ->
                    HorizontalDivider()
                    Text(group.first().groupLabel, style = MaterialTheme.typography.titleSmall)
                    val groupUndo = group.filter { it in undoable }
                    if (groupUndo.isNotEmpty()) TextButton(onClick = { onUndo(UndoRequest(session.id, groupUndo.map { it.id }.toSet(), "media group")) }, enabled = enabled) { Text("Undo media group") }
                    group.forEach { record ->
                        Text(record.destination.substringAfterLast('/'), fontWeight = FontWeight.Medium)
                        Text("${record.status.label} · ${formatBytes(record.size)} · ${record.method}", style = MaterialTheme.typography.bodySmall)
                        Text("${if (record.method == LegacyMovieRecoveryMapping.METHOD) "Recovery target" else "Original"}: ${record.source}", style = MaterialTheme.typography.bodySmall)
                        Text("Exported to: ${record.destination}", style = MaterialTheme.typography.bodySmall)
                        if (record.exportedAt > 0) Text("Exported: ${DateFormat.getDateTimeInstance().format(Date(record.exportedAt))}", style = MaterialTheme.typography.bodySmall)
                        if (record.restoredAt > 0) Text("Restored: ${DateFormat.getDateTimeInstance().format(Date(record.restoredAt))}", style = MaterialTheme.typography.bodySmall)
                        record.temporary?.let { Text("Temporary: $it", style = MaterialTheme.typography.bodySmall) }
                        record.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (record in undoable) TextButton(onClick = { onUndo(UndoRequest(session.id, setOf(record.id), "file")) }, enabled = enabled) { Text("Undo file") }
                    }
                }
                if (session.legacyTemporary.isNotEmpty()) {
                    Text("Retained partials; review manually:", color = MaterialTheme.colorScheme.error)
                    session.legacyTemporary.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun AccessCard(state: ProtectedExportUiState, actions: ProtectedExportViewModel, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (state.shizukuAuthorized) Icons.Outlined.CheckCircle else Icons.Outlined.LockOpen,
                    contentDescription = null, tint = if (state.shizukuAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Column {
                    Text("Shizuku connection", style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        state.shizukuAuthorized -> "Authorized · folder access checked during scan"
                        state.shizukuRunning -> "Connected · permission required"
                        else -> "Not running · enable wireless debugging"
                    }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!state.shizukuAuthorized) {
                Text("Start Shizuku in its app. On POCO HyperOS, wireless debugging must be enabled and Shizuku may need restarting after a reboot.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions::refreshAccess, enabled = !state.isWorking) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Check authorization")
                }
                if (state.shizukuRunning && !state.shizukuAuthorized) {
                    Button(onClick = actions::requestAccess, enabled = !state.isWorking) { Text("Grant access") }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Export route", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Shield, contentDescription = null, Modifier.size(20.dp))
                Column {
                    Text("MovieBox protected downloads", style = MaterialTheme.typography.bodyMedium)
                    Text("Videos + matching subtitle folders", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, Modifier.size(20.dp))
                Column {
                    Text("Shared device storage", style = MaterialTheme.typography.bodyMedium)
                    Text("/Movies/Title (Year)  ·  /TV Shows/Series/Season 01", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BundleCard(bundle: ExportBundle, selected: Boolean, enabled: Boolean,
                       onSelected: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var details by rememberSaveable(bundle.id) { mutableStateOf(false) }
    OutlinedCard(modifier = modifier, border = if (bundle.blocked)
        BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)) else CardDefaults.outlinedCardBorder()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Checkbox(checked = selected, onCheckedChange = onSelected, enabled = enabled && !bundle.blocked)
                Icon(if (bundle.detail == "Movie") Icons.Outlined.Movie else Icons.Outlined.Tv,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(bundle.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${bundle.detail} · ${bundle.operations.size} files · ${formatBytes(bundle.totalBytes)} · " +
                        "${bundle.subtitleCount} subtitles", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (bundle.blocked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(17.dp))
                    Text("Needs review. Export disabled to protect existing files.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (bundle.warnings.isNotEmpty()) {
                Text(bundle.warnings.first(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { details = !details }) {
                Text(if (details) "Hide file destinations" else "View file destinations")
            }
            if (details) {
                HorizontalDivider()
                bundle.operations.forEach { operation ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(if (operation.mediaType == com.kp.filemanager.domain.MediaFileType.SUBTITLE)
                            Icons.Outlined.Subtitles else Icons.Outlined.Movie, contentDescription = null,
                            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text(operation.originalName, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium)
                            Text("→ ${operation.destinationPath}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                bundle.warnings.drop(1).forEach { warning ->
                    Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(progress: ExportProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(progress.stage.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                progress.percent?.let { Text("$it%", fontWeight = FontWeight.Bold) }
            }
            if (progress.fraction != null) {
                LinearProgressIndicator(progress = { progress.fraction ?: 0f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(progress.message, style = MaterialTheme.typography.bodyMedium)
            if (progress.currentFile.isNotEmpty()) {
                Text("Current: ${progress.currentFile}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            if (progress.totalFiles > 0) {
                Text("${progress.completedFiles} / ${progress.totalFiles} files completed · " +
                    "${formatBytes(progress.completedBytes)} / ${formatBytes(progress.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (progress.stage == ExportStage.COPYING && progress.currentFileBytes > 0) {
                LinearProgressIndicator(progress = { (progress.currentCopiedBytes.toDouble() / progress.currentFileBytes).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("Current copy: ${formatBytes(progress.currentCopiedBytes)} / ${formatBytes(progress.currentFileBytes)}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Stop safely") }
        }
    }
}

@Composable
private fun ResultCard(outcome: ExportOutcome, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (outcome.succeeded) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                val restored = outcome.destinations.any(ProtectedExportPaths::inProtectedSource)
                Text(if (restored) "${outcome.exported} files restored" else "${outcome.exported} items processed", style = MaterialTheme.typography.titleMedium)
            }
            outcome.failedBundles.forEach { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error) }
            outcome.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (outcome.succeeded && outcome.destinations.isNotEmpty()) Text(
                if (outcome.destinations.any(ProtectedExportPaths::inProtectedSource)) "Files returned to the protected MovieBox folders."
                else "Files are available in the public Movies and TV Shows folders.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusNotice(message: String, error: Boolean, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = if (error)
        MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info,
                contentDescription = null, modifier = Modifier.size(19.dp))
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var amount = bytes.toDouble()
    var unit = -1
    while (amount >= 1024 && unit < units.lastIndex) { amount /= 1024; unit++ }
    return String.format(Locale.US, "%.1f %s", amount, units[unit])
}
