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
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.TempUnit
import com.nexplay.dronepreflight.data.WindUnit
import com.nexplay.dronepreflight.data.tempIn
import com.nexplay.dronepreflight.data.tempToC
import com.nexplay.dronepreflight.data.windIn
import com.nexplay.dronepreflight.data.windToMs
import com.nexplay.dronepreflight.copilot.CopilotSpeaker
import com.nexplay.dronepreflight.notify.TestNotifier
import com.nexplay.dronepreflight.update.GithubUpdateChecker
import kotlinx.coroutines.flow.first
import com.nexplay.dronepreflight.update.UpdateAvailableDialog
import com.nexplay.dronepreflight.ui.theme.DronePreflightTheme
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import android.provider.Settings as AndroidSettings
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

        // ── AI Co-pilot (Jarvis) ──
        val settingsStore = remember { SettingsStore(context) }
        val copilotOn by settingsStore.assistantEnabled.collectAsState(initial = false)
        val pilotNameFlow by settingsStore.pilotName.collectAsState(initial = "")
        var pilotNameField by remember(pilotNameFlow) { mutableStateOf(pilotNameFlow) }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI Co-pilot (głosowy)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Odzywa się tylko gdy jest coś ważnego — pre-flight brief, alerty, podsumowanie lotu.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = copilotOn,
                onCheckedChange = { on ->
                    scope.launch {
                        settingsStore.setAssistantEnabled(on)
                        if (on) CopilotSpeaker.init(context)
                    }
                },
            )
        }
        if (copilotOn) {
            OutlinedTextField(
                value = pilotNameField,
                onValueChange = { pilotNameField = it },
                label = { Text("Twoje imię (jak zwracać się do Ciebie)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch { settingsStore.setPilotName(pilotNameField) }
                }) { Text("Zapisz imię") }
                OutlinedButton(onClick = {
                    scope.launch {
                        CopilotSpeaker.init(context)
                        val name = settingsStore.pilotName.first()
                        val addr = if (name.isBlank()) "" else " $name"
                        CopilotSpeaker.say("Hej$addr, tu Jarvis. Warunki OK, można lecieć.")
                    }
                }) { Text("Test głosu") }
            }

            // Wybór głosu — sample każdego dostępnego polskiego głosu
            var voices by remember { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
            LaunchedEffect(Unit) {
                CopilotSpeaker.init(context)
                kotlinx.coroutines.delay(800)  // TTS potrzebuje chwili
                voices = CopilotSpeaker.listPolishVoices()
            }
            if (voices.isNotEmpty()) {
                Text(
                    "Głos (${voices.size} dostępne, kliknij aby posłuchać)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OpsColors.TextSecondary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    voices.forEach { voice ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = CopilotSpeaker.selectedVoiceName == voice.name,
                                onClick = {
                                    CopilotSpeaker.setVoice(voice.name)
                                    CopilotSpeaker.say("Testowy głos. Nazywam się Jarvis.")
                                },
                            )
                            Column(Modifier.weight(1f)) {
                                val quality = when {
                                    voice.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH -> "★★★"
                                    voice.quality >= android.speech.tts.Voice.QUALITY_HIGH -> "★★"
                                    else -> "★"
                                }
                                Text("$quality  ${voice.name.take(40)}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (voice.isNetworkConnectionRequired) "wymaga internetu" else "offline",
                                    color = OpsColors.TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
                Text(
                    "Wskazówka: najlepsze polskie głosy pobierzesz w Ustawienia Android → Język → Ustawienia zamiany tekstu na mowę → Google Speech Services → Zainstaluj dane głosowe.",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            // Gemini TTS — chmurowa synteza, dużo bardziej naturalna
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = OpsColors.Grid)
            val useGeminiTts by settingsStore.useGeminiTts.collectAsState(initial = false)
            val geminiTtsVoice by settingsStore.geminiTtsVoice.collectAsState(initial = "Kore")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Naturalny głos (Gemini TTS)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Chmurowa synteza — dużo bardziej ludzki głos niż Android. Używa Twojego klucza Gemini, wchodzi w free tier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpsColors.TextSecondary,
                    )
                }
                Switch(
                    checked = useGeminiTts,
                    onCheckedChange = { on ->
                        scope.launch {
                            settingsStore.setUseGeminiTts(on)
                            val key = settingsStore.assistantGeminiKey.first()
                            CopilotSpeaker.configureGemini(on, key, geminiTtsVoice)
                        }
                    },
                )
            }
            if (useGeminiTts) {
                Text(
                    "Głos Gemini (kliknij aby posłuchać)",
                    style = MaterialTheme.typography.labelSmall,
                    color = OpsColors.TextSecondary,
                )
                val geminiVoices = listOf(
                    "Kore" to "spokojny męski (rekomendowany)",
                    "Puck" to "energiczny męski",
                    "Charon" to "głęboki męski",
                    "Fenrir" to "władczy męski",
                    "Aoede" to "kobiecy przyjazny",
                    "Leda" to "kobiecy młody",
                    "Orus" to "męski zawadiacki",
                    "Zephyr" to "kobiecy spokojny",
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    geminiVoices.forEach { (id, desc) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = geminiTtsVoice == id,
                                onClick = {
                                    scope.launch {
                                        settingsStore.setGeminiTtsVoice(id)
                                        val key = settingsStore.assistantGeminiKey.first()
                                        CopilotSpeaker.configureGemini(true, key, id)
                                        // Sample
                                        CopilotSpeaker.say("Hej, tu Jarvis. Warunki OK, można lecieć.")
                                    }
                                },
                            )
                            Text("$id — $desc", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Hej Jarvis — wake word (foreground service)
            Spacer(Modifier.height(6.dp))
            val jarvisOn = remember { mutableStateOf(com.nexplay.dronepreflight.copilot.JarvisService.isRunning) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hej Jarvis (wake word)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Ciągłe nasłuchiwanie w tle. Nawet gdy apka zamknięta. UWAGA: bateria drenuje 2-3x szybciej.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpsColors.TextSecondary,
                    )
                }
                Switch(
                    checked = jarvisOn.value,
                    onCheckedChange = { on ->
                        jarvisOn.value = on
                        if (on) com.nexplay.dronepreflight.copilot.JarvisService.start(context)
                        else com.nexplay.dronepreflight.copilot.JarvisService.stop(context)
                    },
                )
            }

            // Osobowość Jarvisa
            Spacer(Modifier.height(6.dp))
            val personality by settingsStore.assistantPersonality.collectAsState(initial = "luzny")
            Text("Osobowość Jarvis'a", style = MaterialTheme.typography.titleSmall)
            val personalities = listOf(
                "luzny" to ("😎 Luźny" to "Kumpel, po imieniu, konkret + humor"),
                "pro" to ("🎯 Profesjonalny" to "Rzeczowy, formalny, bez żartów"),
                "motywator" to ("🔥 Motywator" to "Podkręca energię, wspiera"),
                "szyderca" to ("😂 Szyderca" to "Sarkastyczny, złośliwy, ale dba"),
                "mini" to ("🤖 Minimalny" to "Radio-operator, tylko fakty"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                personalities.forEach { (id, labels) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = personality == id,
                            onClick = { scope.launch { settingsStore.setAssistantPersonality(id) } },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(labels.first, style = MaterialTheme.typography.bodyMedium)
                            Text(labels.second, style = MaterialTheme.typography.labelSmall, color = OpsColors.TextSecondary)
                        }
                    }
                }
            }

            // Provider AI: rule-based (offline) / Gemini (darmowe od Google)
            Spacer(Modifier.height(6.dp))
            val provider by settingsStore.assistantProvider.collectAsState(initial = "rule")
            Text("Silnik odpowiedzi", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf("rule" to "Offline (bez konta)", "gemini" to "Gemini 2.0 (darmowe)")
                options.forEachIndexed { i, (id, label) ->
                    SegmentedButton(
                        selected = provider == id,
                        onClick = {
                            scope.launch { settingsStore.setAssistantProvider(id) }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }

            // Gemini key input
            if (provider == "gemini") {
                val geminiKeyFlow by settingsStore.assistantGeminiKey.collectAsState(initial = "")
                var geminiField by remember(geminiKeyFlow) { mutableStateOf(geminiKeyFlow) }
                var geminiStatus by remember { mutableStateOf<String?>(null) }
                Text(
                    "Gemini 2.0 Flash — DARMOWY (1500 requestów/dzień). Klucz: aistudio.google.com/app/apikey",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpsColors.TextSecondary,
                )
                OutlinedTextField(
                    value = geminiField,
                    onValueChange = { geminiField = it },
                    label = { Text("Klucz Gemini API (AIza...)") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch { settingsStore.setAssistantGeminiKey(geminiField) }
                    }) { Text("Zapisz klucz") }
                    OutlinedButton(onClick = {
                        geminiStatus = "Sprawdzam…"
                        scope.launch {
                            val key = settingsStore.assistantGeminiKey.first()
                            if (key.isBlank()) { geminiStatus = "✗ Wpisz klucz"; return@launch }
                            val r = com.nexplay.dronepreflight.copilot.GeminiCopilot.briefing(
                                apiKey = key,
                                pilotName = settingsStore.pilotName.first(),
                                snap = testSnapshot(),
                                assessment = testAssessment(),
                                outlook = emptyList(),
                                units = units,
                            )
                            geminiStatus = if (r.isSuccess) {
                                val text = r.getOrNull() ?: "?"
                                CopilotSpeaker.init(context)
                                CopilotSpeaker.say(text)
                                "✓ ${text.take(80)}…"
                            } else "✗ ${r.exceptionOrNull()?.message?.take(80)}"
                        }
                    }) { Text("Test Gemini") }
                }
                geminiStatus?.let {
                    Text(
                        it,
                        color = if (it.startsWith("✓")) VerdictColors.Go else VerdictColors.NoGo,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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

        // Status pozwolenia systemowego + test
        var testStatus by remember { mutableStateOf<String?>(null) }
        val hasNotifPerm = remember { TestNotifier.hasPermission(context) }
        if (!hasNotifPerm) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VerdictColors.NoGo.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "⚠ Powiadomienia zablokowane systemowo",
                        color = VerdictColors.NoGo,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "Android nie zezwala apce na wysyłanie powiadomień. Kliknij niżej, żeby włączyć w ustawieniach systemu.",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = {
                        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }) { Text("Otwórz ustawienia systemowe") }
                }
            }
        }
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = {
                testStatus = when (val r = TestNotifier.send(context)) {
                    TestNotifier.Result.Success -> "✓ Wysłane — sprawdź pasek powiadomień"
                    TestNotifier.Result.PermissionMissing -> "✗ Brak pozwolenia POST_NOTIFICATIONS"
                    TestNotifier.Result.ChannelDisabled -> "✗ Powiadomienia wyłączone w systemie"
                    is TestNotifier.Result.Error -> "✗ Błąd: ${r.message}"
                }
            }) { Text("Wyślij test") }
            testStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("✓")) VerdictColors.Go else VerdictColors.NoGo,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically),
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // Sekcja: asystent głosowy
        //
        // Dwa osobne przełączniki, bo to dwie osobne zgody. Pierwszy włącza lokalny zestaw
        // komend i mikrofon — nic nie opuszcza telefonu. Drugi dopuszcza model w chmurze i bez
        // własnego klucza nic nie robi.
        Text("Asystent głosowy", style = MaterialTheme.typography.titleMedium)
        Text(
            "Zapytaj głosem o warunki, wiatr, Kp albo checklistę — przydaje się, gdy ręce trzymają aparaturę. " +
                "Najczęstsze komendy działają offline, bez klucza i bez zasięgu.",
            style = MaterialTheme.typography.bodySmall,
            color = OpsColors.TextSecondary,
        )

        val assistantStore = remember { SettingsStore(context.applicationContext) }
        val assistantOn by assistantStore.assistantEnabled.collectAsState(initial = false)
        val assistantSpeak by assistantStore.assistantSpeak.collectAsState(initial = true)
        val assistantLlm by assistantStore.assistantUseLlm.collectAsState(initial = false)
        val assistantKey by assistantStore.assistantApiKey.collectAsState(initial = "")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Włącz asystenta (używa mikrofonu)", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = assistantOn,
                onCheckedChange = { scope.launch { assistantStore.setAssistantEnabled(it) } },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Czytaj odpowiedzi na głos", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = assistantSpeak,
                enabled = assistantOn,
                onCheckedChange = { scope.launch { assistantStore.setAssistantSpeak(it) } },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                "Rozumienie dowolnych pytań przez Claude",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = assistantLlm,
                enabled = assistantOn,
                onCheckedChange = { scope.launch { assistantStore.setAssistantUseLlm(it) } },
            )
        }

        if (assistantOn && assistantLlm) {
            var keyDraft by remember(assistantKey) { mutableStateOf(assistantKey) }
            OutlinedTextField(
                value = keyDraft,
                onValueChange = {
                    keyDraft = it
                    scope.launch { assistantStore.setAssistantApiKey(it.trim()) }
                },
                label = { Text("Klucz API Anthropic") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Twój własny klucz z console.anthropic.com. Wtedy pytanie i bieżące warunki lecą do API. " +
                    "Bez klucza działa tylko tryb offline.",
                style = MaterialTheme.typography.bodySmall,
                color = OpsColors.TextSecondary,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // Sekcja: aktualizacja
        Text("Aktualizacja apki", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ręcznie sprawdź czy jest nowa wersja na GitHub. Apka i tak sprawdza sama przy każdym uruchomieniu.",
            style = MaterialTheme.typography.bodySmall,
            color = OpsColors.TextSecondary,
        )
        var updateChecking by remember { mutableStateOf(false) }
        var updateInfo by remember { mutableStateOf<GithubUpdateChecker.UpdateInfo?>(null) }
        var updateMsg by remember { mutableStateOf<String?>(null) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            OutlinedButton(
                enabled = !updateChecking,
                onClick = {
                    updateChecking = true
                    updateMsg = null
                    scope.launch {
                        val res = GithubUpdateChecker.check(context)
                        updateChecking = false
                        when {
                            res.isFailure -> {
                                val ex = res.exceptionOrNull()
                                updateMsg = "✗ ${ex?.javaClass?.simpleName}: ${ex?.message?.take(120)}"
                            }
                            else -> {
                                val info = res.getOrNull()!!
                                if (info.hasUpdate && info.downloadUrl != null) updateInfo = info
                                else updateMsg = "✓ Masz najnowszą (${info.currentVersion})"
                            }
                        }
                    }
                },
            ) {
                if (updateChecking) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Sprawdzam…")
                } else {
                    Text("Sprawdź aktualizację")
                }
            }
            updateMsg?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("✓")) VerdictColors.Go else VerdictColors.NoGo,
                )
            }
        }
        updateInfo?.let { info ->
            UpdateAvailableDialog(info = info, onDismiss = { updateInfo = null })
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

// Testowy snapshot dla przycisków Test AI
private fun testSnapshot() = com.nexplay.dronepreflight.data.AggregatedSnapshot(
    locationName = "Test", latitude = 52.23, longitude = 21.01,
    fetchedAt = System.currentTimeMillis(), successfulSources = 5, totalSources = 5,
    readings = emptyList(), failures = emptyList(),
    wind = com.nexplay.dronepreflight.data.SourceStat(5.0, 4.5, 5.5, 0.3, 5),
    gust = com.nexplay.dronepreflight.data.SourceStat(7.0, 6.5, 7.5, 0.3, 5),
    windDir = com.nexplay.dronepreflight.data.SourceStat(270.0, 260.0, 280.0, 5.0, 5),
    temp = com.nexplay.dronepreflight.data.SourceStat(20.0, 19.0, 21.0, 0.5, 5),
    precip = com.nexplay.dronepreflight.data.SourceStat(0.0, 0.0, 0.0, 0.0, 5),
    cloud = com.nexplay.dronepreflight.data.SourceStat(30.0, 20.0, 40.0, 5.0, 5),
    visibility = com.nexplay.dronepreflight.data.SourceStat(15000.0, 12000.0, 20000.0, 2000.0, 5),
    stormVotes = 0, fogVotes = 0, kpIndex = 2.1,
    kpSource = "test", kpTotalSources = 5,
)

private fun testAssessment() = com.nexplay.dronepreflight.data.FlightAssessment(
    overall = com.nexplay.dronepreflight.data.Verdict.GO,
    checks = emptyList(),
)
