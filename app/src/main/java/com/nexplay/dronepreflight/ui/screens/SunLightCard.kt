package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.SunLight
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import java.time.LocalTime

@Composable
fun SunLightCard(snap: AggregatedSnapshot) {
    val times = remember(snap.latitude, snap.longitude, snap.fetchedAt) {
        SunLight.compute(snap.latitude, snap.longitude)
    }
    val now = remember { LocalTime.now() }

    // Jaki teraz status?
    val (statusIcon, statusText, statusColor) = currentPhase(now, times)

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SŁOŃCE I ŚWIATŁO",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                val h = times.dayLengthMin / 60
                val m = times.dayLengthMin % 60
                Text(
                    "$h h $m min dnia",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Current phase hero
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(statusIcon, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "TERAZ",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = OpsColors.Grid)
            Spacer(Modifier.height(10.dp))

            // Kluczowe momenty dnia
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                times.blueHourMorning?.let { LightRow("🌌", "Blue hour rano", format(it, times.sunrise ?: it), OpsColors.Accent) }
                times.sunrise?.let { LightRow("🌅", "Wschód", format(it, null), OpsColors.TextPrimary) }
                times.goldenHourMorningEnd?.let { LightRow("✨", "Golden hour rano", format(times.goldenHourMorningStart ?: it, it), Color(0xFFF59E0B)) }
                times.solarNoon?.let { LightRow("☀", "Południe", format(it, null), OpsColors.TextPrimary) }
                times.goldenHourEveningStart?.let { LightRow("✨", "Golden hour wieczorem", format(it, times.goldenHourEveningEnd ?: it), Color(0xFFF59E0B)) }
                times.sunset?.let { LightRow("🌇", "Zachód", format(it, null), OpsColors.TextPrimary) }
                times.blueHourEvening?.let { LightRow("🌌", "Blue hour wieczorem", format(times.sunset ?: it, it), OpsColors.Accent) }
            }
        }
    }
}

@Composable
private fun LightRow(icon: String, label: String, time: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = OpsColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            time,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun format(a: LocalTime, b: LocalTime?): String {
    val f = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    return if (b == null || b == a) a.format(f) else "${a.format(f)}–${b.format(f)}"
}

private fun currentPhase(now: LocalTime, t: SunLight.Times): Triple<String, String, Color> {
    val goldA1 = t.goldenHourMorningStart
    val goldA2 = t.goldenHourMorningEnd
    val goldB1 = t.goldenHourEveningStart
    val goldB2 = t.goldenHourEveningEnd
    val sunrise = t.sunrise
    val sunset = t.sunset
    val blueA = t.blueHourMorning
    val blueB = t.blueHourEvening

    return when {
        goldB1 != null && goldB2 != null && now in goldB1..goldB2 ->
            Triple("✨", "GOLDEN HOUR — nagrywaj TERAZ!", Color(0xFFF59E0B))
        goldA1 != null && goldA2 != null && now in goldA1..goldA2 ->
            Triple("✨", "GOLDEN HOUR — poranek", Color(0xFFF59E0B))
        blueB != null && sunset != null && now in sunset..blueB ->
            Triple("🌌", "BLUE HOUR — miękki niebieskawy odcień", OpsColors.Accent)
        blueA != null && sunrise != null && now in blueA..sunrise ->
            Triple("🌌", "BLUE HOUR — przed świtem", OpsColors.Accent)
        sunrise != null && sunset != null && now in sunrise..sunset ->
            Triple("☀", "DZIEŃ — pełne światło", VerdictColors.Go)
        else ->
            Triple("🌙", "NOC — mało światła", OpsColors.TextSecondary)
    }
}
