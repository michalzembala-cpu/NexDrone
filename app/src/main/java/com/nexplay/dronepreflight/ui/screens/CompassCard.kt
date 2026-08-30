package com.nexplay.dronepreflight.ui.screens

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexplay.dronepreflight.data.windDirectionCardinal
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassCard(windDirectionDeg: Double?) {
    val ctx = LocalContext.current
    var azimuth by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                val rot = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rot, orientation)
                var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (az < 0) az += 360f
                azimuth = az
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "KOMPAS",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (windDirectionDeg != null)
                    "Twój kierunek + kierunek wiatru z prognozy."
                else
                    "Twój kierunek. Wiatr pojawi się po pobraniu pogody.",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CompassRose(
                    azimuth = azimuth,
                    windDirectionDeg = windDirectionDeg,
                    modifier = Modifier.size(180.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text("N", color = OpsColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "%.0f° ${bearingCardinal(azimuth.toDouble())}".format(azimuth),
                        color = OpsColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (windDirectionDeg != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(VerdictColors.Go, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(6.dp))
                            Text("wiatr", color = OpsColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            "%.0f° ${windDirectionCardinal(windDirectionDeg)}".format(windDirectionDeg),
                            color = OpsColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "(kierunek skąd wieje)",
                            color = OpsColors.TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassRose(
    azimuth: Float,
    windDirectionDeg: Double?,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Zewnętrzny okrąg
        drawCircle(
            color = OpsColors.Grid,
            radius = r - 2f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
        )

        // Tarcza z podziałką co 30°
        rotate(-azimuth, pivot = Offset(cx, cy)) {
            for (deg in 0 until 360 step 30) {
                val a = Math.toRadians(deg.toDouble() - 90).toFloat()
                val x1 = cx + (r - 4f) * cos(a)
                val y1 = cy + (r - 4f) * sin(a)
                val x2 = cx + (r - 14f) * cos(a)
                val y2 = cy + (r - 14f) * sin(a)
                drawLine(
                    color = OpsColors.TextSecondary,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            // Igła N (czerwona)
            val nAngle = Math.toRadians(-90.0).toFloat()
            val nx = cx + (r - 20f) * cos(nAngle)
            val ny = cy + (r - 20f) * sin(nAngle)
            drawLine(
                color = Color(0xFFEF4444),
                start = Offset(cx, cy),
                end = Offset(nx, ny),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            // S (biały)
            val sAngle = Math.toRadians(90.0).toFloat()
            val sx = cx + (r - 30f) * cos(sAngle)
            val sy = cy + (r - 30f) * sin(sAngle)
            drawLine(
                color = OpsColors.TextPrimary,
                start = Offset(cx, cy),
                end = Offset(sx, sy),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Wind arrow (zielony)
            if (windDirectionDeg != null) {
                val windAngle = Math.toRadians(windDirectionDeg - 90.0).toFloat()
                val wx = cx + (r - 40f) * cos(windAngle)
                val wy = cy + (r - 40f) * sin(windAngle)
                drawLine(
                    color = VerdictColors.Go,
                    start = Offset(cx, cy),
                    end = Offset(wx, wy),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                // Grot strzałki wiatru
                val arrowSize = 12.dp.toPx()
                val arrowPath = Path().apply {
                    moveTo(wx, wy)
                    lineTo(
                        wx - arrowSize * cos(windAngle - 0.5f),
                        wy - arrowSize * sin(windAngle - 0.5f),
                    )
                    lineTo(
                        wx - arrowSize * cos(windAngle + 0.5f),
                        wy - arrowSize * sin(windAngle + 0.5f),
                    )
                    close()
                }
                drawPath(arrowPath, VerdictColors.Go)
            }
        }

        // Etykiety kierunków (bez rotacji z tarczą — użytkownik widzi je zawsze na pozycji stałych stron świata)
        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#D9F5FF")
            textSize = 32f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        // Ale muszą się obracać razem z tarczą — tak jak reszta.
        rotate(-azimuth, pivot = Offset(cx, cy)) {
            val labels = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)
            for ((label, deg) in labels) {
                val a = Math.toRadians(deg - 90).toFloat()
                val lx = cx + (r - 32f) * cos(a)
                val ly = cy + (r - 32f) * sin(a) + 10f
                drawContext.canvas.nativeCanvas.drawText(label, lx, ly, labelPaint)
            }
        }
    }
}

private fun bearingCardinal(deg: Double): String {
    val d = ((deg % 360) + 360) % 360
    return when {
        d < 22.5 || d >= 337.5 -> "N"
        d < 67.5 -> "NE"
        d < 112.5 -> "E"
        d < 157.5 -> "SE"
        d < 202.5 -> "S"
        d < 247.5 -> "SW"
        d < 292.5 -> "W"
        else -> "NW"
    }
}
