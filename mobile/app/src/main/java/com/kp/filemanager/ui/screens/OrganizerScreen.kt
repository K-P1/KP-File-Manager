/** mobile/app/src/main/java/com/kp/filemanager/ui/screens/OrganizerScreen.kt: Renders every organizer workflow state, review, conflicts, confirmation, results, and undo; created for the UI refactor. */
package com.kp.filemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kp.filemanager.domain.OperationPlan
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.OperationType
import com.kp.filemanager.domain.PlannedFileOperation
import com.kp.filemanager.ui.FileManagerActions
import com.kp.filemanager.ui.FileManagerUiState
import com.kp.filemanager.ui.OrganizerWorkflowState
import com.kp.filemanager.ui.ReviewFilter
import com.kp.filemanager.ui.ReviewCriteria
import com.kp.filemanager.ui.ReviewSort
import com.kp.filemanager.ui.confirmationSummary
import com.kp.filemanager.ui.components.EmptyState
import com.kp.filemanager.ui.components.FolderSelectorCard
import com.kp.filemanager.ui.components.MessageTone
import com.kp.filemanager.ui.components.NumberStepper
import com.kp.filemanager.ui.components.OperationListDivider
import com.kp.filemanager.ui.components.OperationRow
import com.kp.filemanager.ui.components.PageHeader
import com.kp.filemanager.ui.components.ProgressPanel
import com.kp.filemanager.ui.components.SectionHeader
import com.kp.filemanager.ui.components.StatCard
import com.kp.filemanager.ui.components.StatusMessage
import com.kp.filemanager.ui.components.StickyActionBar
import com.kp.filemanager.ui.operationsFor
import com.kp.filemanager.ui.reviewSummary
import com.kp.filemanager.ui.refinedBy
import com.kp.filemanager.ui.searchedAndSorted
import com.kp.filemanager.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerScreen(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    var showScanOptions by rememberSaveable { mutableStateOf(false) }
    var showCancelConfirmation by rememberSaveable { mutableStateOf(false) }
    var showApplyConfirmation by rememberSaveable { mutableStateOf(false) }
    var showUndoConfirmation by rememberSaveable { mutableStateOf(false) }
    var selectedOperationUri by rememberSaveable { mutableStateOf<String?>(null) }
    var conflictOperationUri by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedOperation = uiState.preview.firstOrNull { it.sourceUri == selectedOperationUri }
    val conflictOperation = uiState.preview.firstOrNull { it.sourceUri == conflictOperationUri }

    val bottomBar: @Composable () -> Unit = {
        when (uiState.workflowState) {
            OrganizerWorkflowState.IDLE,
            OrganizerWorkflowState.FOLDER_SELECTED,
            -> StickyActionBar(
                primaryLabel = "Scan folder",
                onPrimary = actions::preview,
                primaryEnabled = uiState.canPreview,
                supportingText = if (uiState.sourceUri == null) {
                    "Select a media folder to continue."
                } else {
                    "Scanning only reads files. You will review every proposed change."
                },
            )
            OrganizerWorkflowState.REVIEWING,
            OrganizerWorkflowState.RESOLVING_CONFLICTS,
            -> {
                val conflicts = uiState.activePlan?.reviewSummary()?.conflicts ?: 0
                StickyActionBar(
                    primaryLabel = if (conflicts > 0) {
                        "Resolve $conflicts ${if (conflicts == 1) "conflict" else "conflicts"}"
                    } else {
                        "Apply ${uiState.activePlan?.readyCount ?: 0} changes"
                    },
                    onPrimary = {
                        if (conflicts > 0) {
                            conflictOperationUri = uiState.preview
                                .firstOrNull { it.status == OperationStatus.CONFLICT }
                                ?.sourceUri
                        } else {
                            showApplyConfirmation = true
                        }
                    },
                    primaryEnabled = conflicts > 0 || uiState.canApply,
                    secondaryLabel = "Rescan",
                    onSecondary = actions::preview,
                    supportingText = if (conflicts > 0) {
                        "Resolve or safely skip every blocking conflict before applying."
                    } else {
                        "No existing files will be overwritten."
                    },
                )
            }
            OrganizerWorkflowState.APPLY_COMPLETE,
            OrganizerWorkflowState.APPLY_PARTIAL_FAILURE,
            -> StickyActionBar(
                primaryLabel = "Done",
                onPrimary = actions::done,
                secondaryLabel = "Undo changes".takeIf { uiState.canUndo },
                onSecondary = { showUndoConfirmation = true }.takeIf { uiState.canUndo },
            )
            OrganizerWorkflowState.UNDO_COMPLETE -> StickyActionBar(
                primaryLabel = "Done",
                onPrimary = actions::done,
            )
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when (uiState.workflowState) {
            OrganizerWorkflowState.SCANNING -> ScanningContent(
                uiState = uiState,
                onCancel = { showCancelConfirmation = true },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            OrganizerWorkflowState.SCAN_FAILED -> ScanFailedContent(
                uiState = uiState,
                onPickSource = onPickSource,
                onRetry = actions::preview,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            OrganizerWorkflowState.SCAN_COMPLETE -> NoMediaContent(
                onPickSource = onPickSource,
                onOptions = { showScanOptions = true },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            OrganizerWorkflowState.REVIEWING,
            OrganizerWorkflowState.RESOLVING_CONFLICTS,
            -> ReviewContent(
                plan = uiState.activePlan,
                isExpanded = isExpanded,
                onOperationClick = { operation ->
                    if (operation.status == OperationStatus.CONFLICT) {
                        conflictOperationUri = operation.sourceUri
                    } else {
                        selectedOperationUri = operation.sourceUri
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            OrganizerWorkflowState.APPLYING -> ApplyingContent(
                uiState = uiState,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            OrganizerWorkflowState.APPLY_COMPLETE,
            OrganizerWorkflowState.APPLY_PARTIAL_FAILURE,
            -> ResultsContent(
                uiState = uiState,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            OrganizerWorkflowState.UNDOING -> ProgressPanel(
                title = "Undoing changes",
                supportingText = "Restoring files to their original names and locations.",
                progressLabel = "Please keep KP File Manager open",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(MaterialTheme.spacing.section),
            )
            OrganizerWorkflowState.UNDO_COMPLETE -> UndoCompleteContent(
                uiState = uiState,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> OrganizerSetupContent(
                uiState = uiState,
                actions = actions,
                onPickSource = onPickSource,
                onPickDestination = onPickDestination,
                onScanOptions = { showScanOptions = true },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (showScanOptions) {
        ScanOptionsSheet(
            uiState = uiState,
            actions = actions,
            onPickDestination = onPickDestination,
            onDismiss = { showScanOptions = false },
        )
    }
    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text("Cancel scan?") },
            text = {
                Text("Files have not been changed. The current scan results will be discarded.")
            },
            confirmButton = {
                Button(onClick = { showCancelConfirmation = false }) { Text("Keep scanning") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmation = false
                        actions.cancelScan()
                    },
                ) {
                    Text("Cancel scan", color = MaterialTheme.colorScheme.error)
                }
            },
        )
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
    if (showUndoConfirmation) {
        AlertDialog(
            onDismissRequest = { showUndoConfirmation = false },
            icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
            title = { Text("Undo organization?") },
            text = {
                Text("Successfully organized files will return to their original names and locations.")
            },
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
    if (selectedOperation != null) {
        OperationDetailsSheet(
            operation = selectedOperation,
            onDismiss = { selectedOperationUri = null },
        )
    }
    if (conflictOperation != null) {
        ConflictResolutionDialog(
            operation = conflictOperation,
            onDismiss = { conflictOperationUri = null },
            onSkip = {
                actions.skipConflict(conflictOperation.sourceUri)
                conflictOperationUri = null
            },
            onRename = { newName ->
                actions.renameConflict(conflictOperation.sourceUri, newName)
                conflictOperationUri = null
            },
        )
    }
}

@Composable
private fun OrganizerSetupContent(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    onScanOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(MaterialTheme.spacing.standard),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        item {
            PageHeader(
                title = "Organize Media",
                supportingText = "Select a folder, scan your media, review the plan, and apply changes safely.",
            )
        }
        if (uiState.statusTitle != null) {
            item {
                StatusMessage(
                    title = uiState.statusTitle,
                    message = uiState.statusMessage.orEmpty(),
                    tone = MessageTone.INFO,
                    details = uiState.failures,
                    onDismiss = actions::dismissStatus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            FolderSelectorCard(
                title = "Selected folder",
                folderName = uiState.sourceName,
                folderPath = uiState.sourceUri,
                onSelect = onPickSource,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ScanOptionsCard(
                uiState = uiState,
                onOpen = onScanOptions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (uiState.destinationUri != null) {
            item {
                FolderSelectorCard(
                    title = "Organization destination",
                    folderName = uiState.destinationName,
                    folderPath = uiState.destinationUri,
                    onSelect = onPickDestination,
                    onClear = actions::clearDestination,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(MaterialTheme.spacing.standard)) }
    }
}

@Composable
private fun ScanOptionsCard(
    uiState: FileManagerUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier, onClick = onOpen) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.standard),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text("Scan options", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${uiState.maximumScanDepth} levels • ${uiState.movieFolderName} • ${uiState.tvFolderName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ScanningContent(
    uiState: FileManagerUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(MaterialTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        ProgressPanel(
            title = "Scanning Media",
            supportingText = "Looking for movies, TV episodes, subtitles, artwork, and metadata.",
            progressLabel = "${uiState.scanProgress.filesScanned} files scanned",
            currentLabel = "Currently scanning",
            currentValue = uiState.scanProgress.currentPath,
            onCancel = onCancel,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        ) {
            StatCard(
                label = "Movies",
                value = uiState.scanProgress.moviesDetected.toString(),
                icon = Icons.Outlined.Movie,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Episodes",
                value = uiState.scanProgress.episodesDetected.toString(),
                icon = Icons.Outlined.Tv,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Subtitles",
                value = uiState.scanProgress.subtitlesDetected.toString(),
                icon = Icons.Outlined.Subtitles,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScanFailedContent(
    uiState: FileManagerUiState,
    onPickSource: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(MaterialTheme.spacing.section), contentAlignment = Alignment.Center) {
        EmptyState(
            title = "Unable to scan folder",
            message = uiState.statusMessage
                ?: "KP File Manager could not access this location. Check folder access or choose another folder.",
            icon = Icons.Outlined.ErrorOutline,
            primaryLabel = "Choose another folder",
            onPrimary = onPickSource,
            secondaryLabel = "Try again",
            onSecondary = onRetry,
            modifier = Modifier.widthIn(max = 560.dp),
        )
    }
}

@Composable
private fun NoMediaContent(
    onPickSource: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(MaterialTheme.spacing.section), contentAlignment = Alignment.Center) {
        EmptyState(
            title = "No movies or TV episodes found",
            message = "Try selecting a different folder or increasing the maximum scan depth.",
            icon = Icons.Outlined.FolderOpen,
            primaryLabel = "Change folder",
            onPrimary = onPickSource,
            secondaryLabel = "Scan options",
            onSecondary = onOptions,
            modifier = Modifier.widthIn(max = 560.dp),
        )
    }
}

@Composable
private fun ReviewContent(
    plan: OperationPlan?,
    isExpanded: Boolean,
    onOperationClick: (PlannedFileOperation) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (plan == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                title = "No review available",
                message = "Scan the selected folder again to rebuild the proposed changes.",
                icon = Icons.Outlined.Refresh,
            )
        }
        return
    }
    var selectedFilterName by rememberSaveable { mutableStateOf(ReviewFilter.ALL.name) }
    var reviewQuery by rememberSaveable { mutableStateOf("") }
    var reviewSortName by rememberSaveable { mutableStateOf(ReviewSort.TITLE.name) }
    var operationTypeFilter by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("") }
    var lowConfidenceOnly by rememberSaveable { mutableStateOf(false) }
    var showAdvancedFilters by rememberSaveable { mutableStateOf(false) }
    val selectedFilter = ReviewFilter.entries.first { it.name == selectedFilterName }
    val reviewSort = ReviewSort.entries.first { it.name == reviewSortName }
    val reviewCriteria = ReviewCriteria(
        operationTypes = operationTypeFilter
            .split(',')
            .mapNotNull { name -> OperationType.entries.firstOrNull { it.name == name } }
            .toSet(),
        statuses = statusFilter
            .split(',')
            .mapNotNull { name -> OperationStatus.entries.firstOrNull { it.name == name } }
            .toSet(),
        lowConfidenceOnly = lowConfidenceOnly,
    )
    val operations = plan.operationsFor(selectedFilter)
        .refinedBy(reviewCriteria)
        .searchedAndSorted(reviewQuery, reviewSort)
    val summary = plan.reviewSummary()

    if (isExpanded) {
        Row(modifier = modifier) {
            ReviewOverview(
                plan = plan,
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilterName = it.name },
                query = reviewQuery,
                onQueryChange = { reviewQuery = it },
                sort = reviewSort,
                onSortChange = { reviewSortName = it.name },
                criteria = reviewCriteria,
                onFilterClick = { showAdvancedFilters = true },
                modifier = Modifier.fillMaxHeight().weight(0.42f),
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
            OperationList(
                title = selectedFilter.label,
                operations = operations,
                onOperationClick = onOperationClick,
                modifier = Modifier.fillMaxHeight().weight(0.58f),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(MaterialTheme.spacing.standard),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
        ) {
            item {
                PageHeader(
                    title = "Review Changes",
                    supportingText = "${summary.ready} ready • ${summary.conflicts} need attention • ${summary.unchanged} unchanged",
                )
            }
            if (plan.scanWarnings.isNotEmpty()) {
                item {
                    StatusMessage(
                        title = "Scan completed with warnings",
                        message = "${plan.scanWarnings.size} folders could not be accessed. The rest of the library was scanned normally.",
                        tone = MessageTone.WARNING,
                        details = plan.scanWarnings.map { "${it.path}: ${it.reason}" },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item { ReviewStats(summary = summary) }
            item {
                ReviewFilterRow(
                    selectedFilter = selectedFilter,
                    counts = mapOf(
                        ReviewFilter.ALL to plan.operations.size,
                        ReviewFilter.MOVIES to plan.operationsFor(ReviewFilter.MOVIES).size,
                        ReviewFilter.TV_SHOWS to plan.operationsFor(ReviewFilter.TV_SHOWS).size,
                        ReviewFilter.CONFLICTS to summary.conflicts,
                        ReviewFilter.SKIPPED to summary.skipped + summary.unchanged,
                    ),
                    onFilterSelected = { selectedFilterName = it.name },
                )
            }
            item {
                ReviewControls(
                    query = reviewQuery,
                    onQueryChange = { reviewQuery = it },
                    sort = reviewSort,
                    onSortChange = { reviewSortName = it.name },
                    criteria = reviewCriteria,
                    onFilterClick = { showAdvancedFilters = true },
                )
            }
            if (operations.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing here",
                        message = "This review category has no items.",
                        icon = Icons.Outlined.CheckCircle,
                        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.large),
                    )
                }
            } else {
                items(operations, key = PlannedFileOperation::sourceUri) { operation ->
                    OperationRow(
                        operation = operation,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOperationClick(operation) },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(MaterialTheme.spacing.standard)) }
        }
    }
    if (showAdvancedFilters) {
        ReviewFilterSheet(
            criteria = reviewCriteria,
            onDismiss = { showAdvancedFilters = false },
            onApply = { criteria ->
                operationTypeFilter = criteria.operationTypes.joinToString(",") { it.name }
                statusFilter = criteria.statuses.joinToString(",") { it.name }
                lowConfidenceOnly = criteria.lowConfidenceOnly
                showAdvancedFilters = false
            },
        )
    }
}

@Composable
private fun ReviewOverview(
    plan: OperationPlan,
    selectedFilter: ReviewFilter,
    onFilterSelected: (ReviewFilter) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: ReviewSort,
    onSortChange: (ReviewSort) -> Unit,
    criteria: ReviewCriteria,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = plan.reviewSummary()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(MaterialTheme.spacing.section),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        item {
            PageHeader(
                title = "Review Changes",
                supportingText = "${summary.detected} files detected • ${summary.ready} changes planned",
            )
        }
        item { ReviewStats(summary = summary) }
        item {
            SectionHeader(
                title = "Review categories",
                supportingText = "Choose a category to inspect the proposed file operations.",
            )
        }
        item {
            ReviewFilterRow(
                selectedFilter = selectedFilter,
                counts = mapOf(
                    ReviewFilter.ALL to plan.operations.size,
                    ReviewFilter.MOVIES to plan.operationsFor(ReviewFilter.MOVIES).size,
                    ReviewFilter.TV_SHOWS to plan.operationsFor(ReviewFilter.TV_SHOWS).size,
                    ReviewFilter.CONFLICTS to summary.conflicts,
                    ReviewFilter.SKIPPED to summary.skipped + summary.unchanged,
                ),
                onFilterSelected = onFilterSelected,
            )
        }
        item {
            ReviewControls(
                query = query,
                onQueryChange = onQueryChange,
                sort = sort,
                onSortChange = onSortChange,
                criteria = criteria,
                onFilterClick = onFilterClick,
            )
        }
        if (plan.scanWarnings.isNotEmpty()) {
            item {
                StatusMessage(
                    title = "Scan warnings",
                    message = "${plan.scanWarnings.size} folders could not be accessed.",
                    tone = MessageTone.WARNING,
                    details = plan.scanWarnings.map { "${it.path}: ${it.reason}" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReviewStats(
    summary: com.kp.filemanager.ui.ReviewSummary,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        ) {
            StatCard("Ready", summary.ready.toString(), Icons.Outlined.CheckCircle, Modifier.weight(1f), MessageTone.SUCCESS)
            StatCard("Conflicts", summary.conflicts.toString(), Icons.Outlined.WarningAmber, Modifier.weight(1f), MessageTone.WARNING)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        ) {
            StatCard("Skipped", summary.skipped.toString(), Icons.Outlined.Refresh, Modifier.weight(1f))
            StatCard("Unchanged", summary.unchanged.toString(), Icons.Outlined.CheckCircle, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReviewFilterRow(
    selectedFilter: ReviewFilter,
    counts: Map<ReviewFilter, Int>,
    onFilterSelected: (ReviewFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        contentPadding = PaddingValues(end = MaterialTheme.spacing.standard),
    ) {
        items(ReviewFilter.entries, key = ReviewFilter::name) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text("${filter.label} ${counts[filter] ?: 0}") },
            )
        }
    }
}

@Composable
private fun ReviewControls(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: ReviewSort,
    onSortChange: (ReviewSort) -> Unit,
    criteria: ReviewCriteria,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            label = { Text("Search review") },
            singleLine = true,
        )
        Box {
            OutlinedButton(onClick = { showSortMenu = true }) {
                Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null)
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.tight))
                Text(sort.label)
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                ReviewSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSortChange(option)
                            showSortMenu = false
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = onFilterClick) {
            Icon(Icons.Outlined.FilterList, contentDescription = null)
            if (criteria.isActive) {
                Spacer(modifier = Modifier.width(MaterialTheme.spacing.tight))
                Text("Filtered")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewFilterSheet(
    criteria: ReviewCriteria,
    onDismiss: () -> Unit,
    onApply: (ReviewCriteria) -> Unit,
) {
    var operationTypes by remember(criteria) { mutableStateOf(criteria.operationTypes) }
    var statuses by remember(criteria) { mutableStateOf(criteria.statuses) }
    var lowConfidenceOnly by remember(criteria) { mutableStateOf(criteria.lowConfidenceOnly) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.section)
                .padding(bottom = MaterialTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
        ) {
            SectionHeader(
                title = "Filter Results",
                supportingText = "Leave a section unselected to include every value.",
            )
            FilterSection("Operations") {
                OperationType.entries.forEach { type ->
                    FilterCheckboxRow(
                        label = type.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase),
                        checked = type in operationTypes,
                        onCheckedChange = { checked ->
                            operationTypes = if (checked) operationTypes + type else operationTypes - type
                        },
                    )
                }
            }
            FilterSection("Status") {
                listOf(
                    OperationStatus.READY,
                    OperationStatus.CONFLICT,
                    OperationStatus.SKIPPED,
                    OperationStatus.UNCHANGED,
                ).forEach { status ->
                    FilterCheckboxRow(
                        label = status.name.lowercase().replaceFirstChar(Char::uppercase),
                        checked = status in statuses,
                        onCheckedChange = { checked ->
                            statuses = if (checked) statuses + status else statuses - status
                        },
                    )
                }
            }
            FilterCheckboxRow(
                label = "Low confidence only",
                checked = lowConfidenceOnly,
                onCheckedChange = { lowConfidenceOnly = it },
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
            ) {
                TextButton(
                    onClick = {
                        operationTypes = emptySet()
                        statuses = emptySet()
                        lowConfidenceOnly = false
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = {
                        onApply(
                            ReviewCriteria(
                                operationTypes = operationTypes,
                                statuses = statuses,
                                lowConfidenceOnly = lowConfidenceOnly,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Apply filters")
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.micro)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        content()
    }
}

@Composable
private fun FilterCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = MaterialTheme.spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun OperationList(
    title: String,
    operations: List<PlannedFileOperation>,
    onOperationClick: (PlannedFileOperation) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(MaterialTheme.spacing.section),
    ) {
        item {
            Text(
                text = title,
                modifier = Modifier
                    .semantics { heading() }
                    .padding(bottom = MaterialTheme.spacing.standard),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (operations.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing here",
                    message = "This review category has no items.",
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.large),
                )
            }
        } else {
            items(operations, key = PlannedFileOperation::sourceUri) { operation ->
                OperationRow(
                    operation = operation,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOperationClick(operation) },
                )
                OperationListDivider()
            }
        }
    }
}

@Composable
private fun ApplyingContent(
    uiState: FileManagerUiState,
    modifier: Modifier = Modifier,
) {
    ProgressPanel(
        title = "Organizing Library",
        supportingText = "Applying the approved plan without overwriting existing files.",
        progressLabel = "Processing ${uiState.activePlan?.readyCount ?: 0} changes",
        currentLabel = "Important",
        currentValue = "Please keep KP File Manager open until the operation completes.",
        modifier = modifier.padding(MaterialTheme.spacing.section),
    )
}

@Composable
private fun ResultsContent(
    uiState: FileManagerUiState,
    modifier: Modifier = Modifier,
) {
    val plan = uiState.activePlan
    val completed = plan?.operations?.count { it.status == OperationStatus.COMPLETED } ?: 0
    val failed = plan?.operations?.count { it.status == OperationStatus.FAILED } ?: uiState.failures.size
    val skipped = plan?.operations?.count {
        it.status in setOf(OperationStatus.SKIPPED, OperationStatus.UNCHANGED)
    } ?: 0
    val partial = uiState.workflowState == OrganizerWorkflowState.APPLY_PARTIAL_FAILURE
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(MaterialTheme.spacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        item {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (partial) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (partial) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (partial) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
        item {
            PageHeader(
                title = if (partial) "Completed with Issues" else "Library Organized",
                supportingText = uiState.statusMessage.orEmpty(),
                modifier = Modifier.widthIn(max = 640.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
            ) {
                StatCard("Completed", completed.toString(), Icons.Outlined.CheckCircle, Modifier.weight(1f), MessageTone.SUCCESS)
                StatCard("Skipped", skipped.toString(), Icons.Outlined.Refresh, Modifier.weight(1f))
                StatCard("Failed", failed.toString(), Icons.Outlined.WarningAmber, Modifier.weight(1f), if (failed > 0) MessageTone.ERROR else MessageTone.INFO)
            }
        }
        if (uiState.failures.isNotEmpty()) {
            item {
                StatusMessage(
                    title = "Failed operations",
                    message = "${uiState.failures.size} files could not be changed. Successful work was kept.",
                    tone = MessageTone.ERROR,
                    details = uiState.failures,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
                )
            }
        }
    }
}

@Composable
private fun UndoCompleteContent(
    uiState: FileManagerUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(MaterialTheme.spacing.section), contentAlignment = Alignment.Center) {
        EmptyState(
            title = uiState.statusTitle ?: "Changes Undone",
            message = uiState.statusMessage
                ?: "Files were restored to their original names and locations.",
            icon = if (uiState.failures.isEmpty()) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            modifier = Modifier.widthIn(max = 560.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanOptionsSheet(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    onPickDestination: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.section)
                .padding(bottom = MaterialTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
        ) {
            SectionHeader(
                title = "Scan Options",
                supportingText = "Defaults are suitable for most media folders.",
            )
            NumberStepper(
                label = "Maximum scan depth",
                value = uiState.maximumScanDepth,
                onValueChange = actions::setMaximumScanDepth,
                valueSuffix = " levels",
            )
            OutlinedTextField(
                value = uiState.movieFolderName,
                onValueChange = actions::setMovieFolderName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Movie destination") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.tvFolderName,
                onValueChange = actions::setTvFolderName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TV destination") },
                singleLine = true,
            )
            FolderSelectorCard(
                title = "Organization destination",
                folderName = uiState.destinationName,
                folderPath = uiState.destinationUri,
                onSelect = onPickDestination,
                onClear = (actions::clearDestination).takeIf { uiState.destinationUri != null },
                emptyMessage = "Same as selected media folder",
                selectLabel = "Choose another folder",
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = {
                    actions.setMaximumScanDepth(4)
                    actions.setMovieFolderName("Movies")
                    actions.setTvFolderName("TV Shows")
                    actions.clearDestination()
                },
            ) {
                Text("Reset defaults")
            }
            HorizontalDivider()
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
internal fun ApplyConfirmationDialog(
    plan: OperationPlan?,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val summary = plan?.confirmationSummary() ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
        title = { Text("Apply ${summary.changes} changes?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight)) {
                Text("KP File Manager will:")
                Text("• Move ${summary.moves} files")
                Text("• Rename ${summary.renames} files")
                Text("• Leave ${summary.unchanged} files unchanged")
                Text("• Skip ${summary.skipped} files")
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.micro))
                Text("No existing files will be overwritten.")
                Text("You can undo this operation until the app is closed.")
            }
        },
        confirmButton = {
            Button(onClick = onApply) { Text("Apply ${summary.changes} changes") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun ConflictResolutionDialog(
    operation: PlannedFileOperation,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onRename: (String) -> Unit,
) {
    var alternativeName by remember(operation.sourceUri) { mutableStateOf(operation.newName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
        title = { Text("Resolve Conflict") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
            ) {
                Text(
                    text = operation.conflicts.joinToString("\n") { it.message },
                    style = MaterialTheme.typography.bodyMedium,
                )
                DetailValue("Proposed file", operation.originalName)
                DetailValue("Destination", operation.destinationPath)
                OutlinedTextField(
                    value = alternativeName,
                    onValueChange = { alternativeName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Alternative filename") },
                    supportingText = { Text("Choose a unique filename or safely skip this file.") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRename(alternativeName) },
                enabled = alternativeName.isNotBlank() && alternativeName != operation.newName,
            ) {
                Text("Save rename")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onSkip) { Text("Skip file") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationDetailsSheet(
    operation: PlannedFileOperation,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.section)
                .padding(bottom = MaterialTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
        ) {
            SectionHeader(
                title = operation.originalName,
                supportingText = operation.reason,
            )
            DetailValue("Original", operation.sourcePath)
            DetailValue("New location", operation.destinationPath)
            DetailValue("Operation", operation.operationType.name.replace('_', ' ').lowercase())
            DetailValue("Status", operation.status.name.lowercase())
            if (operation.warnings.isNotEmpty()) {
                StatusMessage(
                    title = "Review note",
                    message = operation.warnings.joinToString("\n"),
                    tone = MessageTone.WARNING,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun DetailValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.micro)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
