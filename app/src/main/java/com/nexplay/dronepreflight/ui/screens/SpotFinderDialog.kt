package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.SavedLocation
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.SunLight
import com.nexplay.dronepreflight.ui.theme.OpsColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** AI wybiera najlepszą miejscówkę z zapisanych na podstawie pogody + światła + oceny użytkownika. */
@Composable
fun SpotFinderDialog(
    snap: AggregatedSnapshot,
    savedLocations: List<SavedLocation>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        val store = SettingsStore(context)
        val key = store.assistantGeminiKey.first()
        if (key.isBlank()) {
            answer = "Włącz Gemini w ustawieniach żeby uzyskać rekomendacje."
            loading = false
            return@LaunchedEffect
        }
        val sun = SunLight.compute(snap.latitude, snap.longitude)
        val prompt = buildString {
            appendLine("Zarekomenduj najlepszą miejscówkę do lotu drona spośród listy poniżej.")
            appendLine("Uwzględnij pogodę, kierunek wiatru, porę dnia i ocenę użytkownika. Max 3 zdania.")
            appendLine()
            appendLine("Warunki teraz:")
            snap.wind.median?.let { appendLine("- wiatr %.1f m/s".format(it)) }
            snap.gust.median?.let { appendLine("- porywy %.1f m/s".format(it)) }
            snap.visibility.median?.let { appendLine("- widoczność %.1f km".format(it/1000)) }
            sun.sunset?.let { appendLine("- zachód o $it") }
            sun.goldenHourEveningStart?.let { appendLine("- golden hour od $it") }
            appendLine()
            appendLine("Zapisane miejscówki (${savedLocations.size}):")
            savedLocations.forEach { loc ->
                append("- ${loc.name} (%.3f, %.3f)".format(loc.lat, loc.lon))
                if (loc.rating > 0) append(", ocena ${loc.rating}/5")
                if (loc.bestConditions.isNotBlank()) append(", ${loc.bestConditions}")
                if (loc.notes.isNotBlank()) append(" — ${loc.notes.take(50)}")
                appendLine()
            }
        }
        val r = com.nexplay.dronepreflight.copilot.JarvisChat.ask(
            apiKey = key,
            pilotName = store.pilotName.first(),
            userQuestion = prompt,
            personality = "luzny",
        )
        answer = r.getOrElse { "Błąd: ${it.message?.take(80)}" }
        // Wypowiedz
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
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "🎯 ZNAJDŹ MI MIEJSCÓWKĘ",
                    color = OpsColors.Accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (savedLocations.isEmpty()) {
                    Text(
                        "Nie masz zapisanych miejscówek. Dodaj kilka w Ustawieniach → Lokalizacje.",
                        color = OpsColors.TextPrimary,
                    )
                } else {
                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Analizuję ${savedLocations.size} miejsc…", color = OpsColors.TextSecondary)
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
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Zamknij")
                }
            }
        }
    }
}
