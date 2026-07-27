/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerScreen.kt: Hosts the adaptive app shell, navigation, storage explanation, and destination screens; rebuilt for the UI refactor. */
package com.kp.filemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.ui.screens.BatchRenameScreen
import com.kp.filemanager.ui.screens.FindAndReplaceScreen
import com.kp.filemanager.ui.screens.OrganizerScreen
import com.kp.filemanager.ui.screens.SettingsScreen
import com.kp.filemanager.ui.theme.ThemePreference
import com.kp.filemanager.ui.theme.spacing

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FileManagerContent(
        uiState = uiState,
        actions = viewModel,
        onPickSource = onPickSource,
        onPickDestination = onPickDestination,
        themePreference = themePreference,
        onThemePreferenceChange = onThemePreferenceChange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileManagerContent(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.ORGANIZE.name) }
    var showFolderAccessExplanation by rememberSaveable { mutableStateOf(false) }
    var pendingDestinationName by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = AppDestination.entries.first { it.name == destinationName }

    val requestSourceFolder = {
        if (uiState.sourceUri == null) {
            showFolderAccessExplanation = true
        } else {
            onPickSource()
        }
    }
    val completeDestinationSelection: (AppDestination) -> Unit = { selected ->
        if (selected != destination) {
            if (uiState.activePlan != null) actions.discardPreview()
            destinationName = selected.name
            when (selected) {
                AppDestination.ORGANIZE -> actions.selectMode(OperationMode.ORGANIZE_MEDIA)
                AppDestination.BATCH_RENAME -> actions.selectMode(OperationMode.BATCH_RENAME)
                AppDestination.FIND_AND_REPLACE -> actions.selectMode(OperationMode.FIND_AND_REPLACE)
                AppDestination.SETTINGS -> Unit
            }
        }
    }
    val selectDestination: (AppDestination) -> Unit = { selected ->
        if (!uiState.isWorking && selected != destination) {
            if (uiState.hasManualReviewChanges) {
                pendingDestinationName = selected.name
            } else {
                completeDestinationSelection(selected)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp
        val isExpanded = maxWidth >= 840.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(if (isCompact) "KP File Manager" else destination.label) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                if (isCompact) {
                    AppBottomNavigation(
                        selected = destination,
                        enabled = !uiState.isWorking,
                        onSelected = selectDestination,
                    )
                }
            },
        ) { scaffoldPadding ->
            if (isCompact) {
                DestinationContent(
                    destination = destination,
                    uiState = uiState,
                    actions = actions,
                    onPickSource = requestSourceFolder,
                    onPickDestination = onPickDestination,
                    themePreference = themePreference,
                    onThemePreferenceChange = onThemePreferenceChange,
                    isExpanded = false,
                    modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
                )
            } else {
                Row(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
                    if (isExpanded) {
                        ExpandedNavigation(
                            selected = destination,
                            enabled = !uiState.isWorking,
                            onSelected = selectDestination,
                            modifier = Modifier.fillMaxHeight().width(224.dp),
                        )
                    } else {
                        AppNavigationRail(
                            selected = destination,
                            enabled = !uiState.isWorking,
                            onSelected = selectDestination,
                        )
                    }
                    DestinationContent(
                        destination = destination,
                        uiState = uiState,
                        actions = actions,
                        onPickSource = requestSourceFolder,
                        onPickDestination = onPickDestination,
                        themePreference = themePreference,
                        onThemePreferenceChange = onThemePreferenceChange,
                        isExpanded = isExpanded,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }

    if (showFolderAccessExplanation) {
        AlertDialog(
            onDismissRequest = { showFolderAccessExplanation = false },
            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
            title = { Text("Choose a Media Folder") },
            text = {
                Text(
                    "KP File Manager needs access to the folder containing your media so it can scan and organize files. You choose exactly which folder the app can access.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFolderAccessExplanation = false
                        onPickSource()
                    },
                ) {
                    Text("Choose folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderAccessExplanation = false }) { Text("Not now") }
            },
        )
    }
    val pendingDestination = pendingDestinationName?.let { pendingName ->
        AppDestination.entries.firstOrNull { it.name == pendingName }
    }
    if (pendingDestination != null) {
        AlertDialog(
            onDismissRequest = { pendingDestinationName = null },
            title = { Text("Leave review?") },
            text = { Text("Your conflict resolutions and review changes will be discarded.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDestinationName = null
                        completeDestinationSelection(pendingDestination)
                    },
                ) {
                    Text("Leave review")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDestinationName = null }) { Text("Stay") }
            },
        )
    }
}

@Composable
private fun DestinationContent(
    destination: AppDestination,
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        when (destination) {
            AppDestination.ORGANIZE -> OrganizerScreen(
                uiState = uiState,
                actions = actions,
                onPickSource = onPickSource,
                onPickDestination = onPickDestination,
                isExpanded = isExpanded,
                modifier = Modifier.fillMaxSize(),
            )
            AppDestination.BATCH_RENAME -> BatchRenameScreen(
                uiState = uiState,
                actions = actions,
                onPickSource = onPickSource,
                modifier = Modifier.fillMaxSize(),
            )
            AppDestination.FIND_AND_REPLACE -> FindAndReplaceScreen(
                uiState = uiState,
                actions = actions,
                onPickSource = onPickSource,
                modifier = Modifier.fillMaxSize(),
            )
            AppDestination.SETTINGS -> SettingsScreen(
                uiState = uiState,
                actions = actions,
                themePreference = themePreference,
                onThemePreferenceChange = onThemePreferenceChange,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AppBottomNavigation(
    selected: AppDestination,
    enabled: Boolean,
    onSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(destination.icon(), contentDescription = null)
                },
                label = { Text(destination.compactLabel) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun AppNavigationRail(
    selected: AppDestination,
    enabled: Boolean,
    onSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            AppDestination.entries.forEach { destination ->
                NavigationRailItem(
                    selected = selected == destination,
                    onClick = { onSelected(destination) },
                    icon = { Icon(destination.icon(), contentDescription = null) },
                    label = { Text(destination.compactLabel) },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun ExpandedNavigation(
    selected: AppDestination,
    enabled: Boolean,
    onSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.related),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        ) {
            Text(
                text = "KP File Manager",
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.spacing.related,
                    vertical = MaterialTheme.spacing.standard,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            AppDestination.entries.forEach { destination ->
                val isSelected = selected == destination
                Surface(
                    onClick = { onSelected(destination) },
                    modifier = Modifier.semantics {
                        this.selected = isSelected
                    },
                    enabled = enabled,
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.spacing.related,
                            vertical = MaterialTheme.spacing.related,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(destination.icon(), contentDescription = null)
                        Text(destination.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.ORGANIZE -> Icons.Outlined.FolderOpen
    AppDestination.BATCH_RENAME -> Icons.Outlined.Edit
    AppDestination.FIND_AND_REPLACE -> Icons.Outlined.FindReplace
    AppDestination.SETTINGS -> Icons.Outlined.Settings
}
