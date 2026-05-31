package com.adsamcik.temperaturedashboard.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adsamcik.temperaturedashboard.core.designsystem.TdashSpacing
import com.adsamcik.temperaturedashboard.core.model.Sensor
import com.adsamcik.temperaturedashboard.core.ui.resources.Res
import com.adsamcik.temperaturedashboard.core.ui.resources.action_cancel
import com.adsamcik.temperaturedashboard.core.ui.resources.action_change_color
import com.adsamcik.temperaturedashboard.core.ui.resources.action_delete
import com.adsamcik.temperaturedashboard.core.ui.resources.action_delete_supporting
import com.adsamcik.temperaturedashboard.core.ui.resources.action_hide
import com.adsamcik.temperaturedashboard.core.ui.resources.action_history_kept_either_way
import com.adsamcik.temperaturedashboard.core.ui.resources.action_rename
import com.adsamcik.temperaturedashboard.core.ui.resources.action_save
import com.adsamcik.temperaturedashboard.core.ui.resources.action_unhide
import com.adsamcik.temperaturedashboard.core.ui.resources.dialog_color_explainer
import com.adsamcik.temperaturedashboard.core.ui.resources.dialog_color_swatch_a11y
import com.adsamcik.temperaturedashboard.core.ui.resources.dialog_color_title
import com.adsamcik.temperaturedashboard.core.ui.resources.dialog_delete_text
import com.adsamcik.temperaturedashboard.core.ui.resources.dialog_delete_title
import com.adsamcik.temperaturedashboard.core.ui.resources.dialog_rename_label
import com.adsamcik.temperaturedashboard.core.ui.resources.dialog_rename_title
import org.jetbrains.compose.resources.stringResource

/**
 * Modal bottom sheet shown when the user long-presses a sensor card.
 * Offers Rename / Change colour / Hide / Delete.
 *
 * Dialogs (rename / colour pick / delete confirm) are local to this composable
 * so the dashboard route only needs to know the resulting actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorActionSheet(
    sensor: Sensor,
    onRename: (newName: String) -> Unit,
    onChangeColor: (argb: Int) -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingDialog by remember { mutableStateOf<PendingDialog>(PendingDialog.None) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = TdashSpacing.l)) {
            Text(
                text = sensor.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = TdashSpacing.m, vertical = TdashSpacing.s),
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.action_rename)) },
                leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                modifier = Modifier.clickable { pendingDialog = PendingDialog.Rename },
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.action_change_color)) },
                leadingContent = { Icon(Icons.Outlined.ColorLens, contentDescription = null) },
                modifier = Modifier.clickable { pendingDialog = PendingDialog.Color },
            )
            ListItem(
                headlineContent = {
                    Text(
                        if (sensor.hidden) stringResource(Res.string.action_unhide)
                        else stringResource(Res.string.action_hide),
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(Res.string.action_history_kept_either_way),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                modifier = Modifier.clickable { onHide(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error) },
                supportingContent = {
                    Text(
                        text = stringResource(Res.string.action_delete_supporting),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable { pendingDialog = PendingDialog.Delete },
            )
        }
    }

    when (pendingDialog) {
        PendingDialog.Rename -> RenameDialog(
            initial = sensor.displayName,
            onConfirm = { newName ->
                onRename(newName)
                pendingDialog = PendingDialog.None
                onDismiss()
            },
            onDismiss = { pendingDialog = PendingDialog.None },
        )
        PendingDialog.Color -> ColorPickerDialog(
            current = sensor.colorSeed,
            onConfirm = { argb ->
                onChangeColor(argb)
                pendingDialog = PendingDialog.None
                onDismiss()
            },
            onDismiss = { pendingDialog = PendingDialog.None },
        )
        PendingDialog.Delete -> AlertDialog(
            onDismissRequest = { pendingDialog = PendingDialog.None },
            title = { Text(stringResource(Res.string.dialog_delete_title, sensor.displayName)) },
            text = { Text(stringResource(Res.string.dialog_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    pendingDialog = PendingDialog.None
                    onDismiss()
                }) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDialog = PendingDialog.None }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
        PendingDialog.None -> Unit
    }
}

private enum class PendingDialog { None, Rename, Color, Delete }

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(Res.string.dialog_rename_label)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank() && text.trim() != initial,
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun ColorPickerDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_color_title)) },
        text = {
            Column {
                Text(
                    stringResource(Res.string.dialog_color_explainer),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.padding(top = TdashSpacing.m),
                    horizontalArrangement = Arrangement.spacedBy(TdashSpacing.s),
                ) {
                    val swatchCd = stringResource(Res.string.dialog_color_swatch_a11y)
                    PRESET_COLORS.forEach { argb ->
                        val color = Color(argb.toLong() or 0xFF000000L)
                        val isSelected = argb == selected
                        Row(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .semantics { contentDescription = swatchCd }
                                .clickable { selected = argb },
                            content = {},
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Reasonable starter palette — warm + cool, enough to distinguish ~8 sensors. */
private val PRESET_COLORS: List<Int> = listOf(
    0xB52A1F.toInt(), // coral (default theme primary)
    0xE0A810.toInt(), // amber
    0x4CAF50.toInt(), // green
    0x009688.toInt(), // teal
    0x2196F3.toInt(), // blue
    0x673AB7.toInt(), // deep purple
    0xE91E63.toInt(), // pink
    0x607D8B.toInt(), // blue grey
)
