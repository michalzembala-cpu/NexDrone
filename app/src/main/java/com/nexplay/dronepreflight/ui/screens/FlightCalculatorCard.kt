package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.nexplay.dronepreflight.data.FlightCalculator
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors

@Composable
fun FlightCalculatorCard(snap: AggregatedSnapshot) {
    var distance by remember { mutableStateOf("1.0") }
    var battery by remember { mutableStateOf("100") }
    var maxSpeed by remember { mutableStateOf("16") }
    var flightTime by remember { mutableStateOf("30") }

    val distanceKm = distance.replace(',', '.').toDoubleOrNull() ?: 1.0
    val batteryPct = battery.toDoubleOrNull() ?: 100.0
    val maxSpeedMs = maxSpeed.replace(',', '.').toDoubleOrNull() ?: 16.0
    val flightTimeMin = flightTime.replace(',', '.').toDoubleOrNull() ?: 30.0
    val windMs = snap.wind.median ?: 0.0

    val result = remember(distanceKm, batteryPct, maxSpeedMs, flightTimeMin, windMs) {
        FlightCalculator.compute(
            distanceKm = distanceKm,
            windMs = windMs,
            droneMaxSpeedMs = maxSpeedMs,
            flightTimeMin = flightTimeMin,
            batteryPct = batteryPct,
        )
    }

    val verdictColor = when (result.verdict) {
        "OK" -> VerdictColors.Go
        "MARGINES" -> VerdictColors.Caution
        else -> VerdictColors.NoGo
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "KALKULATOR LOTU · CZY DAM RADĘ WRÓCIĆ?",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            // Inputs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = distance,
                    onValueChange = { distance = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Dystans (km)", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = battery,
                    onValueChange = { battery = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Bateria %", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = maxSpeed,
                    onValueChange = { maxSpeed = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Max m/s drona", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = flightTime,
                    onValueChange = { flightTime = it.filter { c -> c.isDigit() } },
                    label = { Text("Czas lotu (min)", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Result hero
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        result.verdict,
                        color = verdictColor,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        result.reasoning,
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Details
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniCalcCell("→", "%.1f min".format(result.outboundMin), "tam", Modifier.weight(1f))
                MiniCalcCell("←", "%.1f min".format(result.returnMin), "powrót", Modifier.weight(1f))
                MiniCalcCell("🔋", "%.0f%%".format(result.batteryUsedPct), "zużyte", Modifier.weight(1f))
                MiniCalcCell("💚", "%.0f%%".format(result.batteryReserve), "rezerwa", Modifier.weight(1f))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Wiatr obecny: %.1f m/s (użyty w kalkulacji)".format(windMs),
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MiniCalcCell(icon: String, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .background(OpsColors.BgPanelRaised, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 14.sp)
        Text(value, color = OpsColors.TextPrimary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(label, color = OpsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
