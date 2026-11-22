@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.nexplay.dronepreflight

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexplay.dronepreflight.assistant.AssistantSheet
import com.nexplay.dronepreflight.copilot.AiCopilot
import com.nexplay.dronepreflight.copilot.CopilotSpeaker
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.monitor.StormMonitorService
import com.nexplay.dronepreflight.notify.GoWindowWorker
import com.nexplay.dronepreflight.update.GithubUpdateChecker
import com.nexplay.dronepreflight.update.UpdateAvailableDialog
import com.nexplay.dronepreflight.ui.PreflightViewModel
import com.nexplay.dronepreflight.ui.screens.ChecklistScreen
import com.nexplay.dronepreflight.ui.screens.DashboardScreen
import com.nexplay.dronepreflight.ui.screens.FlightModeScreen
import com.nexplay.dronepreflight.ui.screens.HistoryScreen
import com.nexplay.dronepreflight.ui.screens.LotScreen
import com.nexplay.dronepreflight.ui.screens.PogodaScreen
import com.nexplay.dronepreflight.ui.screens.MapaScreen
import com.nexplay.dronepreflight.ui.screens.OnboardingScreen
import com.nexplay.dronepreflight.ui.screens.PulpitScreen
import com.nexplay.dronepreflight.ui.screens.SettingsScreen
import com.nexplay.dronepreflight.ui.theme.DronePreflightTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Zakładki aplikacji. [wPasku] decyduje, czy ikona siedzi na dolnym pasku, czy
 * w siatce pod przyciskiem "Więcej".
 *
 * Sześć ikon nie mieściło się na szerokości telefonu — etykiety łamały się na dwie
 * linie ("Kalend arz", "Checkl ista"). Na pasku zostają cztery najczęstsze plus
 * "Więcej"; reszta nie znika, tylko przenosi się o jedno dotknięcie dalej.
 */
private enum class Tab(val label: String, val icon: ImageVector, val wPasku: Boolean = false) {
    Pulpit("Pulpit", Icons.Default.Dashboard, true),
    Pogoda("Pogoda", Icons.Default.Cloud, true),
    Mapa("Mapa", Icons.Default.Map, true),
    Lot("Lot", Icons.Default.FlightTakeoff, true),
    Kalendarz("Kalendarz", Icons.Default.CalendarMonth),
    Historia("Historia", Icons.Default.History),
    Checklist("Checklista", Icons.Default.Checklist),
    Settings("Ustawienia", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val vm: PreflightViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DronePreflightTheme {
                val state by vm.state.collectAsState()

                if (!state.onboardingSeen) {
                    OnboardingScreen(onDone = { vm.markOnboardingDone() })
                    return@DronePreflightTheme
                }

                // Check for updates on app start (once per session).
                // Throttle: sprawdzaj max raz na godzinę.
                // Dismiss-memory: jeśli user odrzucił konkretną wersję → nie pokazuj więcej.
                var updateInfo by remember { mutableStateOf<GithubUpdateChecker.UpdateInfo?>(null) }
                var updateChecked by rememberSaveable { mutableStateOf(false) }
                val scopeUpdate = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    if (updateChecked) return@LaunchedEffect
                    updateChecked = true
                    val settings = SettingsStore(applicationContext)
                    val lastCheck = settings.lastUpdateCheckAt.first()
                    val now = System.currentTimeMillis()
                    val throttleMs = 60 * 60 * 1000L  // 1h
                    if (now - lastCheck < throttleMs) return@LaunchedEffect

                    val res = GithubUpdateChecker.check(applicationContext)
                    val info = res.getOrNull() ?: return@LaunchedEffect
                    settings.setLastUpdateCheckAt(now)

                    val dismissed = settings.dismissedUpdateVersion.first()
                    if (info.hasUpdate && info.downloadUrl != null && info.latestVersion != dismissed) {
                        updateInfo = info
                    }
                }
                updateInfo?.let { info ->
                    UpdateAvailableDialog(
                        info = info,
                        onDismiss = { updateInfo = null },
                        onDismissForever = {
                            scopeUpdate.launch {
                                SettingsStore(applicationContext).setDismissedUpdateVersion(info.latestVersion)
                            }
                            updateInfo = null
                        },
                    )
                }

                // Auto-schedule GO window worker on startup if enabled — naprawia
                // sytuację, gdy worker padł po reinstalce apki albo restart telefonu.
                LaunchedEffect(state.notificationsEnabled) {
                    if (state.notificationsEnabled) {
                        GoWindowWorker.schedule(applicationContext)
                    }
                }

                // AI Co-pilot — inicjalizacja TTS (Android systemowy)
                LaunchedEffect(Unit) {
                    CopilotSpeaker.init(applicationContext)
                }

                // AI Co-pilot — pre-flight briefing gdy załadował się świeży snapshot
                LaunchedEffect(state.snapshot?.fetchedAt) {
                    val snap = state.snapshot ?: return@LaunchedEffect
                    val assess = state.assessment ?: return@LaunchedEffect
                    val store = SettingsStore(applicationContext)
                    if (!store.assistantEnabled.first()) return@LaunchedEffect
                    CopilotSpeaker.init(applicationContext)
                    val name = store.pilotName.first()
                    val provider = store.assistantProvider.first()
                    val fallback = { AiCopilot.preFlightBriefing(name, snap, assess, state.hourlyOutlook, state.units).text }
                    val mission = store.activeMission.first()
                    val text = if (provider == "groq") {
                        val key = store.assistantGroqKey.first()
                        if (key.isBlank()) fallback()
                        else com.nexplay.dronepreflight.copilot.GroqChat.briefing(
                            key, name, snap, assess, state.hourlyOutlook, state.units, mission,
                        ).getOrElse { fallback() }
                    } else fallback()
                    CopilotSpeaker.say(text)
                }

                var flightModeActive by rememberSaveable { mutableStateOf(false) }
                if (flightModeActive) {
                    FlightModeScreen(
                        state = state,
                        units = state.units,
                        onDismiss = { flightModeActive = false },
                        onStartMonitor = { StormMonitorService.start(applicationContext) },
                        onStopMonitor = { StormMonitorService.stop(applicationContext) },
                        onRefresh = { vm.refresh() },
                        onSaveFlight = { note, minutes, goPct -> vm.saveCurrentFlight(note, minutes, goPct) },
                    )
                }

                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { Tab.entries.size },
                )
                val scope = rememberCoroutineScope()
                val current = Tab.entries[pagerState.currentPage]

                // Asystent jest opt-in: bierze mikrofon, więc dopóki nie jest włączony
                // w Ustawieniach, nie ma nawet przycisku, który by go otwierał.
                val assistantSettings = remember { SettingsStore(applicationContext) }
                val assistantEnabled by assistantSettings.assistantEnabled.collectAsState(initial = false)
                var assistantOpen by rememberSaveable { mutableStateOf(false) }
                if (assistantOpen) {
                    AssistantSheet(vm = vm, onDismiss = { assistantOpen = false })
                }

                // Siatka ekranów spoza dolnego paska
                var pokazWiecej by rememberSaveable { mutableStateOf(false) }
                if (pokazWiecej) {
                    WiecejSheet(
                        pozycje = Tab.entries.filter { !it.wPasku },
                        onWybor = { tab ->
                            pokazWiecej = false
                            scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                        },
                        onDismiss = { pokazWiecej = false },
                    )
                }

                LaunchedEffect(Unit) {
                    val perms = mutableListOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.RECORD_AUDIO,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms += Manifest.permission.POST_NOTIFICATIONS
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column {
                                    Text("NexDrone", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "CENTRUM DOWODZENIA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.filter { it.wPasku }.forEach { tab ->
                                NavigationBarItem(
                                    selected = current == tab,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                            // "Więcej" podświetla się, gdy jesteś na którymkolwiek
                            // z ekranów schowanych w siatce
                            NavigationBarItem(
                                selected = !current.wPasku,
                                onClick = { pokazWiecej = true },
                                icon = { Icon(Icons.Default.Apps, contentDescription = "Więcej") },
                                label = { Text("Więcej") },
                            )
                        }
                    },
                    floatingActionButton = {
                        if (assistantEnabled) {
                            FloatingActionButton(onClick = { assistantOpen = true }) {
                                Icon(Icons.Default.Mic, contentDescription = "Asystent")
                            }
                        }
                    },
                ) { inner: PaddingValues ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize().padding(inner),
                    ) { page ->
                        when (Tab.entries[page]) {
                            Tab.Pulpit -> PulpitScreen(
                                state = state,
                                onRefresh = { vm.refresh() },
                                onDateSelected = vm::setSelectedDate,
                                onStartMonitor = { StormMonitorService.start(applicationContext) },
                                onStopMonitor = { StormMonitorService.stop(applicationContext) },
                                onStartFlight = { flightModeActive = true },
                                units = state.units,
                            )
                            Tab.Pogoda -> PogodaScreen(
                                state = state,
                                units = state.units,
                            )
                            Tab.Lot -> LotScreen(
                                state = state,
                                units = state.units,
                                onStartMonitor = { StormMonitorService.start(applicationContext) },
                                onStopMonitor = { StormMonitorService.stop(applicationContext) },
                                onStartFlight = { flightModeActive = true },
                            )
                            Tab.Kalendarz -> DashboardScreen(
                                state = state,
                                onRefresh = { vm.refresh() },
                                onDateSelected = vm::setSelectedDate,
                                onHourSelected = vm::toggleTile,
                                units = state.units,
                            )
                            Tab.Mapa -> MapaScreen(
                                snap = state.snapshot,
                                assessment = state.assessment,
                                limits = state.limits,
                                hourlyOutlook = state.hourlyOutlook,
                                bestWindow = state.bestWindow,
                                units = state.units,
                                savedLocations = state.savedLocations,
                                pinnedCoords = state.pinnedCoords,
                                onPin = { lat, lon -> vm.setPinnedLocation(lat, lon) },
                                onClearPin = { vm.clearPinnedLocation() },
                            )
                            Tab.Historia -> HistoryScreen(
                                state = state,
                                onSaveCurrent = { note, minutes ->
                                    vm.saveCurrentFlight(note, minutes)
                                },
                                onDelete = vm::deleteFlight,
                                onUpdateNote = vm::updateFlightNote,
                                units = state.units,
                            )
                            Tab.Checklist -> ChecklistScreen(
                                checked = state.checked,
                                onToggle = vm::toggleChecklistItem,
                                onReset = vm::resetChecklist,
                            )
                            Tab.Settings -> SettingsScreen(
                                limits = state.limits,
                                onSave = vm::saveLimits,
                                locations = state.savedLocations,
                                activeLocationId = state.activeLocationId,
                                onAddLocation = vm::saveLocation,
                                onSetActiveLocation = vm::setActiveLocation,
                                onDeleteLocation = vm::deleteLocation,
                                notificationsEnabled = state.notificationsEnabled,
                                onToggleNotifications = { on ->
                                    vm.setNotificationsEnabled(on)
                                    if (on) GoWindowWorker.schedule(applicationContext)
                                    else GoWindowWorker.cancel(applicationContext)
                                },
                                droneProfiles = state.droneProfiles,
                                activeProfileId = state.activeProfileId,
                                onSaveProfile = vm::saveProfile,
                                onDeleteProfile = vm::deleteProfile,
                                onSetActiveProfile = vm::setActiveProfile,
                                units = state.units,
                                onSetWindUnit = vm::setWindUnit,
                                onSetTempUnit = vm::setTempUnit,
                                onExportBackup = { vm.exportBackup() },
                                onImportBackup = { uri -> vm.importBackup(uri) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Siatka ekranów ukrytych pod "Więcej".
 *
 * Nie chowamy tu niczego na stałe — to te same strony pagera, tylko bez własnej
 * ikony na pasku. Dzięki temu pasek zostaje czytelny, a ekranów może być więcej.
 */
@Composable
private fun WiecejSheet(
    pozycje: List<Tab>,
    onWybor: (Tab) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Zamknij") }
        },
        title = { Text("Więcej") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pozycje.chunked(2).forEach { para ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        para.forEach { tab ->
                            WiecejKafelek(tab, Modifier.weight(1f)) { onWybor(tab) }
                        }
                        if (para.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
    )
}

@Composable
private fun WiecejKafelek(tab: Tab, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(26.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
