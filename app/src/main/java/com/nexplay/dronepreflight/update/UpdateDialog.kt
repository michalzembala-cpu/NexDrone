package com.nexplay.dronepreflight.update

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.ui.theme.OpsColors

@Composable
fun UpdateAvailableDialog(
    info: GithubUpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onDismissForever: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    val sizeMb = if (info.sizeBytes > 0) "%.1f MB".format(info.sizeBytes / 1024.0 / 1024.0) else "?"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowa wersja ${info.latestVersion}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Masz zainstalowaną wersję ${info.currentVersion}. Rozmiar update: $sizeMb.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Co nowego:",
                        style = MaterialTheme.typography.labelMedium,
                        color = OpsColors.TextSecondary,
                    )
                    Text(
                        info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = OpsColors.Accent, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = info.downloadUrl != null && status == null,
                onClick = {
                    info.downloadUrl?.let { url ->
                        UpdateInstaller.downloadAndInstall(context, url) { s -> status = s }
                    }
                },
            ) { Text("Pobierz i zainstaluj") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismissForever) {
                    Text("Pomiń tę wersję", color = OpsColors.TextSecondary)
                }
                TextButton(onClick = onDismiss) { Text("Później") }
            }
        },
    )
}
