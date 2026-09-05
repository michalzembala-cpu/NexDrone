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
import com.nexplay.dronepreflight.ui.screens.MapaScreen
import com.nexplay.dronepreflight.ui.screens.OnboardingScreen
import com.nexplay.dronepreflight.ui.screens.PulpitScreen
import com.nexplay.dronepreflight.ui.screens.SettingsScreen
import com.nexplay.dronepreflight.ui.theme.DronePreflightTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    Pulpit("Pulpit", Icons.Default.Dashboard),
    Kalendarz("Kalendarz", Icons.Default.CalendarMonth),
    Mapa("Mapa", Icons.Default.Map),
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

                // AI Co-pilot — konfiguracja Gemini TTS (bardziej naturalny głos)
                LaunchedEffect(Unit) {
                    val store = SettingsStore(applicationContext)
                    val useGemTts = store.useGeminiTts.first()
                    val key = store.assistantGeminiKey.first()
                    val voice = store.geminiTtsVoice.first()
                    CopilotSpeaker.init(applicationContext)
                    CopilotSpeaker.configureGemini(useGemTts, key, voice)
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
                    val personality = store.assistantPersonality.first()
                    val text = if (provider == "gemini") {
                        val key = store.assistantGeminiKey.first()
                        if (key.isBlank()) fallback()
                        else com.nexplay.dronepreflight.copilot.GeminiCopilot.briefing(
                            key, name, snap, assess, state.hourlyOutlook, state.units, personality,
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
                        onSaveFlight = { note, minutes -> vm.saveCurrentFlight(note, minutes) },
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
                            Tab.entries.forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = current == tab,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
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
