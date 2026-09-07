package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

data class Mission(
    val id: String,
    val emoji: String,
    val name: String,
    val hint: String,
)

val Missions = listOf(
    Mission("film", "🎥", "FILMOWANIE", "Priorytet: światło, wiatr boczny, płynność"),
    Mission("photo", "📸", "ZDJĘCIA", "Priorytet: światło, niska wilgotność"),
    Mission("recon", "🗺", "REKONESANS", "Priorytet: widoczność, KP index (GPS)"),
    Mission("landscape", "🏞", "KRAJOBRAZ", "Priorytet: przejrzystość, słaby wiatr"),
    Mission("general", "✈", "ZWYKŁY LOT", "Priorytet: wszystkie parametry ogólnie"),
)

fun missionById(id: String): Mission = Missions.firstOrNull { it.id == id } ?: Missions.last()

@Composable
fun MissionCard(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "MISJA",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Missions.forEach { m ->
                    val selected = m.id == selectedId
                    MissionTile(
                        mission = m,
                        selected = selected,
                        onClick = { onSelect(m.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val current = missionById(selectedId)
            Text(
                current.hint,
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MissionTile(
    mission: Mission,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val bg = if (selected) VerdictColors.Go.copy(alpha = 0.20f) else OpsColors.BgPanelRaised
    val border = if (selected) VerdictColors.Go else Color.Transparent
    Column(
        modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(mission.emoji, fontSize = 22.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            mission.name,
            color = if (selected) VerdictColors.Go else OpsColors.TextPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

