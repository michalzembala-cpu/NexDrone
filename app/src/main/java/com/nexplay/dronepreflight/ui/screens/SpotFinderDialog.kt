package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.NearbyPlacesFetcher
import com.nexplay.dronepreflight.data.SavedLocation
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.SunLight
import com.nexplay.dronepreflight.ui.theme.OpsColors
import kotlinx.coroutines.flow.first

/**
 * AI wybiera najlepszą miejscówkę:
 * - jeśli masz zapisane lokalizacje → wybiera spośród nich (ocena, wiatr, światło)
 * - jeśli brak zapisanych → pobiera pobliskie miasta/wioski (OSM Overpass) i rekomenduje z nich
 */
@Composable
fun SpotFinderDialog(
    snap: AggregatedSnapshot,
    savedLocations: List<SavedLocation>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Analizuję…") }
    var answer by remember { mutableStateOf<String?>(null) }
    var candidateCount by remember { mutableStateOf(savedLocations.size) }

    LaunchedEffect(Unit) {
        loading = true
        val store = SettingsStore(context)
        val key = store.assistantGroqKey.first()
        if (key.isBlank()) {
            answer = "Włącz Groq w Ustawieniach żeby uzyskać rekomendacje."
            loading = false
            return@LaunchedEffect
        }
        val sun = SunLight.compute(snap.latitude, snap.longitude)

        // Zbierz kandydatów: zapisane lub pobliskie miasta
        val candidatesText: String
        val header: String
        if (savedLocations.isNotEmpty()) {
            header = "Zapisane miejscówki (${savedLocations.size}):"
            candidatesText = buildString {
                savedLocations.forEach { loc ->
                    append("- ${loc.name} (%.3f, %.3f)".format(loc.lat, loc.lon))
                    if (loc.rating > 0) append(", ocena ${loc.rating}/5")
                    if (loc.bestConditions.isNotBlank()) append(", ${loc.bestConditions}")
                    if (loc.notes.isNotBlank()) append(" — ${loc.notes.take(50)}")
                    appendLine()
                }
            }
            candidateCount = savedLocations.size
        } else {
            status = "Szukam pobliskich miejscowości…"
            val nearby = NearbyPlacesFetcher.findNearby(snap.latitude, snap.longitude, radiusKm = 20.0, maxResults = 10)
            val places = nearby.getOrDefault(emptyList())
            if (places.isEmpty()) {
                answer = "Nie znalazłem miejscowości w pobliżu ani nie masz zapisanych. Dodaj miejscówki w Ustawieniach → Lokalizacje."
                loading = false
                return@LaunchedEffect
            }
            candidateCount = places.size
            header = "Pobliskie miejscowości (${places.size}, promień 20 km):"
            candidatesText = buildString {
                places.forEach { p ->
                    appendLine("- ${p.name} (%.1f km, %s)".format(p.distanceKm, p.kind))
                }
            }
        }

        status = "Rekomendacja AI…"
        val prompt = buildString {
            appendLine("Zarekomenduj najlepszą miejscówkę do lotu drona spośród listy poniżej.")
            appendLine("Uwzględnij pogodę, kierunek wiatru, porę dnia i odległość. Max 3 zdania.")
            appendLine()
            appendLine("Warunki teraz:")
            snap.wind.median?.let { appendLine("- wiatr %.1f m/s".format(it)) }
            snap.gust.median?.let { appendLine("- porywy %.1f m/s".format(it)) }
            snap.visibility.median?.let { appendLine("- widoczność %.1f km".format(it/1000)) }
            sun.sunset?.let { appendLine("- zachód o $it") }
            sun.goldenHourEveningStart?.let { appendLine("- golden hour od $it") }
            appendLine()
            appendLine(header)
            append(candidatesText)
        }
        val r = com.nexplay.dronepreflight.copilot.GroqChat.ask(
            apiKey = key,
            pilotName = store.pilotName.first(),
            userQuestion = prompt,
        )
        answer = r.getOrElse { "Błąd: ${it.message?.take(120)}" }
        com.nexplay.dronepreflight.copilot.CopilotSpeaker.init(context)
        answer?.let { com.nexplay.dronepreflight.copilot.CopilotSpeaker.say(it) }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "🎯 ZNAJDŹ MI MIEJSCÓWKĘ",
                    color = OpsColors.Accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(status, color = OpsColors.TextSecondary)
                    }
                }
                answer?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(OpsColors.BgPanelRaised, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(it, color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Zamknij")
                }
            }
        }
    }
}
