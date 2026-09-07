package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.nexplay.dronepreflight.data.SystemCheck
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

@Composable
fun MissionControlCard(report: SystemCheck.Report) {
    val statusColor = when {
        report.allGood && report.readyCount == report.totalCount -> VerdictColors.Go
        report.allGood -> VerdictColors.Caution
        else -> VerdictColors.NoGo
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SYSTEM CHECK",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${report.readyCount}/${report.totalCount}",
                    color = statusColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "SYSTEMS READY",
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(10.dp))

            // 4x2 grid
            val rows = report.items.chunked(2)
            rows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    row.forEach { item ->
                        CheckRow(item, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CheckRow(item: SystemCheck.Item, modifier: Modifier) {
    val (icon, col) = when (item.status) {
        SystemCheck.Status.OK -> "🟢" to VerdictColors.Go
        SystemCheck.Status.WARN -> "🟡" to VerdictColors.Caution
        SystemCheck.Status.FAIL -> "🔴" to VerdictColors.NoGo
        SystemCheck.Status.UNKNOWN -> "⚪" to OpsColors.TextSecondary
    }
    Row(
        modifier
            .background(OpsColors.BgPanelRaised, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                color = OpsColors.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                item.detail,
                color = col,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
