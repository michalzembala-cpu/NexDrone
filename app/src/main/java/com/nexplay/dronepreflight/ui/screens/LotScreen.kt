package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.SettingsStore
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.ui.AllChecklistIds
import com.nexplay.dronepreflight.ui.UiState
import com.nexplay.dronepreflight.ui.theme.OpsColors
import kotlinx.coroutines.launch

/**
 * Zakładka LOT — rzeczy, które robisz wokół samego lotu: typ misji, postęp checklisty,
 * monitoring burz, start i briefing w PDF.
 *
 * Wcześniej wszystko to wisiało na końcu Pulpitu, pod werdyktem i kafelkami, więc
 * trzeba było przewinąć pół ekranu, żeby wystartować lot.
 */
@Composable
fun LotScreen(
    state: UiState,
    units: DisplayUnits,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onStartFlight: () -> Unit,
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val currentMission by settingsStore.activeMission.collectAsState(initial = "general")
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionLabel("CO DZIŚ ROBISZ")
        MissionCard(
            selectedId = currentMission,
            onSelect = { id -> scope.launch { settingsStore.setActiveMission(id) } },
        )

        SectionLabel("PRZED STARTEM")
        ChecklistProgressCard(checkedCount = state.checked.intersect(AllChecklistIds).size)
        MonitoringCard(
            active = state.monitoringActive,
            onStart = onStartMonitor,
            onStop = onStopMonitor,
        )

        StartFlightButton(
            enabled = state.assessment?.overall != Verdict.NO_GO,
            onClick = onStartFlight,
        )
        BriefingButton(state, units)

        if (state.snapshot == null) {
            Text(
                "Bez danych pogodowych ocena startu jest niepełna — odśwież na Pulpicie.",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = OpsColors.TextSecondary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}
