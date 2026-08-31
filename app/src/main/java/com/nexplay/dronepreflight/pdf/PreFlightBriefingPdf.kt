package com.nexplay.dronepreflight.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.ConfidenceCalculator
import com.nexplay.dronepreflight.data.ConditionCheck
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.DroneLimits
import com.nexplay.dronepreflight.data.DroneProfile
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatTemp
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.data.windDirectionCardinal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generuje briefing przedstartowy w PDF (A4). Zawiera:
 * lokalizację, pełne warunki (mediana z 5 źródeł), KP index, werdykt z uzasadnieniem,
 * confidence score, najlepsze okno GO, listę źródeł, postęp checklisty.
 */
object PreFlightBriefingPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val CONTENT_W = PAGE_W - 2 * MARGIN

    private val AccentBg = Color.rgb(20, 30, 48)
    private val AccentGreen = Color.rgb(52, 211, 153)
    private val TextDark = Color.rgb(15, 23, 42)
    private val TextGray = Color.rgb(100, 116, 139)
    private val RowAlt = Color.rgb(248, 250, 252)
    private val Divider = Color.rgb(226, 232, 240)
    private val VerdictGo = Color.rgb(34, 197, 94)
    private val VerdictCaution = Color.rgb(245, 158, 11)
    private val VerdictNoGo = Color.rgb(239, 68, 68)

    fun export(
        context: Context,
        snap: AggregatedSnapshot,
        assessment: FlightAssessment,
        limits: DroneLimits,
        activeProfile: DroneProfile?,
        units: DisplayUnits,
        checklistDone: Int,
        checklistTotal: Int,
        bestWindowText: String?,
    ): Uri {
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val regular = Typeface.SANS_SERIF!!

        val pdf = PdfDocument()
        val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
        val page = pdf.startPage(info)
        val c = page.canvas

        val confidence = ConfidenceCalculator.calculate(snap)

        var y = drawHeader(c, bold, regular, snap)
        y += 20f
        y = drawVerdictHero(c, y, bold, regular, assessment.overall, confidence)
        y += 16f
        y = drawSection(c, y, bold, "LOKALIZACJA")
        y = drawKV(c, y, regular, "Miejsce", snap.locationName)
        y = drawKV(c, y, regular, "Współrzędne", "%.4f, %.4f".format(Locale.US, snap.latitude, snap.longitude))
        activeProfile?.let {
            y = drawKV(c, y, regular, "Profil BSP", it.name)
        }
        y = drawKV(c, y, regular, "Data pobrania",
            SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(snap.fetchedAt)))

        y += 12f
        y = drawSection(c, y, bold, "WARUNKI POGODOWE — MEDIANA z ${snap.successfulSources}/${snap.totalSources} źródeł")
        y = drawKV(c, y, regular, "Wiatr",
            "${snap.wind.median?.let { formatWind(it, units.wind) } ?: "—"}  ${windDirectionCardinal(snap.windDir.median)}")
        y = drawKV(c, y, regular, "Porywy", snap.gust.median?.let { formatWind(it, units.wind) } ?: "—")
        y = drawKV(c, y, regular, "Temperatura", formatTemp(snap.temp.median, units.temp))
        y = drawKV(c, y, regular, "Opady",
            snap.precip.median?.let { if (it > 0.0) "%.1f mm/h".format(it) else "brak" } ?: "brak danych")
        y = drawKV(c, y, regular, "Zachmurzenie",
            snap.cloud.median?.let { "%.0f%%".format(it) } ?: "—")
        y = drawKV(c, y, regular, "Widoczność",
            snap.visibility.median?.let {
                if (it >= 10_000) "%.0f km".format(it / 1000) else "%.1f km".format(it / 1000)
            } ?: "brak danych")
        y = drawKV(c, y, regular, "Wilgotność",
            snap.humidity.median?.let { "%.0f%%".format(it) } ?: "—")

        y += 12f
        y = drawSection(c, y, bold, "KP INDEX")
        y = drawKV(c, y, regular, "Wartość",
            snap.kpIndex?.let { "%.1f".format(it) } ?: "brak danych")
        val kpLevel = when {
            snap.kpIndex == null -> "brak danych"
            snap.kpIndex!! < 4 -> "niska aktywność geomagnetyczna"
            snap.kpIndex!! < 5 -> "średnia aktywność"
            else -> "wysoka aktywność (możliwe zakłócenia GNSS)"
        }
        y = drawKV(c, y, regular, "Ocena", kpLevel)
        y = drawKV(c, y, regular, "Źródła KP", "${snap.kpReadings.size}/${snap.kpTotalSources}")

        y += 12f
        y = drawSection(c, y, bold, "OCENA WZGLĘDEM TWOJEGO BSP")
        assessment.checks.forEach { ck ->
            y = drawCheckRow(c, y, bold, regular, ck)
            if (y > PAGE_H - 100f) return@forEach
        }

        y += 12f
        if (bestWindowText != null) {
            y = drawSection(c, y, bold, "NAJLEPSZE OKNO GO")
            y = drawKV(c, y, regular, "Okno", bestWindowText)
        }

        y += 12f
        y = drawSection(c, y, bold, "CHECKLISTA PANSA")
        y = drawKV(c, y, regular, "Postęp", "$checklistDone / $checklistTotal punktów")

        y += 12f
        y = drawSection(c, y, bold, "PEWNOŚĆ DANYCH")
        y = drawKV(c, y, regular, "Confidence Score", "${confidence.percent}% (${confidence.label})")
        confidence.details.forEach {
            y = drawKV(c, y, regular, "·", it)
        }

        drawFooter(c, bold, regular)
        pdf.finishPage(page)

        val outDir = File(context.getExternalFilesDir(null), "briefings").apply { mkdirs() }
        val filename = "briefing-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.pdf"
        val file = File(outDir, filename)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()

        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
    }

    private fun drawHeader(c: Canvas, bold: Typeface, regular: Typeface, snap: AggregatedSnapshot): Float {
        // Green bar
        val bgPaint = Paint().apply { color = AccentBg; isAntiAlias = true }
        c.drawRect(0f, 0f, PAGE_W.toFloat(), 90f, bgPaint)

        val titlePaint = Paint().apply {
            typeface = bold; textSize = 22f; color = Color.WHITE; isAntiAlias = true
        }
        c.drawText("NEXDRONE · PRE-FLIGHT BRIEFING", MARGIN, 42f, titlePaint)

        val subPaint = Paint().apply {
            typeface = regular; textSize = 11f; color = AccentGreen; isAntiAlias = true
        }
        c.drawText(
            "Wygenerowano " + SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()).format(Date()),
            MARGIN, 62f, subPaint,
        )
        c.drawText(snap.locationName, MARGIN, 78f, subPaint)

        return 110f
    }

    private fun drawVerdictHero(
        c: Canvas, yStart: Float, bold: Typeface, regular: Typeface,
        overall: Verdict, confidence: ConfidenceCalculator.Confidence,
    ): Float {
        val (color, label, sub) = when (overall) {
            Verdict.GO -> Triple(VerdictGo, "GO", "Warunki dobre do lotu")
            Verdict.CAUTION -> Triple(VerdictCaution, "OSTROŻNIE", "Warunki graniczne")
            Verdict.NO_GO -> Triple(VerdictNoGo, "NO-GO", "Nie lataj")
        }
        val boxH = 84f
        val rect = RectF(MARGIN, yStart, PAGE_W - MARGIN, yStart + boxH)
        val bg = Paint().apply {
            this.color = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color))
            isAntiAlias = true
        }
        c.drawRoundRect(rect, 10f, 10f, bg)
        val border = Paint().apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true
        }
        c.drawRoundRect(rect, 10f, 10f, border)

        val labelPaint = Paint().apply {
            typeface = bold; textSize = 42f; this.color = color; isAntiAlias = true
        }
        c.drawText(label, MARGIN + 18f, yStart + 52f, labelPaint)

        val subPaint = Paint().apply {
            typeface = regular; textSize = 12f; this.color = TextDark; isAntiAlias = true
        }
        c.drawText(sub, MARGIN + 18f, yStart + 70f, subPaint)

        // Right side: confidence
        val confPaint = Paint().apply {
            typeface = bold; textSize = 24f; this.color = TextDark; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        c.drawText("${confidence.percent}%", PAGE_W - MARGIN - 18f, yStart + 40f, confPaint)
        val confLabel = Paint().apply {
            typeface = regular; textSize = 10f; this.color = TextGray; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        c.drawText("PEWNOŚĆ · ${confidence.label}", PAGE_W - MARGIN - 18f, yStart + 56f, confLabel)

        return yStart + boxH
    }

    private fun drawSection(c: Canvas, y: Float, bold: Typeface, title: String): Float {
        val paint = Paint().apply {
            typeface = bold; textSize = 11f; color = AccentGreen; isAntiAlias = true
        }
        c.drawText(title, MARGIN, y, paint)
        // underline
        val line = Paint().apply {
            color = Divider; strokeWidth = 1f; isAntiAlias = true
        }
        c.drawLine(MARGIN, y + 4f, PAGE_W - MARGIN, y + 4f, line)
        return y + 18f
    }

    private fun drawKV(c: Canvas, y: Float, regular: Typeface, key: String, value: String): Float {
        val keyPaint = Paint().apply {
            typeface = regular; textSize = 10f; color = TextGray; isAntiAlias = true
        }
        val valPaint = Paint().apply {
            typeface = Typeface.create(regular, Typeface.BOLD); textSize = 11f; color = TextDark; isAntiAlias = true
        }
        c.drawText(key, MARGIN, y, keyPaint)
        c.drawText(value, MARGIN + 130f, y, valPaint)
        return y + 16f
    }

    private fun drawCheckRow(
        c: Canvas, y: Float, bold: Typeface, regular: Typeface, ck: ConditionCheck,
    ): Float {
        val col = when (ck.verdict) {
            Verdict.GO -> VerdictGo
            Verdict.CAUTION -> VerdictCaution
            Verdict.NO_GO -> VerdictNoGo
        }
        val dot = Paint().apply { color = col; isAntiAlias = true }
        c.drawCircle(MARGIN + 4f, y - 4f, 4f, dot)

        val labelPaint = Paint().apply {
            typeface = bold; textSize = 11f; color = TextDark; isAntiAlias = true
        }
        c.drawText(ck.label, MARGIN + 18f, y, labelPaint)

        val valPaint = Paint().apply {
            typeface = regular; textSize = 11f; color = col; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        c.drawText(ck.value, PAGE_W - MARGIN, y, valPaint)

        ck.note?.let {
            val notePaint = Paint().apply {
                typeface = regular; textSize = 9f; color = TextGray; isAntiAlias = true
            }
            c.drawText(it, MARGIN + 18f, y + 12f, notePaint)
            return y + 28f
        }
        return y + 20f
    }

    private fun drawFooter(c: Canvas, bold: Typeface, regular: Typeface) {
        val paint = Paint().apply {
            typeface = regular; textSize = 8f; color = TextGray; isAntiAlias = true
        }
        val line = Paint().apply {
            color = Divider; strokeWidth = 0.5f; isAntiAlias = true
        }
        c.drawLine(MARGIN, PAGE_H - 40f, PAGE_W - MARGIN, PAGE_H - 40f, line)
        c.drawText("NexDrone · github.com/michalzembala-cpu/NexDrone", MARGIN, PAGE_H - 28f, paint)
        val rightPaint = Paint().apply {
            typeface = regular; textSize = 8f; color = TextGray; isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        c.drawText(
            "Wygenerowano " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            PAGE_W - MARGIN, PAGE_H - 28f, rightPaint,
        )
    }
}
