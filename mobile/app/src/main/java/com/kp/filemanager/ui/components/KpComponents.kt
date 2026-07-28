/** mobile/app/src/main/java/com/kp/filemanager/ui/components/KpComponents.kt: Provides accessible cards, headers, status, determinate progress, and action components; updated for pipeline progress. */
package com.kp.filemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kp.filemanager.domain.OperationStatus
import com.kp.filemanager.domain.OperationType
import com.kp.filemanager.domain.PlannedFileOperation
import com.kp.filemanager.ui.theme.spacing

enum class MessageTone {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

@Composable
fun PageHeader(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.micro),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        action?.invoke()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.micro),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun FolderSelectorCard(
    title: String,
    folderName: String?,
    folderPath: String?,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No folder selected",
    selectLabel: String = "Select folder",
    changeLabel: String = "Change folder",
    onClear: (() -> Unit)? = null,
) {
    val hasFolder = !folderPath.isNullOrBlank()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.micro),
                ) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = folderName?.takeIf(String::isNotBlank) ?: emptyMessage,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (hasFolder) {
                        Text(
                            text = folderPath.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.MiddleEllipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight, Alignment.End),
            ) {
                if (onClear != null) {
                    TextButton(onClick = onClear) { Text("Use source folder") }
                }
                if (hasFolder) {
                    OutlinedButton(onClick = onSelect) { Text(changeLabel) }
                } else {
                    Button(onClick = onSelect) { Text(selectLabel) }
                }
            }
        }
    }
}

@Composable
fun NumberStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueSuffix: String = "",
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        ) {
            OutlinedButton(
                onClick = { onValueChange(value - 1) },
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = "$value$valueSuffix",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Increase $label")
            }
        }
    }
}

@Composable
fun StatusMessage(
    title: String,
    message: String,
    tone: MessageTone,
    modifier: Modifier = Modifier,
    details: List<String> = emptyList(),
    onDismiss: (() -> Unit)? = null,
) {
    val colors = messageColors(tone)
    Card(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.standard),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(colors.icon, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
            details.take(3).forEach { detail ->
                Text("• $detail", style = MaterialTheme.typography.bodySmall)
            }
            if (details.size > 3) {
                Text(
                    text = "And ${details.size - 3} more…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun ProgressPanel(
    title: String,
    supportingText: String,
    progressLabel: String,
    modifier: Modifier = Modifier,
    currentLabel: String? = null,
    currentValue: String? = null,
    onCancel: (() -> Unit)? = null,
    progressFraction: Float? = null,
) {
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.section),
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (progressFraction == null) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (progressFraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(progressLabel, style = MaterialTheme.typography.titleMedium)
        if (!currentValue.isNullOrBlank()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(MaterialTheme.spacing.standard),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.micro),
                ) {
                    Text(
                        text = currentLabel.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = currentValue,
                        maxLines = 2,
                        overflow = TextOverflow.MiddleEllipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (onCancel != null) {
            OutlinedButton(onClick = onCancel) { Text("Cancel scan") }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.standard),
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (primaryLabel != null && onPrimary != null) {
            Button(onClick = onPrimary) { Text(primaryLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: MessageTone = MessageTone.INFO,
) {
    val colors = messageColors(tone)
    OutlinedCard(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.related),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = colors.content)
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun OperationRow(
    operation: PlannedFileOperation,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.standard,
                vertical = MaterialTheme.spacing.related,
            ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(operation.status)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.originalName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.micro))
                    Text(
                        text = operation.destinationPath,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = operation.operationType.displayLabel(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    if (onClick != null) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            onClick = onClick,
            content = { content() },
        )
    } else {
        Surface(modifier = modifier, content = content)
    }
}

@Composable
fun StatusChip(
    status: OperationStatus,
    modifier: Modifier = Modifier,
) {
    val tone = when (status) {
        OperationStatus.CONFLICT, OperationStatus.FAILED -> MessageTone.ERROR
        OperationStatus.COMPLETED -> MessageTone.SUCCESS
        OperationStatus.SKIPPED, OperationStatus.UNCHANGED -> MessageTone.WARNING
        else -> MessageTone.INFO
    }
    val colors = messageColors(tone)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Icon(
            imageVector = colors.icon,
            contentDescription = status.displayLabel(),
            modifier = Modifier.padding(6.dp).size(18.dp),
        )
    }
}

@Composable
fun StickyActionBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    supportingText: String? = null,
) {
    Surface(modifier = modifier, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .padding(MaterialTheme.spacing.related),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.tight),
        ) {
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.related),
            ) {
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(secondaryLabel)
                    }
                }
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f),
                    enabled = primaryEnabled,
                ) {
                    Text(primaryLabel)
                }
            }
        }
    }
}

@Composable
private fun messageColors(tone: MessageTone): MessageColors = when (tone) {
    MessageTone.INFO -> MessageColors(
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        icon = Icons.Outlined.Info,
    )
    MessageTone.SUCCESS -> MessageColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = Icons.Outlined.CheckCircle,
    )
    MessageTone.WARNING -> MessageColors(
        container = MaterialTheme.colorScheme.tertiaryContainer,
        content = MaterialTheme.colorScheme.onTertiaryContainer,
        icon = Icons.Outlined.WarningAmber,
    )
    MessageTone.ERROR -> MessageColors(
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
        icon = Icons.Outlined.ErrorOutline,
    )
}

private data class MessageColors(
    val container: Color,
    val content: Color,
    val icon: ImageVector,
)

private fun OperationStatus.displayLabel(): String = when (this) {
    OperationStatus.PENDING -> "Pending"
    OperationStatus.READY -> "Ready"
    OperationStatus.RUNNING -> "Running"
    OperationStatus.COMPLETED -> "Completed"
    OperationStatus.SKIPPED -> "Skipped"
    OperationStatus.CONFLICT -> "Conflict"
    OperationStatus.FAILED -> "Failed"
    OperationStatus.UNCHANGED -> "Unchanged"
}

private fun OperationType.displayLabel(): String = when (this) {
    OperationType.MOVE -> "Move"
    OperationType.RENAME -> "Rename"
    OperationType.MOVE_AND_RENAME -> "Move + rename"
    OperationType.SKIP -> "No change"
}

@Composable
fun OperationListDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(horizontal = MaterialTheme.spacing.standard))
}

@Composable
fun ContentSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(MaterialTheme.spacing.standard))
}
