/** mobile/app/src/main/java/com/kp/filemanager/ui/screens/SettingsScreen.kt: Renders minimal organization, appearance, and safety settings; created for the UI refactor. */
package com.kp.filemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp
import com.kp.filemanager.ui.FileManagerActions
import com.kp.filemanager.ui.FileManagerUiState
import com.kp.filemanager.ui.components.NumberStepper
import com.kp.filemanager.ui.components.PageHeader
import com.kp.filemanager.ui.components.SectionHeader
import com.kp.filemanager.ui.theme.ThemePreference
import com.kp.filemanager.ui.theme.spacing

@Composable
fun SettingsScreen(
    uiState: FileManagerUiState,
    actions: FileManagerActions,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(MaterialTheme.spacing.standard),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        item {
            PageHeader(
                title = "Settings",
                supportingText = "Keep defaults simple and adjust only what affects your workflow.",
            )
        }
        item {
            SettingsCard(
                title = "Organization",
                icon = Icons.Outlined.FolderOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                NumberStepper(
                    label = "Default scan depth",
                    value = uiState.maximumScanDepth,
                    onValueChange = actions::setMaximumScanDepth,
                    valueSuffix = " levels",
                )
                OutlinedTextField(
                    value = uiState.movieFolderName,
                    onValueChange = actions::setMovieFolderName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Movie folder name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.tvFolderName,
                    onValueChange = actions::setTvFolderName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("TV folder name") },
                    singleLine = true,
                )
            }
        }
        item {
            SettingsCard(
                title = "Appearance",
                icon = Icons.Outlined.Palette,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.semantics { selectableGroup() }) {
                    ThemePreference.entries.forEach { preference ->
                        ThemePreferenceRow(
                            preference = preference,
                            selected = themePreference == preference,
                            onSelect = { onThemePreferenceChange(preference) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item {
            SettingsCard(
                title = "Behavior",
                icon = Icons.Outlined.Security,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Confirm before applying changes", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Always on for filesystem safety",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = "KP File Manager only accesses folders you explicitly select through Android's system folder picker. It does not request broad storage access.",
                    modifier = Modifier.padding(MaterialTheme.spacing.standard),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.standard),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                SectionHeader(title = title)
            }
            content()
        }
    }
}

@Composable
private fun ThemePreferenceRow(
    preference: ThemePreference,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = MaterialTheme.spacing.tight),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(preference.label, style = MaterialTheme.typography.bodyLarge)
    }
}
