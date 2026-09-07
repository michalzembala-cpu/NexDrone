package com.nexplay.dronepreflight.copilot

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.nexplay.dronepreflight.assistant.VoiceIO
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.FlightLogEntry
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.SunLight
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.ui.HourlyOutlook
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * NexAI Operator — multi-turn chat z Jarvisem.
 * Otwiera się z auto-briefingiem, user może pytać dalej "dlaczego?", "a za godzinę?"
 */
@Composable
fun MicChatDialog(
    snap: AggregatedSnapshot?,
    assessment: FlightAssessment?,
    outlook: List<HourlyOutlook>,
    flightLog: List<FlightLogEntry> = emptyList(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Historia rozmowy
    val history = remember { mutableStateListOf<NexAIChat.Message>() }
    var isThinking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val voice = remember { VoiceIO(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }

    // System context — pełny stan apki, wysyłany raz jako systemInstruction extra
    val systemContext = remember(snap?.fetchedAt) { buildSystemContext(snap, assessment, outlook, flightLog) }

    fun ask(userQuestion: String) {
        if (isThinking) return
        history += NexAIChat.Message("user", userQuestion)
        isThinking = true
        statusText = "Myślę…"
        scope.launch {
            try {
                val store = SettingsStore(context)
                val key = store.assistantGeminiKey.first()
                if (key.isBlank()) {
                    history += NexAIChat.Message("model", "Włącz Gemini w Ustawieniach i wklej klucz.")
                    return@launch
                }
                val fullContext = systemContext + "\n\nImię pilota: ${store.pilotName.first()}"
                val r = NexAIChat.continueChat(key, fullContext, history.dropLast(1).toList(), userQuestion)
                val answer = r.getOrElse { "Błąd: ${it.message?.take(100)}" }
                history += NexAIChat.Message("model", answer)
                CopilotSpeaker.init(context)
                CopilotSpeaker.say(answer)
                statusText = null
                listState.animateScrollToItem(history.lastIndex)
            } finally {
                isThinking = false
            }
        }
    }

    fun listen() {
        if (isThinking || isListening) return
        isListening = true
        statusText = "Słucham…"
        scope.launch {
            try {
                val heard = voice.listen()
                if (heard.isBlank()) {
                    statusText = "Nic nie usłyszałem"
                } else {
                    ask(heard)
                }
            } catch (e: Exception) {
                statusText = "Mikrofon niedostępny"
            } finally {
                isListening = false
            }
        }
    }

    val micPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) listen()
        else statusText = "Brak pozwolenia na mikrofon"
    }

    fun requestMic() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) listen() else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Auto-briefing przy otwarciu — Jarvis wita i mówi status
    LaunchedEffect(Unit) {
        if (snap == null) return@LaunchedEffect
        ask("Powitaj mnie krótko i przedstaw obecną sytuację misji: werdykt, kluczowe warunki, najlepsze okno.")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
        ) {
            Column(Modifier.padding(16.dp).fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "NEXAI · CENTRUM DOWODZENIA",
                        color = OpsColors.Accent,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Zamknij") }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = OpsColors.Grid)
                Spacer(Modifier.height(8.dp))

                // Historia
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history) { msg ->
                        MessageBubble(msg)
                    }
                }

                statusText?.let {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isThinking || isListening) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(it, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Mic
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = { requestMic() },
                        enabled = !isThinking && !isListening,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) VerdictColors.NoGo else OpsColors.Accent,
                        ),
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Mów", modifier = Modifier.size(36.dp), tint = OpsColors.BgBase)
                    }
                }
                Text(
                    "Powiedz coś (dlaczego? za godzinę? jaki wiatr?)",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: NexAIChat.Message) {
    val isUser = msg.role == "user"
    val bg = if (isUser) OpsColors.BgPanelRaised else OpsColors.Accent.copy(alpha = 0.18f)
    val label = if (isUser) "TY" else "JARVIS"
    val labelColor = if (isUser) OpsColors.TextSecondary else OpsColors.Accent
    Column(
        Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(label, color = labelColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(msg.text, color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun buildSystemContext(
    snap: AggregatedSnapshot?,
    assessment: FlightAssessment?,
    outlook: List<HourlyOutlook>,
    flightLog: List<FlightLogEntry>,
): String = buildString {
    appendLine("--- KONTEKST APKI NEXDRONE (aktualne dane, nie zgaduj) ---")
    if (snap != null) {
        appendLine("Lokalizacja: ${snap.locationName}")
        snap.wind.median?.let { appendLine("Wiatr: %.1f m/s".format(it)) }
        snap.gust.median?.let { appendLine("Porywy: %.1f m/s".format(it)) }
        snap.temp.median?.let { appendLine("Temp: %.1f °C".format(it)) }
        snap.precip.median?.let { appendLine("Opady: %.1f mm/h".format(it)) }
        snap.visibility.median?.let { appendLine("Widoczność: %.1f km".format(it/1000)) }
        snap.kpIndex?.let { appendLine("KP: %.1f".format(it)) }
        appendLine("Źródła: ${snap.successfulSources}/${snap.totalSources}")

        val sun = SunLight.compute(snap.latitude, snap.longitude)
        sun.sunset?.let { appendLine("Zachód: $it") }
        sun.goldenHourEveningStart?.let { appendLine("Golden hour: od $it") }
    }
    if (assessment != null) {
        appendLine("Werdykt: ${when(assessment.overall) { Verdict.GO -> "GO"; Verdict.CAUTION -> "OSTROŻNIE"; Verdict.NO_GO -> "NO-GO" }}")
        assessment.checks.filter { it.verdict != Verdict.GO }.forEach {
            appendLine("- ${it.label}: ${it.value}")
        }
    }
    if (outlook.isNotEmpty()) {
        val next6 = outlook.take(6)
        appendLine("Prognoza wiatr 6h: " + next6.joinToString(", ") {
            "%02dh=%.1f".format(it.timeLocal.hour, it.windMs ?: 0.0)
        })
    }
    val recent = flightLog.take(3)
    if (recent.isNotEmpty()) {
        appendLine("Ostatnie loty:")
        recent.forEach {
            appendLine("- ${it.locationName}, ${it.durationMinutes}min, score ${it.score}")
        }
    }
    appendLine("--- KONIEC KONTEKSTU ---")
}
