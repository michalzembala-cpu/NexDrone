package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.ConfidenceCalculator
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.ui.BestWindow
import com.nexplay.dronepreflight.ui.HourlyOutlook
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

/**
 * Panel analizy lokacji — pojawia się gdy user postawi pinezkę na mapie.
 * Zwięzły command-center style: werdykt, pogoda, KP, best window, trend, ryzyko.
 */
@Composable
fun SpotIntelligencePanel(
    snap: AggregatedSnapshot,
    assessment: FlightAssessment,
    outlook: List<HourlyOutlook>,
    bestWindow: BestWindow?,
    units: DisplayUnits,
    pinnedCoords: Pair<Double, Double>,
    onDismiss: () -> Unit,
) {
    val confidence = ConfidenceCalculator.calculate(snap)
    val verdict = assessment.overall
    val verdictColor = when (verdict) {
        Verdict.GO -> VerdictColors.Go
        Verdict.CAUTION -> VerdictColors.Caution
        Verdict.NO_GO -> VerdictColors.NoGo
    }
    val verdictLabel = when (verdict) {
        Verdict.GO -> "GO"
        Verdict.CAUTION -> "OSTROŻNIE"
        Verdict.NO_GO -> "NO-GO"
    }

    // TREND — porównaj obecny wiatr z +3h prognozą
    val currentWind = snap.wind.median
    val futureWind = outlook.getOrNull(2)?.windMs
    val (trendArrow, trendLabel, trendColor) = when {
        currentWind == null || futureWind == null -> Triple("—", "brak danych", OpsColors.TextSecondary)
        futureWind - currentWind > 1.5 -> Triple("↗", "WIATR ROŚNIE", VerdictColors.Caution)
        currentWind - futureWind > 1.5 -> Triple("↘", "WIATR SŁABNIE", VerdictColors.Go)
        else -> Triple("→", "STABILNIE", OpsColors.Accent)
    }

    // RYZYKO — heurystyka z werdyktu, confidence i trendu
    val (riskLabel, riskColor) = when {
        verdict == Verdict.NO_GO -> "WYSOKIE" to VerdictColors.NoGo
        verdict == Verdict.CAUTION && confidence.percent < 60 -> "WYSOKIE" to VerdictColors.NoGo
        verdict == Verdict.CAUTION -> "ŚREDNIE" to VerdictColors.Caution
        confidence.percent < 60 -> "ŚREDNIE" to VerdictColors.Caution
        trendArrow == "↗" && verdict == Verdict.GO -> "ŚREDNIE" to VerdictColors.Caution
        else -> "NISKIE" to VerdictColors.Go
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = verdictColor)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "ANALIZA MIEJSCA",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "%.4f, %.4f".format(pinnedCoords.first, pinnedCoords.second),
                        color = OpsColors.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Usuń", color = OpsColors.TextSecondary) }
            }

            Spacer(Modifier.height(12.dp))

            // Werdykt hero
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(verdictColor.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(verdictLabel, color = verdictColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PEWNOŚĆ", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text("${confidence.percent}%  (${confidence.label})", color = OpsColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = OpsColors.Grid)
            Spacer(Modifier.height(10.dp))

            // Grid 2x2 z metrykami
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpotMetric(
                    "WIATR",
                    snap.wind.median?.let { formatWind(it, units.wind) } ?: "—",
                    Modifier.weight(1f),
                )
                SpotMetric(
                    "PORYWY",
                    snap.gust.median?.let { formatWind(it, units.wind) } ?: "—",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpotMetric(
                    "WIDOCZ.",
                    snap.visibility.median?.let {
                        if (it >= 10000) "%.0f km".format(it / 1000) else "%.1f km".format(it / 1000)
                    } ?: "—",
                    Modifier.weight(1f),
                )
                SpotMetric(
                    "KP",
                    snap.kpIndex?.let { "%.1f".format(it) } ?: "—",
                    Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = OpsColors.Grid)
            Spacer(Modifier.height(10.dp))

            // Best window
            SpotRow(
                label = "NAJLEPSZE OKNO",
                value = bestWindow?.let {
                    "%02d:00 – %02d:00 (%d h)".format(
                        it.startLocal.hour, (it.endLocal.hour + 1) % 24, it.hours,
                    )
                } ?: "brak okna ≥2h GO",
                color = if (bestWindow != null) VerdictColors.Go else OpsColors.TextSecondary,
            )

            Spacer(Modifier.height(8.dp))

            // Trend
            SpotRow(
                label = "TREND",
                value = "$trendArrow  $trendLabel",
                color = trendColor,
            )

            Spacer(Modifier.height(8.dp))

            // Ryzyko
            SpotRow(
                label = "RYZYKO",
                value = riskLabel,
                color = riskColor,
                bold = true,
            )
        }
    }
}

@Composable
private fun SpotMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(OpsColors.BgPanelRaised, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp),
    ) {
        Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = OpsColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpotRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = OpsColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(140.dp),
        )
        Text(
            value,
            color = color,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}
