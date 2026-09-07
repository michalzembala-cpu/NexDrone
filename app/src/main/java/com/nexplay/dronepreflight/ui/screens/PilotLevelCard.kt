package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.data.Badge
import com.nexplay.dronepreflight.data.FlightLogEntry
import com.nexplay.dronepreflight.data.PilotStats
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

@Composable
fun PilotLevelCard(entries: List<FlightLogEntry>) {
    if (entries.isEmpty()) return
    val stats = PilotStats.compute(entries)

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "PROFIL PILOTA",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(10.dp))

            // Level hero
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PILOT", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(
                        "LEVEL ${stats.level}",
                        color = OpsColors.Accent,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${stats.xp} / ${stats.xpNext} XP do lvl ${stats.level + 1}",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("BADGES", color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${stats.badges.count { it.earned }}/${stats.badges.size}",
                        color = OpsColors.TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // XP progress bar
            val progress = if (stats.xpNext > 0) stats.xp.toFloat() / stats.xpNext else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = OpsColors.Accent,
                trackColor = OpsColors.Grid,
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = OpsColors.Grid)
            Spacer(Modifier.height(10.dp))

            // Statistics summary
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("🎮", "${stats.totalFlights}", "lotów", Modifier.weight(1f))
                MiniStat("⏱", formatMinutes(stats.totalMinutes), "w powietrzu", Modifier.weight(1f))
                MiniStat("⭐", "${stats.perfectFlights}", "perfect", Modifier.weight(1f))
                MiniStat("🗺", "${stats.uniqueLocations}", "miejsc", Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "ODZNAKI",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            // Grid 3 columns of badges
            val chunks = stats.badges.chunked(3)
            chunks.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    row.forEach { b ->
                        BadgeChip(b, Modifier.weight(1f))
                    }
                    // Wypełnij pustymi kolumnami jeśli niepełny rząd
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(icon: String, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .background(OpsColors.BgPanelRaised, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 16.sp)
        Text(value, color = OpsColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BadgeChip(badge: Badge, modifier: Modifier) {
    val bg = if (badge.earned) VerdictColors.Go.copy(alpha = 0.18f) else OpsColors.BgPanelRaised
    val alpha = if (badge.earned) 1f else 0.3f
    Column(
        modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(badge.icon, fontSize = 22.sp, modifier = Modifier.alpha(alpha))
        Spacer(Modifier.height(2.dp))
        Text(
            badge.name,
            color = if (badge.earned) OpsColors.TextPrimary else OpsColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(alpha),
        )
    }
}

private fun formatMinutes(min: Int): String {
    val h = min / 60
    val m = min % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

