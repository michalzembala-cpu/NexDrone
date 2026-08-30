package com.nexplay.dronepreflight.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.ui.theme.OpsColors

@Composable
fun MapaScreen(
    snap: AggregatedSnapshot? = null,
    assessment: FlightAssessment? = null,
    pinnedCoords: Pair<Double, Double>? = null,
    onPin: (Double, Double) -> Unit = { _, _ -> },
    onClearPin: () -> Unit = {},
) {
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = fullscreen) { fullscreen = false }

    if (fullscreen) {
        // Fullscreen — tylko mapa
        Column(Modifier.fillMaxSize()) {
            ConditionsMapCard(
                snap = snap,
                assessment = assessment,
                pinnedCoords = pinnedCoords,
                onPin = onPin,
                onClearPin = onClearPin,
                fullscreen = true,
                onToggleFullscreen = { fullscreen = false },
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Nagłówek
        Text(
            "Mapa warunków",
            style = MaterialTheme.typography.headlineMedium,
            color = OpsColors.TextPrimary,
        )
        Text(
            "Twoja lokalizacja + strefy PANSA + oficjalne mapy CAA.",
            style = MaterialTheme.typography.bodyMedium,
            color = OpsColors.TextSecondary,
        )

        Spacer(Modifier.height(4.dp))

        // Podgląd mapy warunków (OSM + heatmap)
        ConditionsMapCard(
            snap = snap,
            assessment = assessment,
            pinnedCoords = pinnedCoords,
            onPin = onPin,
            onClearPin = onClearPin,
            fullscreen = false,
            onToggleFullscreen = { fullscreen = true },
        )

        // Główna karta z mapami
        Card(
            colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
            border = BorderStroke(1.dp, OpsColors.Grid),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        tint = OpsColors.Accent,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Mapy stref BSP",
                            style = MaterialTheme.typography.titleMedium,
                            color = OpsColors.TextPrimary,
                        )
                        Text(
                            "Wybierz źródło",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpsColors.TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                MapOption(
                    title = "DroneRadar (PANSA)",
                    subtitle = "droneradar.eu — mapa stref w czasie rzeczywistym",
                    url = "https://droneradar.eu/",
                    onClick = { openCustomTab(context, it) },
                    onExternal = { openExternal(context, it) },
                )
                Spacer(Modifier.height(10.dp))
                MapOption(
                    title = "PansaUTM (oficjalna PANSA)",
                    subtitle = "utm.pansa.pl — mapa i check-in",
                    url = "https://utm.pansa.pl/",
                    onClick = { openCustomTab(context, it) },
                    onExternal = { openExternal(context, it) },
                )
                Spacer(Modifier.height(10.dp))
                MapOption(
                    title = "Mapa CAA ULC",
                    subtitle = "caamap.ulc.gov.pl — Urząd Lotnictwa Cywilnego",
                    url = "https://caamap.ulc.gov.pl/",
                    onClick = { openCustomTab(context, it) },
                    onExternal = { openExternal(context, it) },
                )
                Spacer(Modifier.height(10.dp))
                MapOption(
                    title = "dron.pansa.pl (stara)",
                    subtitle = "Bywa niedostępna — użyj powyższych",
                    url = "https://dron.pansa.pl/",
                    onClick = { openCustomTab(context, it) },
                    onExternal = { openExternal(context, it) },
                )
            }
        }

        // Sekcja PANSA check-in
        Card(
            colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
            border = BorderStroke(1.dp, OpsColors.Grid),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "CHECK-IN LOTU",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Obowiązek zgłoszenia operacji BSP w PANSA — przed startem i po lądowaniu.",
                    color = OpsColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { openCustomTab(context, "https://checkin.pansa.pl/") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("checkin.pansa.pl")
                }
            }
        }

        // Info
        Text(
            "Uwaga: Aplikacja NexDrone nie zastępuje oficjalnego check-inu w PANSA.",
            style = MaterialTheme.typography.labelSmall,
            color = OpsColors.TextSecondary,
        )
    }
}

@Composable
private fun MapOption(
    title: String,
    subtitle: String,
    url: String,
    onClick: (String) -> Unit,
    onExternal: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanelRaised),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = OpsColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { onClick(url) }) { Text("Otwórz") }
        }
    }
}

private fun openCustomTab(context: android.content.Context, url: String) {
    runCatching {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        intent.launchUrl(context, Uri.parse(url))
    }.onFailure {
        openExternal(context, url)
    }
}

private fun openExternal(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
