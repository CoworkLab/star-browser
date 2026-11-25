package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.viewmodel.PermissionRequestKind

@Composable
fun OnDemandPermissionDialog(
    kind: PermissionRequestKind,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, body, iconRes) = when (kind) {
        PermissionRequestKind.MICROPHONE -> Triple(
            "Enable Voice Dictation",
            "Star Browser only requests Microphone access when you tap the voice button to dictate a prompt. Audio is never recorded in the background.",
            R.drawable.ic_modern_mic
        )
        PermissionRequestKind.EXTERNAL_FOLDER -> Triple(
            "Link External Folder",
            "Choose a folder via Android's Storage Access Framework so the sandbox can exchange files with only that directory.",
            R.drawable.ic_modern_folder
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp)) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onAllow) {
                Text("Allow")
            }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = onOpenSettings) {
                    Text("App Settings")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text("Not Now")
                }
            }
        }
    )
}
