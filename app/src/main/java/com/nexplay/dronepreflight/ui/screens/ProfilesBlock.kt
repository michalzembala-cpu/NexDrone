package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.DroneProfile
import com.nexplay.dronepreflight.data.tempIn
import com.nexplay.dronepreflight.data.tempToC
import com.nexplay.dronepreflight.data.windIn
import com.nexplay.dronepreflight.data.windToMs
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

@Composable
fun ProfilesBlock(
    profiles: List<DroneProfile>,
    activeId: String?,
    units: DisplayUnits = DisplayUnits(),
    onSave: (DroneProfile) -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String?) -> Unit,
) {
    var showEditor: DroneProfile? by remember { mutableStateOf(null) }
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
                        "PROFILE BSP",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "Zapisz limity dla każdego drona osobno.",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilledTonalIconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj profil")
                }
            }
            Spacer(Modifier.height(10.dp))

            if (profiles.isEmpty()) {
                Text(
                    "Brak profili — dodaj pierwszy klikając „+”.",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                profiles.forEach { p ->
                    ProfileRow(
                        profile = p,
                        units = units,
                        active = activeId == p.id,
                        onClick = { onSetActive(p.id) },
                        onEdit = { showEditor = p },
                        onDelete = { onDelete(p.id) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        ProfileEditorDialog(
            initial = null,
            units = units,
            onDismiss = { showAdd = false },
            onSave = {
                showAdd = false
                onSave(it)
            },
        )
    }
    showEditor?.let { p ->
        ProfileEditorDialog(
            initial = p,
            units = units,
            onDismiss = { showEditor = null },
            onSave = {
                showEditor = null
                onSave(it)
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: DroneProfile,
    units: DisplayUnits,
    active: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                Icons.Default.FlightTakeoff,
                contentDescription = null,
                tint = if (active) VerdictColors.Go else OpsColors.Accent,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        color = OpsColors.TextPrimary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (active) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .background(VerdictColors.Go.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("AKTYWNY", color = VerdictColors.Go, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    "Wiatr max %.1f %s · %.0f%s – %.0f%s".format(
                        profile.maxWindMs.windIn(units.wind), units.wind.short,
                        profile.minTempC.tempIn(units.temp), units.temp.short,
                        profile.maxTempC.tempIn(units.temp), units.temp.short,
                    ),
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onEdit) { Text("Edytuj") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = VerdictColors.NoGo)
            }
        }
    }
}

@Composable
private fun ProfileEditorDialog(
    initial: DroneProfile?,
    units: DisplayUnits,
    onDismiss: () -> Unit,
    onSave: (DroneProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // Pola w jednostce użytkownika; konwersja przy zapisie.
    var wind by remember(units.wind) {
        mutableStateOf("%.1f".format((initial?.maxWindMs ?: 10.0).windIn(units.wind)))
    }
    var tmin by remember(units.temp) {
        mutableStateOf("%.1f".format((initial?.minTempC ?: 0.0).tempIn(units.temp)))
    }
    var tmax by remember(units.temp) {
        mutableStateOf("%.1f".format((initial?.maxTempC ?: 40.0).tempIn(units.temp)))
    }
    val windVal = wind.replace(',', '.').toDoubleOrNull()
    val tminVal = tmin.replace(',', '.').toDoubleOrNull()
    val tmaxVal = tmax.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && windVal != null && tminVal != null && tmaxVal != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nowy profil BSP" else "Edytuj profil") },
        text = {
            Column {
                Text(
                    "Wartości z instrukcji obsługi drona.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nazwa (np. DJI Mini 3)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = wind, onValueChange = { wind = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Maks. wiatr (${units.wind.short})") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tmin, onValueChange = { tmin = it.filter { c -> c.isDigit() || c == '-' || c == '.' || c == ',' } },
                    label = { Text("Min. temperatura (${units.temp.short})") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tmax, onValueChange = { tmax = it.filter { c -> c.isDigit() || c == '-' || c == '.' || c == ',' } },
                    label = { Text("Maks. temperatura (${units.temp.short})") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = initial?.id ?: "prof_${System.currentTimeMillis()}"
                    onSave(
                        DroneProfile(
                            id = id, name = name.trim(),
                            maxWindMs = windVal!!.windToMs(units.wind),
                            minTempC = tminVal!!.tempToC(units.temp),
                            maxTempC = tmaxVal!!.tempToC(units.temp),
                        )
                    )
                },
                enabled = valid,
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
