package com.example.beesmart.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.beesmart.R

/**
 * Confirmation shown when the user tries to leave an add/edit form that has unsaved changes.
 *
 * Offers three actions:
 *  - Save: persist the changes, then leave (only enabled when the form is valid)
 *  - Discard: leave without saving
 *  - Cancel: stay on the form
 *
 * @param canSave whether the form currently passes validation; when false, the "Save" action is disabled
 *               and the message nudges the user to either complete the form or discard.
 */
@Composable
fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    canSave: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.sx_misc_unsaved_title)) },
        text = {
            Text(
                if (canSave) {
                    stringResource(R.string.sx_misc_unsaved_message_can_save)
                } else {
                    stringResource(R.string.sx_misc_unsaved_message_cannot_save)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(stringResource(R.string.sx_misc_unsaved_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.sx_misc_unsaved_discard), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.sx_misc_unsaved_cancel))
                }
            }
        }
    )
}
