package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.SavedLocation
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

@Composable
fun LocationsBlock(
    locations: List<SavedLocation>,
    activeId: String?,
    onSave: (name: String, lat: Double, lon: Double) -> Unit,
    onSetActive: (String?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "MOJE LOKALIZACJE",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "Home, spoty do latania — wybierz jednym tapnięciem.",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalIconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj lokalizację")
                }
            }
            Spacer(Modifier.height(10.dp))

            // "GPS teraz" — opcja bez zapisanej lokalizacji
            LocationRow(
                title = "GPS (obecna pozycja)",
                subtitle = "Automatyczna lokalizacja z telefonu",
                active = activeId == null,
                onClick = { onSetActive(null) },
                onDelete = null,
            )
            locations.forEach { loc ->
                LocationRow(
                    title = loc.name,
                    subtitle = "%.4f, %.4f".format(loc.lat, loc.lon),
                    active = activeId == loc.id,
                    onClick = { onSetActive(loc.id) },
                    onDelete = { onDelete(loc.id) },
                )
            }
        }
    }

    if (showAdd) {
        AddLocationDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, lat, lon ->
                showAdd = false
                onSave(name, lat, lon)
            },
        )
    }
}

@Composable
private fun LocationRow(
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val border = if (active) BorderStroke(1.dp, VerdictColors.Go)
    else BorderStroke(1.dp, OpsColors.Grid)
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanelRaised),
        border = border,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (active) VerdictColors.Go else OpsColors.Accent,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = OpsColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = OpsColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (active) {
                Box(
                    Modifier
                        .background(VerdictColors.Go.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("AKTYWNA", color = VerdictColors.Go, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = VerdictColors.NoGo)
                }
            }
        }
    }
}

@Composable
private fun AddLocationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    val latVal = lat.replace(',', '.').toDoubleOrNull()
    val lonVal = lon.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && latVal != null && lonVal != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nowa lokalizacja") },
        text = {
            Column {
                Text(
                    "Współrzędne skopiujesz np. z Google Maps (długie przytrzymanie na miejscu → \"lat, lon\" na górze).",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nazwa (np. Dom, Spot rzeka)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lat, onValueChange = { lat = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' } },
                    label = { Text("Szerokość geograficzna (lat)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lon, onValueChange = { lon = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' } },
                    label = { Text("Długość geograficzna (lon)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, latVal ?: 0.0, lonVal ?: 0.0) },
                enabled = valid,
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
