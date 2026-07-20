/** mobile/app/src/main/java/com/kp/filemanager/ui/FileManagerScreen.kt: Renders the accessible, adaptive single-screen file workflow; created for the mobile UI. */
package com.kp.filemanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kp.filemanager.domain.OperationMode
import com.kp.filemanager.domain.PlannedChange

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FileManagerContent(
        uiState = uiState,
        actions = viewModel,
        onPickSource = onPickSource,
        onPickDestination = onPickDestination,
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KP File Manager", style = MaterialTheme.typography.titleLarge)
                        Text("Private, local, and root-free", style = MaterialTheme.typography.labelMedium)
                    }
                },
                actions = {
                    TextButton(onClick = actions::undo, enabled = uiState.canUndo && !uiState.isWorking) {
                        Text("Undo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            ActionBar(
                isWorking = uiState.isWorking,
                enabled = uiState.canRun,
                onPreview = actions::preview,
                onRun = actions::run,
            )
        },
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(scaffoldPadding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item { HeroCard() }
                if (uiState.statusTitle != null) {
                    item {
                        StatusCard(
                            title = uiState.statusTitle,
                            message = uiState.statusMessage.orEmpty(),
                            failures = uiState.failures,
                            onDismiss = actions::dismissStatus,
                        )
                    }
                }
                item {
                    FolderSection(
                        uiState = uiState,
                        onPickSource = onPickSource,
                        onPickDestination = onPickDestination,
                        onClearDestination = actions::clearDestination,
                    )
                }
                item { OperationPicker(selected = uiState.mode, onSelected = actions::selectMode) }
                item { OperationSettings(uiState = uiState, actions = actions) }
                if (uiState.preview.isNotEmpty()) {
                    item { PreviewSection(changes = uiState.preview) }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun HeroCard(modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("A calmer way to clean up files", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Choose a folder, review every proposed change, then run it on-device. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    message: String,
    failures: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            failures.take(3).forEach { failure ->
                Text("• $failure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (failures.size > 3) Text("And ${failures.size - 3} more…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FolderSection(
    uiState: FileManagerUiState,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    onClearDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Folders", "Android’s system picker grants access only where you choose.")
        FolderCard(
            label = "Source folder",
            folderName = uiState.sourceName.ifBlank { "No folder selected" },
            buttonLabel = if (uiState.sourceUri == null) "Choose source" else "Change",
            onChoose = onPickSource,
        )
        if (uiState.mode == OperationMode.SORT) {
            FolderCard(
                label = "Destination folder",
                folderName = uiState.destinationName.ifBlank { "Same as source" },
                buttonLabel = if (uiState.destinationUri == null) "Choose another" else "Change",
                onChoose = onPickDestination,
                onClear = onClearDestination.takeIf { uiState.destinationUri != null },
            )
        }
    }
}

@Composable
private fun FolderCard(
    label: String,
    folderName: String,
    buttonLabel: String,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) { Text("▰", style = MaterialTheme.typography.titleLarge) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(folderName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
            if (onClear != null) TextButton(onClick = onClear) { Text("Reset") }
            OutlinedButton(onClick = onChoose) { Text(buttonLabel) }
        }
    }
}

@Composable
private fun OperationPicker(
    selected: OperationMode,
    onSelected: (OperationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Choose a tool", "Each tool uses the source folder selected above.")
        LazyRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp),
        ) {
            items(OperationMode.entries, key = OperationMode::name) { mode ->
                val isSelected = mode == selected
                ElevatedCard(
                    modifier = Modifier
                        .width(168.dp)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelected(mode) },
                            role = Role.RadioButton,
                        ),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(mode.label, style = MaterialTheme.typography.titleMedium)
                        Text(mode.supportingText, style = MaterialTheme.typography.bodySmall, minLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationSettings(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("${uiState.mode.label} settings", settingsDescription(uiState.mode))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (uiState.mode) {
                    OperationMode.SORT -> ToggleRow(
                        title = "Series mode",
                        supportingText = "Group files that share a normalized show title.",
                        checked = uiState.seriesMode,
                        onCheckedChange = actions::setSeriesMode,
                    )
                    OperationMode.MASS_RENAME -> MassRenameSettings(uiState, actions)
                    OperationMode.MUSIC_RENAME -> Text(
                        "MP3 files use embedded track, artist, and title tags. Files without complete tags receive a cleaned-up filename.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OperationMode.KEYWORD_RENAME -> KeywordSettings(uiState, actions)
                }
            }
        }
    }
}

@Composable
private fun MassRenameSettings(uiState: FileManagerUiState, actions: FileManagerActions) {
    OutlinedTextField(
        value = uiState.extensionFilter,
        onValueChange = actions::setExtensionFilter,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("File extension") },
        supportingText = { Text("Use * or leave blank for every file type") },
        singleLine = true,
    )
    OutlinedTextField(
        value = uiState.prefix,
        onValueChange = actions::setPrefix,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Filename prefix") },
        singleLine = true,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberStepper(
            label = "Start",
            value = uiState.startIndex,
            onValueChange = actions::setStartIndex,
            modifier = Modifier.weight(1f),
        )
        NumberStepper(
            label = "Padding",
            value = uiState.zeroPadding,
            onValueChange = actions::setZeroPadding,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider()
    ToggleRow(
        title = "Scramble numbers",
        supportingText = "Assign unique random values instead of a sequence.",
        checked = uiState.scramble,
        onCheckedChange = actions::setScramble,
    )
    if (uiState.scramble) {
        NumberStepper(
            label = "Scramble range multiplier",
            value = uiState.scrambleMultiplier,
            onValueChange = actions::setScrambleMultiplier,
        )
    }
}

@Composable
private fun KeywordSettings(uiState: FileManagerUiState, actions: FileManagerActions) {
    OutlinedTextField(
        value = uiState.extensionFilter,
        onValueChange = actions::setExtensionFilter,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("File extension") },
        supportingText = { Text("Use * or leave blank for every file type") },
        singleLine = true,
    )
    OutlinedTextField(
        value = uiState.keyword,
        onValueChange = actions::setKeyword,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Find in filename") },
        supportingText = if (uiState.keyword.isBlank()) ({ { Text("Required") } })() else null,
        isError = uiState.keyword.isBlank(),
        singleLine = true,
    )
    OutlinedTextField(
        value = uiState.replacement,
        onValueChange = actions::setReplacement,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Replace with") },
        supportingText = { Text("Leave blank to remove the matching text") },
        singleLine = true,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(supportingText, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onValueChange(value - 1) },
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("−") }
            Text(value.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) { Text("+") }
        }
    }
}

@Composable
private fun PreviewSection(changes: List<PlannedChange>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Review changes", "Showing ${minOf(changes.size, 30)} of ${changes.size} planned change(s).")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                changes.take(30).forEachIndexed { index, change ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(change.sourceName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "→ ${change.destinationPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (index < minOf(changes.lastIndex, 29)) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, supportingText: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionBar(
    isWorking: Boolean,
    enabled: Boolean,
    onPreview: () -> Unit,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column {
            if (isWorking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = onPreview, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Preview") }
                Button(onClick = onRun, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Run changes") }
            }
        }
    }
}

private fun settingsDescription(mode: OperationMode): String = when (mode) {
    OperationMode.SORT -> "Choose standard categories or group matching series titles."
    OperationMode.MASS_RENAME -> "Create predictable sequential or unique scrambled names."
    OperationMode.MUSIC_RENAME -> "No extra setup is required."
    OperationMode.KEYWORD_RENAME -> "Replace matching text while preserving each extension."
}
