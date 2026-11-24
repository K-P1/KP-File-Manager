/** mobile/app/src/main/java/com/kp/filemanager/ui/screens/FileToolScreens.kt: Renders the Batch Rename and Find & Replace forms with inline previews and safe confirmations; created for the UI refactor. */
package com.kp.filemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.PlannedFileOperation
import com.kp.filemanager.ui.FileManagerActions
import com.kp.filemanager.ui.FileManagerUiState
import com.kp.filemanager.ui.OrganizerWorkflowState
import com.kp.filemanager.ui.components.EmptyState
import com.kp.filemanager.ui.components.FolderSelectorCard
import com.kp.filemanager.ui.components.MessageTone
import com.kp.filemanager.ui.components.NumberStepper
import com.kp.filemanager.ui.components.OperationRow
import com.kp.filemanager.ui.components.PageHeader
import com.kp.filemanager.ui.components.ProgressPanel
import com.kp.filemanager.ui.components.SectionHeader
import com.kp.filemanager.ui.components.StatusMessage
import com.kp.filemanager.ui.components.StickyActionBar
import com.kp.filemanager.ui.theme.spacing

@Composable
fun BatchRenameScreen(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FileToolScreen(
        uiState = uiState,
        actions = actions,
        mode = OperationMode.BATCH_RENAME,
        title = "Batch Rename",
        supportingText = "Build a numbered filename pattern and review every generated name.",
        icon = Icons.Outlined.Edit,
        onPickSource = onPickSource,
        modifier = modifier,
    ) {
        BatchRenameFields(uiState, actions)
    }
}

@Composable
fun FindAndReplaceScreen(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FileToolScreen(
        uiState = uiState,
        actions = actions,
        mode = OperationMode.FIND_AND_REPLACE,
        title = "Find & Replace",
        supportingText = "Replace or remove matching text in filenames after reviewing the preview.",
        icon = Icons.Outlined.FindReplace,
        onPickSource = onPickSource,
        modifier = modifier,
    ) {
        FindAndReplaceFields(uiState, actions)
    }
}

@Composable
private fun FileToolScreen(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    mode: OperationMode,
    title: String,
    supportingText: String,
    icon: ImageVector,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier,
    fields: @Composable () -> Unit,
) {
    var showApplyConfirmation by rememberSaveable { mutableStateOf(false) }
    var showUndoConfirmation by rememberSaveable { mutableStateOf(false) }
    var conflictUri by rememberSaveable { mutableStateOf<String?>(null) }
    val conflict = uiState.preview.firstOrNull { it.sourceUri == conflictUri }
    val previewMatchesMode = uiState.mode == mode && uiState.activePlan != null
    val conflicts = uiState.preview.count { it.status == OperationStatus.CONFLICT }
    val isResult = uiState.workflowState in setOf(
        OrganizerWorkflowState.APPLY_COMPLETE,
        OrganizerWorkflowState.APPLY_PARTIAL_FAILURE,
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (!uiState.isWorking) {
                when {
                    isResult -> StickyActionBar(
                        primaryLabel = "Done",
                        onPrimary = actions::done,
                        secondaryLabel = "Undo changes".takeIf { uiState.canUndo },
                        onSecondary = { showUndoConfirmation = true }.takeIf { uiState.canUndo },
                    )
                    conflicts > 0 -> StickyActionBar(
                        primaryLabel = "Resolve $conflicts ${if (conflicts == 1) "conflict" else "conflicts"}",
                        onPrimary = {
                            conflictUri = uiState.preview
                                .firstOrNull { it.status == OperationStatus.CONFLICT }
                                ?.sourceUri
                        },
                        supportingText = "No files will be overwritten.",
                    )
                    previewMatchesMode && uiState.canApply -> StickyActionBar(
                        primaryLabel = actionLabel(mode, uiState.activePlan?.readyCount ?: 0),
                        onPrimary = { showApplyConfirmation = true },
                        secondaryLabel = "Refresh preview",
                        onSecondary = actions::preview,
                    )
                    else -> StickyActionBar(
                        primaryLabel = "Preview files",
                        onPrimary = actions::preview,
                        primaryEnabled = uiState.canPreview,
                        supportingText = if (uiState.sourceUri == null) {
                            "Select a folder to continue."
                        } else {
                            "Previewing scans only the selected folder and does not change files."
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (uiState.isWorking) {
            ProgressPanel(
                title = if (uiState.workflowState == OrganizerWorkflowState.APPLYING) {
                    "Applying filename changes"
                } else {
                    "Building preview"
                },
                supportingText = "Checking filenames and destinations without overwriting existing files.",
                progressLabel = uiState.progressText.ifBlank { "Working..." },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(MaterialTheme.spacing.section),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                contentPadding = PaddingValues(MaterialTheme.spacing.standard),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
            ) {
                item {
                    PageHeader(title = title, supportingText = supportingText)
                }
                item {
                    FolderSelectorCard(
                        title = "Folder",
                        folderName = uiState.sourceName,
                        folderPath = uiState.sourceUri,
                        onSelect = onPickSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(MaterialTheme.spacing.standard),
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
                        ) {
                            SectionHeader(
                                title = if (mode == OperationMode.BATCH_RENAME) "Naming pattern" else "Replacement",
                            )
                            fields()
                        }
                    }
                }
                if (isResult) {
                    item {
                        StatusMessage(
                            title = uiState.statusTitle.orEmpty(),
                            message = uiState.statusMessage.orEmpty(),
                            tone = if (uiState.failures.isEmpty()) MessageTone.SUCCESS else MessageTone.WARNING,
                            details = uiState.failures,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    item {
                        SectionHeader(
                            title = "Preview",
                            supportingText = if (previewMatchesMode) {
                                "${uiState.preview.size} matching files"
                            } else {
                                "Previewed filenames will appear here before any changes are applied."
                            },
                        )
                    }
                    if (!previewMatchesMode || uiState.preview.isEmpty()) {
                        item {
                            EmptyState(
                                title = if (previewMatchesMode) "No matching files" else "Preview not built yet",
                                message = if (previewMatchesMode) {
                                    "Adjust the file type or filename pattern and refresh the preview."
                                } else {
                                    "Select Preview files to scan this folder safely."
                                },
                                icon = icon,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = MaterialTheme.spacing.section),
                            )
                        }
                    } else {
                        items(
                            items = uiState.preview.take(30),
                            key = PlannedFileOperation::sourceUri,
                        ) { operation ->
                            OperationRow(
                                operation = operation,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = if (operation.status == OperationStatus.CONFLICT) {
                                    { conflictUri = operation.sourceUri }
                                } else {
                                    null
                                },
                            )
                        }
                        if (uiState.preview.size > 30) {
                            item {
                                Text(
                                    text = "Showing 30 of ${uiState.preview.size} files",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(MaterialTheme.spacing.standard)) }
            }
        }
    }

    if (showApplyConfirmation) {
        ApplyConfirmationDialog(
            plan = uiState.activePlan,
            onDismiss = { showApplyConfirmation = false },
            onApply = {
                showApplyConfirmation = false
                actions.run()
            },
        )
    }
    if (conflict != null) {
        ConflictResolutionDialog(
            operation = conflict,
            onDismiss = { conflictUri = null },
            onSkip = {
                actions.skipConflict(conflict.sourceUri)
                conflictUri = null
            },
            onRename = { name ->
                actions.renameConflict(conflict.sourceUri, name)
                conflictUri = null
            },
        )
    }
    if (showUndoConfirmation) {
        AlertDialog(
            onDismissRequest = { showUndoConfirmation = false },
            icon = { androidx.compose.material3.Icon(Icons.Outlined.Refresh, contentDescription = null) },
            title = { Text("Undo filename changes?") },
            text = { Text("Successfully changed files will return to their previous names.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUndoConfirmation = false
                        actions.undo()
                    },
                ) {
                    Text("Undo changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUndoConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BatchRenameFields(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
) {
    OutlinedTextField(
        value = uiState.prefix,
        onValueChange = actions::setPrefix,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Prefix") },
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
    ) {
        NumberStepper(
            label = "Starting number",
            value = uiState.startIndex,
            onValueChange = actions::setStartIndex,
            modifier = Modifier.weight(1f),
        )
        NumberStepper(
            label = "Digit padding",
            value = uiState.zeroPadding,
            onValueChange = actions::setZeroPadding,
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        value = uiState.suffix,
        onValueChange = actions::setSuffix,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Suffix") },
        singleLine = true,
    )
    OutlinedTextField(
        value = uiState.extensionFilter,
        onValueChange = actions::setExtensionFilter,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Files to include") },
        supportingText = { Text("Use * for all files or enter an extension such as mkv.") },
        singleLine = true,
    )
    OutlinedTextField(
        value = uiState.replacementExtension,
        onValueChange = actions::setReplacementExtension,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("New extension") },
        supportingText = { Text("Leave blank to keep each original extension.") },
        singleLine = true,
    )
}

@Composable
private fun FindAndReplaceFields(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
) {
    OutlinedTextField(
        value = uiState.keyword,
        onValueChange = actions::setKeyword,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Find") },
        supportingText = if (uiState.keyword.isBlank()) {
            { Text("Enter text to find in filenames.") }
        } else {
            null
        },
        isError = uiState.sourceUri != null && uiState.keyword.isBlank(),
        singleLine = true,
    )
    OutlinedTextField(
        value = uiState.replacement,
        onValueChange = actions::setReplacement,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Replace with") },
        supportingText = { Text("Leave blank to remove the matching text.") },
        singleLine = true,
    )
    OutlinedTextField(
        value = uiState.extensionFilter,
        onValueChange = actions::setExtensionFilter,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("File types") },
        supportingText = { Text("Use * for all files or enter an extension such as mkv.") },
        singleLine = true,
    )
}

private fun actionLabel(mode: OperationMode, count: Int): String = when (mode) {
    OperationMode.BATCH_RENAME -> "Rename $count ${if (count == 1) "file" else "files"}"
    OperationMode.FIND_AND_REPLACE -> "Apply to $count ${if (count == 1) "file" else "files"}"
    OperationMode.ORGANIZE_MEDIA -> "Apply $count changes"
}
