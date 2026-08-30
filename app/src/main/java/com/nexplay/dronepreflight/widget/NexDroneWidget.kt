package com.nexplay.dronepreflight.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nexplay.dronepreflight.MainActivity
import com.nexplay.dronepreflight.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NexDroneWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsStore(context)
        val snap = settings.widgetSnapshot.first()
        provideContent { WidgetContent(snap) }
    }

    suspend fun updateAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(NexDroneWidget::class.java).forEach { id ->
            update(context, id)
        }
    }
}

class NexDroneWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NexDroneWidget
}

@Composable
private fun WidgetContent(snap: SettingsStore.WidgetSnapshot) {
    val (verdictLabel, verdictColor) = when (snap.verdict) {
        "GO" -> "GO" to Color(0xFF22C55E)
        "CAUTION" -> "OSTROŻNIE" to Color(0xFFF59E0B)
        "NO_GO" -> "NO-GO" to Color(0xFFEF4444)
        else -> "—" to Color(0xFF64748B)
    }
    val bg = Color(0xFF0A1220)
    val panelBg = Color(0xFF141E30)
    val accent = Color(0xFF34D399)
    val textPri = Color(0xFFE5EEF7)
    val textSec = Color(0xFF94A3B8)

    val timestamp = if (snap.updatedAt > 0)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snap.updatedAt))
    else "—"

    val mono = FontFamily.Monospace

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bg))
            .cornerRadius(16.dp)
            .padding(10.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        // Header: NEXDRONE + timestamp
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NEXDRONE",
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                "· $timestamp",
                style = TextStyle(
                    color = ColorProvider(textSec),
                    fontSize = 9.sp,
                    fontFamily = mono,
                ),
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        // Verdict pigułka (pełna szerokość)
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(verdictColor))
                .cornerRadius(10.dp)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                verdictLabel,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        Spacer(GlanceModifier.height(8.dp))

        // Dwa panele: TEMP i WIATR
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(ColorProvider(panelBg))
                    .cornerRadius(8.dp)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "TEMP",
                        style = TextStyle(
                            color = ColorProvider(textSec),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        snap.tempC,
                        style = TextStyle(
                            color = ColorProvider(textPri),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = mono,
                        ),
                    )
                }
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .background(ColorProvider(panelBg))
                    .cornerRadius(8.dp)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "WIATR",
                        style = TextStyle(
                            color = ColorProvider(textSec),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        snap.windMs,
                        style = TextStyle(
                            color = ColorProvider(textPri),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = mono,
                        ),
                    )
                }
            }
        }
        Spacer(GlanceModifier.height(6.dp))

        // Lokalizacja
        Text(
            snap.location,
            style = TextStyle(
                color = ColorProvider(textSec),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier.fillMaxWidth(),
            maxLines = 1,
        )
    }
}
