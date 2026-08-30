package com.nexplay.dronepreflight.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.FlightLogEntry
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import java.util.Calendar

@Composable
fun FlightStatsCard(entries: List<FlightLogEntry>) {
    if (entries.isEmpty()) return

    val totalMinutes = entries.mapNotNull { it.durationMinutes }.sum()
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60

    // Filtruj po miesiącu/roku
    val now = Calendar.getInstance()
    val thisMonth = now.get(Calendar.MONTH)
    val thisYear = now.get(Calendar.YEAR)
    val thisMonthCount = entries.count {
        val c = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        c.get(Calendar.MONTH) == thisMonth && c.get(Calendar.YEAR) == thisYear
    }
    val thisYearCount = entries.count {
        Calendar.getInstance().apply { timeInMillis = it.timestamp }
            .get(Calendar.YEAR) == thisYear
    }

    // Top 3 lokalizacje
    val topLocations = entries
        .groupingBy { it.locationName }
        .eachCount()
        .entries.sortedByDescending { it.value }
        .take(3)

    // Średnie
    val temps = entries.mapNotNull { it.tempC }
    val winds = entries.mapNotNull { it.windMs }
    val kps = entries.mapNotNull { it.kpIndex }

    // Bar chart — ostatnie 12 miesięcy
    val monthCounts = IntArray(12)
    entries.forEach { entry ->
        val entryCal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
        val monthsBack = (thisYear - entryCal.get(Calendar.YEAR)) * 12 +
                (thisMonth - entryCal.get(Calendar.MONTH))
        if (monthsBack in 0..11) monthCounts[11 - monthsBack]++
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "TWOJE STATYSTYKI",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(12.dp))

            // Rząd 1: łączny czas + liczba lotów
            Row {
                StatCell(
                    label = "ŁĄCZNIE",
                    value = if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                    sub = "${entries.size} lotów",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "TEN MIESIĄC",
                    value = thisMonthCount.toString(),
                    sub = "lotów",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "TEN ROK",
                    value = thisYearCount.toString(),
                    sub = "lotów",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = OpsColors.Grid)
            Spacer(Modifier.height(14.dp))

            // Rząd 2: średnie
            Row {
                StatCell(
                    label = "ŚR. TEMP",
                    value = temps.averageOrNull()?.let { "%.1f°C".format(it) } ?: "—",
                    sub = null,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "ŚR. WIATR",
                    value = winds.averageOrNull()?.let { "%.1f m/s".format(it) } ?: "—",
                    sub = null,
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = "ŚR. KP",
                    value = kps.averageOrNull()?.let { "%.1f".format(it) } ?: "—",
                    sub = null,
                    modifier = Modifier.weight(1f),
                )
            }

            if (topLocations.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = OpsColors.Grid)
                Spacer(Modifier.height(14.dp))

                Text(
                    "ULUBIONE MIEJSCA",
                    color = OpsColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                topLocations.forEachIndexed { i, (name, count) ->
                    Row(
                        Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${i + 1}.",
                            color = OpsColors.Accent,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            name,
                            color = OpsColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$count lotów",
                            color = OpsColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = OpsColors.Grid)
            Spacer(Modifier.height(14.dp))

            Text(
                "LOTY (OSTATNIE 12 MIESIĘCY)",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            MonthsBarChart(monthCounts)
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = OpsColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            value,
            color = OpsColors.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        sub?.let {
            Text(
                it,
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MonthsBarChart(counts: IntArray) {
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val monthLabels = listOf("s","l","m","k","m","c","l","s","w","p","l","g") // pierwsze litery
    val now = Calendar.getInstance()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val cellW = size.width / 12
        val chartH = size.height - 18f
        counts.forEachIndexed { i, c ->
            val monthOffset = 11 - i // 0 = najstarszy, 11 = teraz
            val monthNum = ((now.get(Calendar.MONTH) - (11 - i) + 12) % 12)
            val barH = (c.toFloat() / maxCount * chartH).coerceAtLeast(2f)
            val x = i * cellW + 2f
            val w = cellW - 4f
            drawRoundRect(
                color = if (c > 0) VerdictColors.Go else OpsColors.Grid.copy(alpha = 0.3f),
                topLeft = Offset(x, chartH - barH),
                size = Size(w, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
            if (c > 0) {
                val labelPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#E5EEF7")
                    textSize = 22f; isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    c.toString(),
                    x + w / 2f,
                    chartH - barH - 4f,
                    labelPaint,
                )
            }
            val monthPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#94A3B8")
                textSize = 20f; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                monthLabels[monthNum],
                x + w / 2f,
                size.height - 2f,
                monthPaint,
            )
        }
    }
}

private fun List<Double>.averageOrNull(): Double? =
    if (isEmpty()) null else average()
