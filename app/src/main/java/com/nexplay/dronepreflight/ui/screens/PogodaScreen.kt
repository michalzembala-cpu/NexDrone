package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.ui.UiState
import com.nexplay.dronepreflight.ui.theme.OpsColors

/**
 * Zakładka POGODA — wszystko o warunkach w jednym miejscu.
 *
 * Wcześniej te karty były podekranami Pulpitu ("szczegóły" i "prognoza"), czyli dwa
 * kliknięcia w głąb. Pogoda to rzecz, do której wraca się najczęściej, więc dostaje
 * własną zakładkę zamiast kafelka na dole długiej kolumny.
 */
@Composable
fun PogodaScreen(state: UiState, units: DisplayUnits) {
    val snap = state.snapshot

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (snap == null) {
            Text(
                "Brak danych pogodowych. Odśwież na Pulpicie.",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        SectionLabel("TERAZ")
        WeatherMedianCard(snap, units)
        KpMedianCard(snap)

        // Jeśli któreś źródło odjeżdża od reszty, mówimy o tym tutaj, a nie na Pulpicie
        val anomalies = remember(snap.fetchedAt) {
            com.nexplay.dronepreflight.data.AnomalyDetector.detect(snap)
        }
        if (anomalies.isNotEmpty()) {
            AnomalyCard(anomalies)
        }

        if (state.hourlyOutlook.isNotEmpty()) {
            SectionLabel("NAJBLIŻSZE GODZINY")
            Next3HoursCard(state.hourlyOutlook, units)
            HourlyChartCard(state.hourlyOutlook, state.limits, units)
        }

        SectionLabel("SKĄD TE LICZBY")
        DataSourcesCard(snap, units)

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
