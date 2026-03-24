package com.rawsweep.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DeleteConfirmDialog(
    count: Int,
    formattedSize: String,
    alsoDeleteFromGooglePhotos: Boolean,
    onAlsoDeleteFromGooglePhotosChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text("Delete $count RAW photo${if (count > 1) "s" else ""}?")
        },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(
                    "This will permanently delete $count RAW file${if (count > 1) "s" else ""} " +
                            "($formattedSize). Your JPEG versions will be kept.\n\n" +
                            "This action cannot be undone."
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = alsoDeleteFromGooglePhotos,
                        onCheckedChange = onAlsoDeleteFromGooglePhotosChange,
                    )
                    Text(
                        "Open Google Photos after delete so you can remove backed-up cloud copies too.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
