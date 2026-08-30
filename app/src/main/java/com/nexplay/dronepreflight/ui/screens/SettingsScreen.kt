package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.DroneLimits
import com.nexplay.dronepreflight.data.DroneProfile
import com.nexplay.dronepreflight.data.SavedLocation
import com.nexplay.dronepreflight.data.TempUnit
import com.nexplay.dronepreflight.data.WindUnit
import com.nexplay.dronepreflight.data.tempIn
import com.nexplay.dronepreflight.data.tempToC
import com.nexplay.dronepreflight.data.windIn
import com.nexplay.dronepreflight.data.windToMs
import com.nexplay.dronepreflight.ui.theme.DronePreflightTheme
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    limits: DroneLimits,
    onSave: (DroneLimits) -> Unit,
    locations: List<SavedLocation> = emptyList(),
    activeLocationId: String? = null,
    onAddLocation: (String, Double, Double) -> Unit = { _, _, _ -> },
    onSetActiveLocation: (String?) -> Unit = {},
    onDeleteLocation: (String) -> Unit = {},
    notificationsEnabled: Boolean = false,
    onToggleNotifications: (Boolean) -> Unit = {},
    droneProfiles: List<DroneProfile> = emptyList(),
    activeProfileId: String? = null,
    onSaveProfile: (DroneProfile) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    onSetActiveProfile: (String?) -> Unit = {},
    units: DisplayUnits = DisplayUnits(),
    onSetWindUnit: (WindUnit) -> Unit = {},
    onSetTempUnit: (TempUnit) -> Unit = {},
    onExportBackup: (suspend () -> Uri)? = null,
    onImportBackup: (suspend (Uri) -> Result<Unit>)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importResult by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && onImportBackup != null) {
            scope.launch {
                val res = onImportBackup(uri)
                importResult = if (res.isSuccess) "✓ Zaimportowano ustawienia"
                else "✗ Błąd: ${res.exceptionOrNull()?.message?.take(80)}"
            }
        }
    }

    // Pola trzymamy w wybranej jednostce użytkownika; konwersja na m/s / °C przy save.
    var maxWind by remember(limits, units.wind) {
        mutableStateOf("%.1f".format(limits.maxWindMs.windIn(units.wind)))
    }
    var minTemp by remember(limits, units.temp) {
        mutableStateOf("%.1f".format(limits.minTempC.tempIn(units.temp)))
    }
    var maxTemp by remember(limits, units.temp) {
        mutableStateOf("%.1f".format(limits.maxTempC.tempIn(units.temp)))
    }
    var savedTick by remember { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfilesBlock(
            profiles = droneProfiles,
            activeId = activeProfileId,
            units = units,
            onSave = onSaveProfile,
            onDelete = onDeleteProfile,
            onSetActive = onSetActiveProfile,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Domyślne limity (bez profilu)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Używane gdy nie masz żadnego aktywnego profilu BSP. Zwykle wystarczy dodać profil powyżej.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = maxWind,
            onValueChange = { maxWind = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            label = { Text("Maksymalny wiatr (${units.wind.short})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = minTemp,
            onValueChange = { minTemp = it.filter { c -> c.isDigit() || c == '-' || c == '.' || c == ',' } },
            label = { Text("Minimalna temperatura (${units.temp.short})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = maxTemp,
            onValueChange = { maxTemp = it.filter { c -> c.isDigit() || c == '-' || c == '.' || c == ',' } },
            label = { Text("Maksymalna temperatura (${units.temp.short})") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val windParsed = maxWind.replace(',', '.').toDoubleOrNull()
                val tminParsed = minTemp.replace(',', '.').toDoubleOrNull()
                val tmaxParsed = maxTemp.replace(',', '.').toDoubleOrNull()
                val parsed = DroneLimits(
                    // Konwersja z jednostki użytkownika z powrotem do m/s / °C (canonical)
                    maxWindMs = windParsed?.windToMs(units.wind) ?: limits.maxWindMs,
                    minTempC = tminParsed?.tempToC(units.temp) ?: limits.minTempC,
                    maxTempC = tmaxParsed?.tempToC(units.temp) ?: limits.maxTempC,
                )
                onSave(parsed)
                savedTick++
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Zapisz") }

        if (savedTick > 0) {
            Text(
                "Zapisano ($savedTick).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        LocationsBlock(
            locations = locations,
            activeId = activeLocationId,
            onSave = onAddLocation,
            onSetActive = onSetActiveLocation,
            onDelete = onDeleteLocation,
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Jednostki", style = MaterialTheme.typography.titleMedium)
        Text(
            "Wiatr",
            style = MaterialTheme.typography.labelMedium,
            color = OpsColors.TextSecondary,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WindUnit.entries.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = units.wind == u,
                    onClick = { onSetWindUnit(u) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = WindUnit.entries.size),
                ) { Text(u.short) }
            }
        }
        Text(
            "Temperatura",
            style = MaterialTheme.typography.labelMedium,
            color = OpsColors.TextSecondary,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TempUnit.entries.forEachIndexed { i, u ->
                SegmentedButton(
                    selected = units.temp == u,
                    onClick = { onSetTempUnit(u) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = TempUnit.entries.size),
                ) { Text(u.short) }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Powiadomienia o oknach GO", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Sprawdzam co ~1h najbliższe 24h. Powiadomię, gdy pojawi się okno ≥2h z werdyktem GO.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = notificationsEnabled, onCheckedChange = onToggleNotifications)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Kopia zapasowa", style = MaterialTheme.typography.titleMedium)
        Text(
            "Zapisz wszystkie ustawienia + historię + lokalizacje + profile do pliku JSON. Przydatne przy zmianie telefonu.",
            style = MaterialTheme.typography.bodySmall,
            color = OpsColors.TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (onExportBackup != null) scope.launch {
                        val uri = onExportBackup()
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "NexDrone — backup")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Zapisz backup"))
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = onExportBackup != null,
            ) { Text("Eksport JSON") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.weight(1f),
                enabled = onImportBackup != null,
            ) { Text("Import JSON") }
        }
        importResult?.let {
            Text(
                it,
                color = if (it.startsWith("✓")) VerdictColors.Go else VerdictColors.NoGo,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text("Źródła danych", style = MaterialTheme.typography.titleMedium)
        Text(
            "• Pogoda: Open-Meteo (api.open-meteo.com)\n" +
                    "• KP index: NOAA SWPC (services.swpc.noaa.gov)\n" +
                    "• Strefy geograficzne: sprawdź drone.pansa.pl przed lotem\n" +
                    "• Check-in operacji lotniczej: checkin.pansa.pl",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview(showBackground = true, name = "Ustawienia")
@Composable
private fun SettingsPreview() = DronePreflightTheme {
    SettingsScreen(limits = DroneLimits(), onSave = {})
}
