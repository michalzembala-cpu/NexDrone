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
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.FlightLogEntry
import com.nexplay.dronepreflight.data.formatTemp
import com.nexplay.dronepreflight.data.formatWind
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF z logiem lotów (A4).
 * Jeśli w `assets/fonts/nexdrone-regular.ttf` i `nexdrone-bold.ttf` są pliki fontów,
 * apka ich użyje. Inaczej — systemowy sans-serif.
 */
object FlightLogPdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN_L = 40f
    private const val MARGIN_R = 40f

    // Kolory NexDrone
    private val AccentBg = Color.rgb(20, 30, 48)       // #141E30
    private val AccentGreen = Color.rgb(52, 211, 153)  // #34D399
    private val TextDark = Color.rgb(15, 23, 42)
    private val TextGray = Color.rgb(100, 116, 139)
    private val RowAlt = Color.rgb(248, 250, 252)
    private val Divider = Color.rgb(226, 232, 240)
    private val VerdictGo = Color.rgb(34, 197, 94)
    private val VerdictCaution = Color.rgb(245, 158, 11)
    private val VerdictNoGo = Color.rgb(239, 68, 68)

    fun export(
        context: Context,
        entries: List<FlightLogEntry>,
        units: DisplayUnits = DisplayUnits(),
    ): Uri {
        val typefaceRegular = loadAssetFont(context, "fonts/nexdrone-regular.ttf")
            ?: Typeface.SANS_SERIF
        val typefaceBold = loadAssetFont(context, "fonts/nexdrone-bold.ttf")
            ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()

        // Paints
        val titlePaint = Paint().apply {
            typeface = typefaceBold; textSize = 22f
            color = Color.WHITE; isAntiAlias = true
        }
        val subTitleWhitePaint = Paint().apply {
            typeface = typefaceRegular; textSize = 10f
            color = Color.WHITE; isAntiAlias = true; alpha = 200
        }
        val headerCellPaint = Paint().apply {
            typeface = typefaceBold; textSize = 9f
            color = TextGray; isAntiAlias = true
            letterSpacing = 0.08f
        }
        val rowCellPaint = Paint().apply {
            typeface = typefaceRegular; textSize = 11f
            color = TextDark; isAntiAlias = true
        }
        val rowBoldPaint = Paint().apply {
            typeface = typefaceBold; textSize = 11f
            color = TextDark; isAntiAlias = true
        }
        val notePaint = Paint().apply {
            typeface = typefaceRegular; textSize = 10f
            color = TextGray; isAntiAlias = true
            isSubpixelText = true
        }
        val dividerPaint = Paint().apply { color = Divider; strokeWidth = 0.5f }
        val bgPaint = Paint()
        val statPaint = Paint().apply {
            typeface = typefaceBold; textSize = 14f
            color = TextDark; isAntiAlias = true
        }
        val footerPaint = Paint().apply {
            typeface = typefaceRegular; textSize = 8f
            color = TextGray; isAntiAlias = true
        }

        val dfDate = SimpleDateFormat("d MMM yyyy", Locale("pl"))
        val dfTime = SimpleDateFormat("HH:mm", Locale("pl"))

        var pageNum = 1
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas
        var y = drawPageHeader(canvas, titlePaint, subTitleWhitePaint, entries.size)

        // Statystyki na pierwszej stronie
        y = drawStats(canvas, entries, y, statPaint, notePaint, rowCellPaint, units)

        y += 10f
        canvas.drawLine(MARGIN_L, y, PAGE_W - MARGIN_R, y, dividerPaint)
        y += 12f

        // Header tabeli
        y = drawTableHeader(canvas, y, headerCellPaint, dividerPaint)

        // Wiersze
        entries.forEachIndexed { i, entry ->
            val neededHeight = 24f + if (entry.note.isNotBlank()) 18f else 0f
            if (y + neededHeight > PAGE_H - 40f) {
                drawFooter(canvas, pageNum, footerPaint)
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = drawPageHeader(canvas, titlePaint, subTitleWhitePaint, entries.size)
                y = drawTableHeader(canvas, y, headerCellPaint, dividerPaint)
            }

            // Alternating row bg
            if (i % 2 == 1) {
                bgPaint.color = RowAlt
                canvas.drawRect(MARGIN_L, y - 12f, PAGE_W - MARGIN_R, y + 8f, bgPaint)
            }

            drawEntryRow(canvas, y, entry, rowCellPaint, rowBoldPaint, dfDate, dfTime, units)
            y += 16f
            if (entry.note.isNotBlank()) {
                val note = "„${entry.note}”"
                canvas.drawText(note.take(100), MARGIN_L + 20f, y, notePaint)
                y += 14f
            }
            y += 4f
        }

        drawFooter(canvas, pageNum, footerPaint)
        pdf.finishPage(page)

        val outDir = File(context.getExternalFilesDir(null), "flight-logs").apply { mkdirs() }
        val filename = "nexdrone-log-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.pdf"
        val file = File(outDir, filename)
        file.outputStream().use { pdf.writeTo(it) }
        pdf.close()

        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
    }

    private fun loadAssetFont(context: Context, path: String): Typeface? = runCatching {
        Typeface.createFromAsset(context.assets, path)
    }.getOrNull()

    private fun drawPageHeader(
        canvas: Canvas,
        titlePaint: Paint,
        subPaint: Paint,
        totalFlights: Int,
    ): Float {
        // Zielony pasek tytułowy
        val bgPaint = Paint().apply { color = AccentBg }
        val accentPaint = Paint().apply { color = AccentGreen }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 60f, bgPaint)
        canvas.drawRect(0f, 60f, PAGE_W.toFloat(), 62f, accentPaint)
        canvas.drawText("NexDrone", MARGIN_L, 30f, titlePaint)
        canvas.drawText(
            "CENTRUM DOWODZENIA · $totalFlights lotów · " +
                    SimpleDateFormat("d MMM yyyy · HH:mm", Locale("pl")).format(Date()),
            MARGIN_L, 46f, subPaint,
        )
        return 80f
    }

    private fun drawStats(
        canvas: Canvas,
        entries: List<FlightLogEntry>,
        startY: Float,
        boldPaint: Paint,
        subPaint: Paint,
        cellPaint: Paint,
        units: DisplayUnits,
    ): Float {
        val totalMin = entries.mapNotNull { it.durationMinutes }.sum()
        val hours = totalMin / 60
        val mins = totalMin % 60
        val temps = entries.mapNotNull { it.tempC }
        val winds = entries.mapNotNull { it.windMs }
        val avgTemp = if (temps.isEmpty()) null else temps.average()
        val avgWind = if (winds.isEmpty()) null else winds.average()

        var y = startY + 10f
        canvas.drawText("PODSUMOWANIE", MARGIN_L, y, subPaint.apply { color = TextGray })
        y += 18f

        val col1 = MARGIN_L
        val col2 = MARGIN_L + 140f
        val col3 = MARGIN_L + 280f
        val col4 = MARGIN_L + 420f

        canvas.drawText("Łączny czas", col1, y, subPaint)
        canvas.drawText("${hours}h ${mins}m", col1, y + 14f, boldPaint)
        canvas.drawText("Lotów", col2, y, subPaint)
        canvas.drawText("${entries.size}", col2, y + 14f, boldPaint)
        canvas.drawText("Śr. temperatura", col3, y, subPaint)
        canvas.drawText(formatTemp(avgTemp, units.temp), col3, y + 14f, boldPaint)
        canvas.drawText("Śr. wiatr", col4, y, subPaint)
        canvas.drawText(formatWind(avgWind, units.wind), col4, y + 14f, boldPaint)
        return y + 24f
    }

    private fun drawTableHeader(
        canvas: Canvas,
        startY: Float,
        headerPaint: Paint,
        dividerPaint: Paint,
    ): Float {
        val y = startY
        canvas.drawText("DATA", MARGIN_L, y, headerPaint)
        canvas.drawText("LOKALIZACJA", MARGIN_L + 100f, y, headerPaint)
        canvas.drawText("WERD.", MARGIN_L + 240f, y, headerPaint)
        canvas.drawText("TEMP", MARGIN_L + 300f, y, headerPaint)
        canvas.drawText("WIATR", MARGIN_L + 360f, y, headerPaint)
        canvas.drawText("KP", MARGIN_L + 430f, y, headerPaint)
        canvas.drawText("MIN", MARGIN_L + 470f, y, headerPaint)
        canvas.drawLine(MARGIN_L, y + 4f, PAGE_W - MARGIN_R, y + 4f, dividerPaint)
        return y + 16f
    }

    private fun drawEntryRow(
        canvas: Canvas,
        y: Float,
        entry: FlightLogEntry,
        cellPaint: Paint,
        boldPaint: Paint,
        dfDate: SimpleDateFormat,
        dfTime: SimpleDateFormat,
        units: DisplayUnits,
    ) {
        val date = dfDate.format(Date(entry.timestamp))
        val time = dfTime.format(Date(entry.timestamp))
        canvas.drawText("$date · $time", MARGIN_L, y, cellPaint)
        canvas.drawText(entry.locationName.take(20), MARGIN_L + 100f, y, cellPaint)

        // Verdict pill
        val (label, color) = when (entry.verdict) {
            "GO" -> "GO" to VerdictGo
            "CAUTION" -> "OSTR." to VerdictCaution
            "NO_GO" -> "NO-GO" to VerdictNoGo
            else -> "—" to TextGray
        }
        val pillPaint = Paint().apply { this.color = color; isAntiAlias = true }
        val pillTextPaint = Paint().apply {
            typeface = boldPaint.typeface; textSize = 9f
            this.color = Color.WHITE; isAntiAlias = true
        }
        val pillWidth = pillTextPaint.measureText(label) + 12f
        val rect = RectF(MARGIN_L + 240f, y - 9f, MARGIN_L + 240f + pillWidth, y + 3f)
        canvas.drawRoundRect(rect, 4f, 4f, pillPaint)
        canvas.drawText(label, MARGIN_L + 240f + 6f, y - 1f, pillTextPaint)

        canvas.drawText(formatTemp(entry.tempC, units.temp), MARGIN_L + 300f, y, cellPaint)
        canvas.drawText(formatWind(entry.windMs, units.wind), MARGIN_L + 360f, y, cellPaint)
        canvas.drawText(entry.kpIndex?.let { "%.1f".format(it) } ?: "—", MARGIN_L + 430f, y, cellPaint)
        canvas.drawText(entry.durationMinutes?.toString() ?: "—", MARGIN_L + 470f, y, cellPaint)
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int, paint: Paint) {
        canvas.drawText(
            "NexDrone · centrum dowodzenia dla pilotów BSP · strona $pageNum",
            MARGIN_L, (PAGE_H - 20).toFloat(), paint,
        )
    }
}
