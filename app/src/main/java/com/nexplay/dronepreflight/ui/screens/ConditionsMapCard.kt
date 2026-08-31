package com.nexplay.dronepreflight.ui.screens

import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCoroutineScope
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DroneLimits
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.WindGridFetcher
import com.nexplay.dronepreflight.data.WindPoint
import com.nexplay.dronepreflight.ui.theme.OpsColors
import com.nexplay.dronepreflight.ui.theme.VerdictColors
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ConditionsMapCard(
    snap: AggregatedSnapshot?,
    assessment: FlightAssessment?,
    limits: DroneLimits = DroneLimits(),
    pinnedCoords: Pair<Double, Double>? = null,
    onPin: (Double, Double) -> Unit = { _, _ -> },
    onClearPin: () -> Unit = {},
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
) {
    if (snap == null) return
    val context = LocalContext.current

    // Konfiguracja osmdroid raz
    DisposableEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            load(context, context.getSharedPreferences("osmdroid", 0))
        }
        onDispose { }
    }

    val verdict = assessment?.overall ?: Verdict.CAUTION
    val verdictColor = when (verdict) {
        Verdict.GO -> VerdictColors.Go
        Verdict.CAUTION -> VerdictColors.Caution
        Verdict.NO_GO -> VerdictColors.NoGo
    }

    var tapCoords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showWindGrid by remember { mutableStateOf(false) }
    var loadingWind by remember { mutableStateOf(false) }
    var windPoints by remember { mutableStateOf<List<WindPoint>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val centerLat = pinnedCoords?.first ?: snap.latitude
    val centerLon = pinnedCoords?.second ?: snap.longitude

    // Trzymamy jedną instancję MapView przez cały czas życia
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(snap.latitude, snap.longitude))
            // WAŻNE: prosimy rodzica (Compose scrollable Column) o nie-przechwytywanie
            // touch events — bez tego drag na mapie idzie jako scroll pionowy strony.
            setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false // event trafia dalej do MapView
            }

            // Kompas overlay
            overlays.add(
                CompassOverlay(context, InternalCompassOrientationProvider(context), this).apply {
                    enableCompass()
                }
            )
            // Scale bar
            overlays.add(
                ScaleBarOverlay(this).apply {
                    setCentred(false)
                    setScaleBarOffset(20, 20)
                }
            )
            // Tap listener — stawia pinezke + fetch pogody
            overlays.add(
                MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p != null) {
                            tapCoords = p.latitude to p.longitude
                            onPin(p.latitude, p.longitude)
                        }
                        return true
                    }
                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                })
            )
        }
    }

    // Aktualizuj heatmap gdy zmieni się lokalizacja, werdykt, lub pinezka
    LaunchedEffect(snap.latitude, snap.longitude, verdict, pinnedCoords) {
        mapView.overlays.removeAll { it is Polygon }
        mapView.addConditionCircle(snap.latitude, snap.longitude, 15.0, verdictColor.copy(alpha = 0.10f))
        mapView.addConditionCircle(snap.latitude, snap.longitude, 8.0, verdictColor.copy(alpha = 0.20f))
        mapView.addConditionCircle(snap.latitude, snap.longitude, 3.0, verdictColor.copy(alpha = 0.35f))
        mapView.addConditionCircle(snap.latitude, snap.longitude, 0.3, OpsColors.Accent)
        // Marker pinezki (jeśli jest) — biały punkt z outline w kolorze werdyktu
        pinnedCoords?.let { (lat, lon) ->
            mapView.addConditionCircle(lat, lon, 0.4, Color.White)
            mapView.addConditionCircle(lat, lon, 0.2, verdictColor)
        }
        mapView.invalidate()
    }

    // Toggle strzałek wiatru — pobiera 3x3 siatkę z Open-Meteo
    LaunchedEffect(showWindGrid, centerLat, centerLon) {
        mapView.overlays.removeAll { it is WindArrowsOverlay }
        if (showWindGrid) {
            loadingWind = true
            runCatching { WindGridFetcher.fetch(centerLat, centerLon) }
                .onSuccess { points ->
                    windPoints = points
                    if (points.isNotEmpty()) {
                        mapView.overlays.add(WindArrowsOverlay(points, limits))
                    }
                }
                .onFailure { android.util.Log.e("WindGrid", "fetch failed", it) }
            loadingWind = false
        } else {
            windPoints = emptyList()
        }
        mapView.invalidate()
    }

    val kpText = snap.kpIndex?.let { "%.2f".format(it).replace('.', ',') } ?: "—"
    val kpLevel = when {
        snap.kpIndex == null -> "BRAK DANYCH"
        snap.kpIndex < 4 -> "AKTYWNOŚĆ NISKA"
        snap.kpIndex < 5 -> "AKTYWNOŚĆ ŚREDNIA"
        else -> "AKTYWNOŚĆ WYSOKA"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = OpsColors.BgPanel),
        border = BorderStroke(1.dp, OpsColors.Grid),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                if (fullscreen) Modifier.fillMaxWidth().weight(1f)
                else Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { mapView },
                )

                // Nazwa lokalizacji — top left overlay
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(OpsColors.BgBase.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        snap.locationName,
                        color = OpsColors.TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                // Zoom + fullscreen + clear pin controls — top right
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledIconButton(
                        onClick = onToggleFullscreen,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OpsColors.BgBase.copy(alpha = 0.9f),
                        ),
                    ) {
                        Icon(
                            if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = OpsColors.TextPrimary,
                        )
                    }
                    FilledIconButton(
                        onClick = { mapView.controller.zoomIn() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OpsColors.BgBase.copy(alpha = 0.9f),
                        ),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = OpsColors.TextPrimary)
                    }
                    FilledIconButton(
                        onClick = { mapView.controller.zoomOut() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OpsColors.BgBase.copy(alpha = 0.9f),
                        ),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = OpsColors.TextPrimary)
                    }
                    FilledIconButton(
                        onClick = { showWindGrid = !showWindGrid },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (showWindGrid) OpsColors.Accent
                                else OpsColors.BgBase.copy(alpha = 0.9f),
                        ),
                    ) {
                        Icon(
                            Icons.Default.Air,
                            contentDescription = "Strzałki wiatru",
                            tint = if (showWindGrid) OpsColors.BgBase else OpsColors.TextPrimary,
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            mapView.controller.animateTo(GeoPoint(snap.latitude, snap.longitude))
                            mapView.controller.setZoom(11.0)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OpsColors.Accent,
                        ),
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Wycentruj", tint = OpsColors.BgBase)
                    }
                    if (pinnedCoords != null) {
                        FilledIconButton(
                            onClick = onClearPin,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = VerdictColors.NoGo,
                            ),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Usuń pinezke", tint = Color.White)
                        }
                    }
                }

                // Banner strzałek wiatru
                if (showWindGrid) {
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 12.dp, end = 12.dp)
                            .background(OpsColors.BgBase.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Air, contentDescription = null, tint = OpsColors.Accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (loadingWind) "Ładuję siatkę 3×3…" else "SIATKA WIATRU · ${windPoints.size} pkt",
                            color = OpsColors.TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                // Info o pinezce — bottom center
                if (pinnedCoords != null) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .background(OpsColors.BgBase.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, tint = verdictColor)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Pinezka · %.4f, %.4f".format(pinnedCoords.first, pinnedCoords.second),
                            color = OpsColors.TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                // Info o tapniętym punkcie — bottom
                tapCoords?.let { (lat, lon) ->
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(OpsColors.BgBase.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "Tap: %.4f, %.4f".format(lat, lon),
                            color = OpsColors.TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            // KP INDEX bar u dołu (ukryty w fullscreen)
            if (!fullscreen) Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "KP INDEX (TERAZ)",
                        color = OpsColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        kpLevel,
                        color = when {
                            snap.kpIndex == null -> OpsColors.TextSecondary
                            snap.kpIndex < 4 -> VerdictColors.Go
                            snap.kpIndex < 5 -> VerdictColors.Caution
                            else -> VerdictColors.NoGo
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    kpText,
                    color = VerdictColors.Go,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                KpScaleBar(snap.kpIndex)
            }
        }
    }
}

@Composable
private fun KpScaleBar(kp: Double?) {
    Row(Modifier.fillMaxWidth().height(16.dp)) {
        for (i in 0..9) {
            val c = when {
                i < 4 -> VerdictColors.Go
                i < 5 -> VerdictColors.Caution
                i < 6 -> VerdictColors.Caution.copy(alpha = 0.85f)
                else -> VerdictColors.NoGo
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(c),
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        for (i in 0..9) {
            Text(
                "$i",
                color = OpsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun MapView.addConditionCircle(lat: Double, lon: Double, radiusKm: Double, color: Color) {
    val polygon = Polygon().apply {
        points = circlePoints(lat, lon, radiusKm)
        fillPaint.color = color.toArgb()
        outlinePaint.color = color.copy(alpha = (color.alpha * 1.5f).coerceIn(0f, 1f)).toArgb()
        outlinePaint.strokeWidth = 1f
        outlinePaint.style = Paint.Style.STROKE
        // Polygon nie łapie touch events — mapa dostaje zoom/drag normalnie
        setOnClickListener { _, _, _ -> false }
    }
    overlays.add(polygon)
}

private fun circlePoints(lat: Double, lon: Double, radiusKm: Double): List<GeoPoint> {
    val n = 60
    val kmPerDegLat = 111.0
    val kmPerDegLon = 111.0 * cos(lat * PI / 180.0)
    return (0..n).map { i ->
        val theta = 2 * PI * i / n
        val dLat = (radiusKm * cos(theta)) / kmPerDegLat
        val dLon = (radiusKm * sin(theta)) / kmPerDegLon
        GeoPoint(lat + dLat, lon + dLon)
    }
}
