package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.SunLight
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatTemp
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.ui.BestWindow
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import java.time.LocalTime

/** Podsumowanie ONE-SCREEN: status misji, pogoda, okno, AI. */
@Composable
fun CommandCenterCard(
    snap: AggregatedSnapshot,
    assessment: FlightAssessment,
    bestWindow: BestWindow?,
    units: DisplayUnits,
) {
    val verdictColor = when (assessment.overall) {
        Verdict.GO -> VerdictColors.Go
        Verdict.CAUTION -> VerdictColors.Caution
        Verdict.NO_GO -> VerdictColors.NoGo
    }
    val statusText = when (assessment.overall) {
        Verdict.GO -> "READY"
        Verdict.CAUTION -> "MARGINES"
        Verdict.NO_GO -> "HOLD"
    }

    val sun = remember(snap.latitude, snap.longitude) {
        SunLight.compute(snap.latitude, snap.longitude)
    }
    val now = LocalTime.now()
    val goldenSoon = sun.goldenHourEveningStart?.let {
        val minsTo = java.time.Duration.between(now, it).toMinutes()
        if (minsTo in 0..90) "GOLDEN za ${minsTo}min" else null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(2.dp, verdictColor.copy(alpha = 0.7f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Row 1: MISSION STATUS
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MISSION STATUS", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                goldenSoon?.let {
                    Box(
                        Modifier
                            .background(androidx.compose.ui.graphics.Color(0xFFF59E0B).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(it, color = androidx.compose.ui.graphics.Color(0xFFF59E0B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                statusText,
                color = verdictColor,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = OpsColors.Grid)
            Spacer(Modifier.height(10.dp))

            // Row 2: 4 col overview (weather + wind + window + airspace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcCell("🌡", formatTemp(snap.temp.median, units.temp), "TEMP", Modifier.weight(1f))
                CcCell(
                    "🌬",
                    snap.wind.median?.let { formatWind(it, units.wind) } ?: "—",
                    snap.gust.median?.let { "porywy " + formatWind(it, units.wind).take(6) } ?: "wiatr",
                    Modifier.weight(1f),
                )
                CcCell(
                    "🕐",
                    bestWindow?.let { "%02d:%02d".format(it.startLocal.hour, 0) } ?: "—",
                    bestWindow?.let { "okno ${it.hours}h" } ?: "brak okna",
                    Modifier.weight(1f),
                )
                CcCell(
                    "📡",
                    snap.kpIndex?.let { "%.1f".format(it) } ?: "—",
                    "KP",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CcCell(icon: String, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .background(OpsColors.BgPanelRaised, RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 18.sp)
        Text(value, color = OpsColors.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
