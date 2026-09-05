package com.nexplay.dronepreflight.copilot

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.core.content.ContextCompat
import com.nexplay.dronepreflight.assistant.VoiceIO
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.ui.HourlyOutlook
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "MÓW MI" — klikasz mikrofon → pytasz → Gemini z pełnym kontekstem odpowiada głosem.
 * Bez wake-word, bez ciągłego nasłuchiwania, bez drenażu baterii.
 */
@Composable
fun MicChatDialog(
    snap: AggregatedSnapshot?,
    assessment: FlightAssessment?,
    outlook: List<HourlyOutlook>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Kliknij mikrofon i mów") }
    var transcript by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }

    val voice = remember { VoiceIO(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }

    fun processQuestion(question: String) {
        transcript = question
        thinking = true
        status = "Myślę…"
        scope.launch {
            try {
                val store = SettingsStore(context)
                val provider = store.assistantProvider.first()
                val key = store.assistantGeminiKey.first()
                val name = store.pilotName.first()

                if (provider != "gemini" || key.isBlank()) {
                    answer = "Włącz Gemini w ustawieniach i wklej klucz z aistudio.google.com."
                    CopilotSpeaker.init(context)
                    CopilotSpeaker.say(answer)
                    status = ""
                    return@launch
                }

                // Zbuduj bogaty kontekst — snap + verdict + hint co user pyta
                val context1 = buildString {
                    appendLine("Kontekst apki NexDrone (dane realne, nie zgaduj):")
                    if (snap != null) {
                        appendLine("Lokalizacja: ${snap.locationName}")
                        snap.wind.median?.let { appendLine("Wiatr: %.1f m/s".format(it)) }
                        snap.gust.median?.let { appendLine("Porywy: %.1f m/s".format(it)) }
                        snap.temp.median?.let { appendLine("Temp: %.1f °C".format(it)) }
                        snap.precip.median?.let { appendLine("Opady: %.1f mm/h".format(it)) }
                        snap.visibility.median?.let { appendLine("Widoczność: %.1f km".format(it / 1000)) }
                        snap.kpIndex?.let { appendLine("KP: %.1f".format(it)) }
                        if (snap.stormVotes > 0) appendLine("Burza w prognozie (${snap.stormVotes}/${snap.successfulSources} źródeł)")
                    }
                    if (assessment != null) {
                        appendLine("Werdykt: ${when(assessment.overall) { Verdict.GO -> "GO"; Verdict.CAUTION -> "OSTROŻNIE"; Verdict.NO_GO -> "NO-GO" }}")
                        val problems = assessment.checks.filter { it.verdict != Verdict.GO }
                        if (problems.isNotEmpty()) {
                            appendLine("Problemy:")
                            problems.forEach { appendLine("- ${it.label}: ${it.value}") }
                        }
                    }
                    if (outlook.isNotEmpty()) {
                        val next6 = outlook.take(6)
                        appendLine("Prognoza wiatr next 6h: " + next6.joinToString(", ") {
                            "%02d:00=%.1f".format(it.timeLocal.hour, it.windMs ?: 0.0)
                        })
                    }
                    appendLine()
                    appendLine("Pytanie pilota:")
                    append(question)
                }

                val r = JarvisChat.ask(apiKey = key, pilotName = name, userQuestion = context1)
                answer = r.getOrElse { "Błąd: ${it.message?.take(80)}" }
                CopilotSpeaker.init(context)
                CopilotSpeaker.say(answer)
                status = ""
            } finally {
                thinking = false
            }
        }
    }

    fun startListen() {
        listening = true
        status = "Słucham…"
        answer = ""
        scope.launch {
            try {
                val heard = voice.listen()
                listening = false
                if (heard.isBlank()) {
                    status = "Nic nie usłyszałem"
                } else {
                    processQuestion(heard)
                }
            } catch (e: Exception) {
                listening = false
                status = "Mikrofon niedostępny"
            }
        }
    }

    val micPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startListen()
        else status = "Bez pozwolenia na mikrofon nie mogę słuchać"
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
                    "MÓW DO JARVIS'A",
                    color = OpsColors.Accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )

                // Mic button
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FilledIconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startListen()
                            else micPerm.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        enabled = !listening && !thinking,
                        modifier = Modifier.size(80.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (listening) VerdictColors.NoGo else OpsColors.Accent,
                        ),
                    ) {
                        if (thinking) {
                            CircularProgressIndicator(
                                Modifier.size(28.dp), strokeWidth = 3.dp, color = OpsColors.BgBase,
                            )
                        } else {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Mów",
                                modifier = Modifier.size(40.dp),
                                tint = OpsColors.BgBase,
                            )
                        }
                    }
                }

                Text(
                    status,
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (transcript.isNotEmpty()) {
                    Text(
                        "Ty:",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        transcript,
                        color = OpsColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (answer.isNotEmpty()) {
                    Text(
                        "Jarvis:",
                        color = OpsColors.Accent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(OpsColors.BgPanelRaised, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            answer,
                            color = OpsColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Przykłady: „Czy mogę teraz latać?”, „Kiedy będzie najlepszy moment?”, „Dlaczego OSTROŻNIE?”, „Jaki mam wiatr?”",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Zamknij") }
            }
        }
    }
}
