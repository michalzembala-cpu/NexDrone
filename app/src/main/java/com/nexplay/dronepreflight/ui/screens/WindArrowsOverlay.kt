package com.nexplay.dronepreflight.ui.screens

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.nexplay.dronepreflight.data.DroneLimits
import com.nexplay.dronepreflight.data.WindPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rysuje strzałki wiatru na mapie osmdroid. Długość ~ prędkość, kolor ~ limity BSP,
 * kierunek = "gdzie leci" (meteorologiczne + 180°).
 */
class WindArrowsOverlay(
    private val points: List<WindPoint>,
    private val limits: DroneLimits,
) : Overlay() {

    private val paint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint().apply {
        isAntiAlias = true
        textSize = 22f
        color = Color.WHITE
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }
    private val bgPaint = Paint().apply {
        isAntiAlias = true
        color = Color.argb(180, 20, 30, 48)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection

        points.forEach { p ->
            val screen = projection.toPixels(GeoPoint(p.lat, p.lon), null)
            val cx = screen.x.toFloat()
            val cy = screen.y.toFloat()

            // Skala długości: min 20px, max 80px
            val len = (p.windMs * 4.5f + 15f).coerceIn(20f, 90f)
            // Kierunek w matematycznych radianach — meteo direction to skąd wieje,
            // strzałka pokazuje "gdzie leci", więc +180°.
            val angRad = Math.toRadians(p.dirDeg + 90.0) // +90 bo screen Y odwrócone
            val endX = cx + (cos(angRad) * len).toFloat()
            val endY = cy + (sin(angRad) * len).toFloat()

            // Kolor po limitach
            val color = when {
                p.windMs >= limits.maxWindMs -> Color.rgb(239, 68, 68)
                p.windMs >= limits.maxWindMs * 0.75 -> Color.rgb(245, 158, 11)
                else -> Color.rgb(52, 211, 153)
            }
            paint.color = color

            // Trzon strzałki
            canvas.drawLine(cx, cy, endX, endY, paint)

            // Grot — dwie linie 25° w tył
            val headLen = 12f
            val backAng1 = angRad + Math.PI - Math.PI / 7
            val backAng2 = angRad + Math.PI + Math.PI / 7
            canvas.drawLine(
                endX, endY,
                endX + (cos(backAng1) * headLen).toFloat(),
                endY + (sin(backAng1) * headLen).toFloat(),
                paint,
            )
            canvas.drawLine(
                endX, endY,
                endX + (cos(backAng2) * headLen).toFloat(),
                endY + (sin(backAng2) * headLen).toFloat(),
                paint,
            )

            // Etykieta prędkości u podstawy (tylko dla dłuższych strzałek)
            val label = "%.1f".format(p.windMs)
            val textW = labelPaint.measureText(label)
            val padX = 4f
            val padY = 2f
            canvas.drawRoundRect(
                cx - textW / 2 - padX, cy + 8f,
                cx + textW / 2 + padX, cy + 8f + labelPaint.textSize + padY,
                4f, 4f, bgPaint,
            )
            canvas.drawText(label, cx - textW / 2, cy + 8f + labelPaint.textSize - 2f, labelPaint)
        }
    }
}
