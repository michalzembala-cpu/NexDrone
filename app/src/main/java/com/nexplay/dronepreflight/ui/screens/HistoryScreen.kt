package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.content.Intent
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.nexplay.dronepreflight.pdf.FlightLogPdfExporter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.FlightLogEntry
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.ui.UiState
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    state: UiState,
    onSaveCurrent: (note: String, minutes: Int?) -> Unit,
    onDelete: (String) -> Unit,
    onUpdateNote: (String, String) -> Unit,
    units: com.nexplay.dronepreflight.data.DisplayUnits = com.nexplay.dronepreflight.data.DisplayUnits(),
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var editingEntry: FlightLogEntry? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Historia lotów", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${state.flightLog.size} zapisanych lotów",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsColors.TextSecondary,
                        )
                    }
                    FilledTonalButton(
                        onClick = { showSaveDialog = true },
                        enabled = state.snapshot != null,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Zapisz lot")
                    }
                }
                if (state.flightLog.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val uri = FlightLogPdfExporter.export(context, state.flightLog, units)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "NexDrone — historia lotów")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(share, "Udostępnij PDF"),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Eksport PDF (${state.flightLog.size} lotów)")
                    }
                }
            }
        }

        if (state.flightLog.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Brak zapisanych lotów",
                    style = MaterialTheme.typography.titleMedium,
                    color = OpsColors.TextSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Po każdym locie kliknij „Zapisz lot” u góry — historia pomoże Ci uczyć się na własnych warunkach.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpsColors.TextSecondary,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "stats") {
                    FlightStatsCard(state.flightLog)
                }
                items(state.flightLog, key = { it.id }) { entry ->
                    FlightRow(
                        entry = entry,
                        units = units,
                        onEdit = { editingEntry = entry },
                        onDelete = { onDelete(entry.id) },
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveFlightDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { note, minutes ->
                showSaveDialog = false
                onSaveCurrent(note, minutes)
            },
        )
    }

    editingEntry?.let { entry ->
        EditNoteDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { note ->
                onUpdateNote(entry.id, note)
                editingEntry = null
            },
        )
    }
}

@Composable
private fun FlightRow(
    entry: FlightLogEntry,
    units: com.nexplay.dronepreflight.data.DisplayUnits,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val verdict = runCatching { Verdict.valueOf(entry.verdict) }.getOrNull()
    val color = when (verdict) {
        Verdict.GO -> VerdictColors.Go
        Verdict.CAUTION -> VerdictColors.Caution
        Verdict.NO_GO -> VerdictColors.NoGo
        null -> OpsColors.TextSecondary
    }
    val date = SimpleDateFormat("d MMM yyyy · HH:mm", Locale("pl")).format(Date(entry.timestamp))
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, RoundedCornerShape(5.dp))
                    .padding(top = 6.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.locationName,
                        style = MaterialTheme.typography.titleSmall,
                        color = OpsColors.TextPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        entry.verdict,
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(date, style = MaterialTheme.typography.bodySmall, color = OpsColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                val bits = buildList {
                    entry.tempC?.let { add(com.nexplay.dronepreflight.data.formatTemp(it, units.temp)) }
                    entry.windMs?.let { add("wiatr " + com.nexplay.dronepreflight.data.formatWind(it, units.wind)) }
                    entry.gustMs?.let { add("porywy " + com.nexplay.dronepreflight.data.formatWind(it, units.wind)) }
                    entry.kpIndex?.let { add("Kp %.1f".format(it)) }
                    entry.durationMinutes?.let { add("lot ${it} min") }
                }
                if (bits.isNotEmpty()) {
                    Text(
                        bits.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OpsColors.TextPrimary,
                    )
                }
                if (entry.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = OpsColors.TextSecondary,
                    )
                }
                Row {
                    TextButton(onClick = onEdit) { Text("Edytuj notatkę") }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = VerdictColors.NoGo)
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveFlightDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int?) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zapisz lot") },
        text = {
            Column {
                Text(
                    "Zapiszemy aktualne warunki pogodowe z Pulpitu jako wpis w historii.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                    label = { Text("Czas lotu (minuty)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notatka") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note, duration.toIntOrNull()) }) { Text("Zapisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}

@Composable
private fun EditNoteDialog(
    entry: FlightLogEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edytuj notatkę") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text("Zapisz") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
