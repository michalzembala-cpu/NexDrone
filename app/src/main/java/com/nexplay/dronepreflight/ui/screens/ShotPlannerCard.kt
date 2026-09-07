package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.ShotPlanner
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

@Composable
fun ShotPlannerCard(snap: AggregatedSnapshot) {
    val shots = remember(snap.fetchedAt) { ShotPlanner.score(snap) }
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) shots else shots.take(3)

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SHOT PLANNER · CO NAKRĘCIĆ",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (expanded) "▲" else "▼",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.height(10.dp))

            visible.forEach { s ->
                ShotRow(s)
                Spacer(Modifier.height(6.dp))
            }

            if (!expanded && shots.size > 3) {
                Text(
                    "Klik po całą listę (${shots.size - 3} więcej)",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ShotRow(s: ShotPlanner.ShotScore) {
    val col = when (s.verdict) {
        Verdict.GO -> VerdictColors.Go
        Verdict.CAUTION -> VerdictColors.Caution
        Verdict.NO_GO -> VerdictColors.NoGo
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(OpsColors.BgPanelRaised, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.shot.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.shot.name,
                    color = OpsColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${s.score}/100",
                    color = col,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                s.reason,
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
