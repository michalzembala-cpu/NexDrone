package com.nexplay.dronepreflight.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.ui.PreflightViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class Line(val fromUser: Boolean, val text: String)

/**
 * Twarz asystenta. Zostaje w pełni obsługiwalny z klawiatury, nie tylko głosem: rozpoznawanie
 * mowy zależy od zgody na mikrofon i od tego, czy producent telefonu w ogóle je dowiózł, a
 * okno działające wyłącznie na głos jest w takim wypadku oknem martwym.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantSheet(vm: PreflightViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val settings = remember { SettingsStore(context.applicationContext) }
    val voice = remember { VoiceIO(context.applicationContext) }
    val brain = remember { AssistantBrain(vm, settings) }

    val lines = remember {
        mutableStateListOf(
            Line(
                false,
                "Powiedz albo napisz, np. „czy mogę lecieć”, „jaki jest wiatr”, " +
                    "„ile zostało na checkliście”, „odśwież”.",
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Silniki mowy trzymają zasoby systemowe — zamknięcie arkusza musi je oddać.
    DisposableEffect(Unit) {
        onDispose { voice.shutdown() }
    }

    fun handle(utterance: String, spoken: Boolean) {
        if (busy || utterance.isBlank()) return
        busy = true
        lines += Line(true, utterance)
        status = "Myślę…"
        scope.launch {
            val answer = brain.ask(utterance)
            lines += Line(false, answer)
            // Czytamy na głos tylko wtedy, gdy rozmowa zaczęła się głosem — inaczej asystent
            // odzywałby się do kogoś, kto właśnie cicho pisze.
            if (spoken && settings.assistantSpeak.first()) voice.speak(answer)
            status = if (brain.awaitingConfirmation) "Powiedz tak albo nie." else ""
            busy = false
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    fun listen() {
        if (busy) return
        busy = true
        status = "Słucham…"
        scope.launch {
            try {
                val heard = voice.listen()
                busy = false
                if (heard.isBlank()) status = "Nic nie usłyszałem." else handle(heard, spoken = true)
            } catch (e: VoiceUnavailableException) {
                lines += Line(false, e.message ?: "Mikrofon niedostępny.")
                status = ""
                busy = false
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) listen()
        else lines += Line(false, "Bez zgody na mikrofon mogę tylko czytać to, co wpiszesz.")
    }

    fun talk() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) listen() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "ASYSTENT",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(lines) { line ->
                    Text(
                        text = if (line.fromUser) "Ty: ${line.text}" else line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (line.fromUser) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (status.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(status, style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Napisz komendę…") },
                    singleLine = true,
                )
                FilledIconButton(
                    onClick = {
                        val text = input.trim()
                        input = ""
                        handle(text, spoken = false)
                    },
                    enabled = input.isNotBlank() && !busy,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Wyślij")
                }
                FilledIconButton(onClick = { talk() }, enabled = !busy) {
                    Icon(Icons.Default.Mic, contentDescription = "Mów")
                }
            }
        }
    }
}
