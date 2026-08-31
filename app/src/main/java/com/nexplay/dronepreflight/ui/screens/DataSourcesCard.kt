package com.nexplay.dronepreflight.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.ConfidenceCalculator
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.SourceFailure
import com.nexplay.dronepreflight.data.formatTemp
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.data.sources.SourceReading
import com.nexplay.dronepreflight.data.sources.kp.KpReading
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

@Composable
fun DataSourcesCard(snap: AggregatedSnapshot, units: DisplayUnits) {
    var expanded by remember { mutableStateOf(false) }
    val confidence = remember(snap.fetchedAt, snap.successfulSources) {
        ConfidenceCalculator.calculate(snap)
    }
    val confColor = when {
        confidence.percent >= 85 -> VerdictColors.Go
        confidence.percent >= 60 -> VerdictColors.Caution
        else -> VerdictColors.NoGo
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp),
        ) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ŹRÓDŁA DANYCH",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = OpsColors.TextSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))

            // Summary metrics
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill(
                    "POGODA",
                    "${snap.successfulSources}/${snap.totalSources}",
                    colorForCoverage(snap.successfulSources, snap.totalSources),
                    Modifier.weight(1f),
                )
                MetricPill(
                    "KP",
                    "${snap.kpReadings.size}/${snap.kpTotalSources}",
                    colorForCoverage(snap.kpReadings.size, snap.kpTotalSources),
                    Modifier.weight(1f),
                )
                MetricPill(
                    "PEWNOŚĆ",
                    "${confidence.percent}%",
                    confColor,
                    Modifier.weight(1f),
                )
            }

            // Expanded details
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 14.dp)) {
                    SectionHeader("POGODA")
                    snap.readings.forEach { WeatherSourceLine(it, units) }
                    snap.failures.forEach { FailureLine(it) }

                    if (snap.kpReadings.isNotEmpty() || snap.kpFailures.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SectionHeader("KP INDEX")
                        snap.kpReadings.forEach { KpSourceLine(it) }
                        snap.kpFailures.forEach { FailureLine(it) }
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = OpsColors.Grid)
                    Spacer(Modifier.height(10.dp))
                    Text("SZCZEGÓŁY POMIARU", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    confidence.details.forEach {
                        Text("· $it", color = OpsColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        color = OpsColors.Accent,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun WeatherSourceLine(r: SourceReading, units: DisplayUnits) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(VerdictColors.Go)
        Spacer(Modifier.width(8.dp))
        Text(
            shortName(r.source),
            color = OpsColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            buildString {
                append(r.windMs?.let { formatWind(it, units.wind) } ?: "—")
                r.tempC?.let { append(" · "); append(formatTemp(it, units.temp)) }
            },
            color = OpsColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun KpSourceLine(r: KpReading) {
    val color = when {
        r.value < 4 -> VerdictColors.Go
        r.value < 5 -> VerdictColors.Caution
        else -> VerdictColors.NoGo
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color)
        Spacer(Modifier.width(8.dp))
        Text(
            shortName(r.source),
            color = OpsColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            "%.1f".format(r.value).replace('.', ','),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FailureLine(f: SourceFailure) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(VerdictColors.NoGo)
        Spacer(Modifier.width(8.dp))
        Text(
            shortName(f.source),
            color = OpsColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            "błąd: " + f.reason.take(28),
            color = VerdictColors.NoGo,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(9.dp).background(color, RoundedCornerShape(5.dp)))
}

private fun colorForCoverage(count: Int, total: Int): Color {
    if (total == 0) return OpsColors.TextSecondary
    val frac = count.toDouble() / total
    return when {
        frac >= 0.8 -> VerdictColors.Go
        frac >= 0.5 -> VerdictColors.Caution
        else -> VerdictColors.NoGo
    }
}

private fun shortName(s: String): String = when {
    s.startsWith("Open-Meteo") -> "Open-Meteo"
    s.startsWith("MET") -> "MET Norway"
    s.startsWith("Bright Sky") -> "Bright Sky"
    s.startsWith("7Timer") -> "7Timer!"
    s.startsWith("wttr") -> "wttr.in"
    s.startsWith("NOAA SWPC (planetary)") -> "NOAA planetary"
    s.startsWith("NOAA SWPC (forecast)") -> "NOAA forecast"
    s.startsWith("NOAA SWPC (1-min") -> "NOAA 1-min"
    s.startsWith("NOAA SWPC (G-scale)") -> "NOAA G-scale"
    s.startsWith("GFZ") -> "GFZ Potsdam"
    else -> s.take(24)
}
