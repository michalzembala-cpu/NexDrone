package com.nexplay.dronepreflight.copilot

import com.nexplay.dronepreflight.data.AggregatedSnapshot
import com.nexplay.dronepreflight.data.DisplayUnits
import com.nexplay.dronepreflight.data.FlightAssessment
import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.ui.HourlyOutlook

/** Wiadomości generowane przez AI Co-pilota — treść dla TTS i loga w UI. */
data class CopilotMessage(
    val priority: Priority,
    val text: String,
) {
    enum class Priority { INFO, WARN, ALERT, SUCCESS }
}

/**
 * Rule-based silnik decyzyjny drugiego pilota — w stylu Jarvisa.
 * Zwięzły, grzeczny, mówi TYLKO gdy jest o czym mówić. Nie gada co 30 sekund.
 */
object AiCopilot {

    /** Pre-flight brief przy załadowaniu snapshotu — jedno zdanie o warunkach + ewentualne ostrzeżenie. */
    fun preFlightBriefing(
        pilotName: String,
        snap: AggregatedSnapshot,
        assessment: FlightAssessment,
        outlook: List<HourlyOutlook>,
        units: DisplayUnits,
    ): CopilotMessage {
        val addressee = if (pilotName.isBlank()) "" else " $pilotName"
        val greet = timeGreeting()

        val wind = snap.wind.median?.let { formatWind(it, units.wind) } ?: "brak danych"
        val gust = snap.gust.median?.let { formatWind(it, units.wind) }

        // Trend następnych 2h
        val currentWind = snap.wind.median ?: 0.0
        val next2h = outlook.take(3).mapNotNull { it.windMs }
        val futureMax = next2h.maxOrNull() ?: currentWind
        val bigJumpAheadMinutes: Int? = if (futureMax > currentWind + 3.0) {
            outlook.indexOfFirst { (it.windMs ?: 0.0) > currentWind + 3.0 }
                .takeIf { it >= 0 }?.let { (it + 1) * 60 - 30 }
        } else null

        val text = buildString {
            append("$greet$addressee. ")
            when (assessment.overall) {
                Verdict.GO -> {
                    append("Warunki wyglądają dobrze. Wiatr $wind")
                    if (gust != null) append(", porywy do $gust")
                    append(". Systemy pokazują zielone światło.")
                }
                Verdict.CAUTION -> {
                    append("Warunki są graniczne. Wiatr $wind")
                    if (gust != null) append(", porywy $gust")
                    append(". ")
                    val problem = assessment.checks.firstOrNull { it.verdict == Verdict.CAUTION }
                    if (problem != null) append("Sugeruję zwrócić uwagę na ${problem.label.lowercase()}.")
                }
                Verdict.NO_GO -> {
                    append("Odradzam lot. ")
                    val problem = assessment.checks.firstOrNull { it.verdict == Verdict.NO_GO }
                    if (problem != null) {
                        append("${problem.label} przekracza limit BSP. ")
                    }
                    append("Sprawdzę okno na później.")
                }
            }
            bigJumpAheadMinutes?.let {
                append(" Za około $it minut wiatr może się zaostrzyć. Jeśli chce Pan nagrywać — teraz jest lepsze okno.")
            }
        }

        val prio = when (assessment.overall) {
            Verdict.GO -> CopilotMessage.Priority.SUCCESS
            Verdict.CAUTION -> CopilotMessage.Priority.WARN
            Verdict.NO_GO -> CopilotMessage.Priority.ALERT
        }
        return CopilotMessage(prio, text)
    }

    /** Podsumowanie po locie — Jarvis-style. */
    fun postFlight(
        pilotName: String,
        elapsedSec: Int,
        maxWindMs: Double?,
        maxGustMs: Double?,
        units: DisplayUnits,
        goPct: Int,
        outlook: List<HourlyOutlook>,
    ): CopilotMessage {
        val mm = elapsedSec / 60
        val addressee = if (pilotName.isBlank()) "" else " $pilotName"
        val text = buildString {
            append("Lot zakończony$addressee. ")
            append("$mm minut w powietrzu. ")
            if (maxWindMs != null) {
                append("Maksymalny wiatr ${formatWind(maxWindMs, units.wind)}")
                if (maxGustMs != null) append(", porywy ${formatWind(maxGustMs, units.wind)}")
                append(". ")
            }
            append(when {
                goPct >= 90 -> "Wszystko w normie."
                goPct >= 50 -> "Warunki były zmienne. Sugeruję sprawdzić nagranie pod kątem drgań."
                else -> "Warunki były trudne. Dobrze że wróciliśmy w jednym kawałku."
            })
            nextGoWindow(outlook)?.let { append(" $it") }
        }
        return CopilotMessage(CopilotMessage.Priority.INFO, text)
    }

    /** Alert gdy verdict się pogorszył. */
    fun conditionsDropped(
        pilotName: String,
        newVerdict: Verdict,
        snap: AggregatedSnapshot,
        units: DisplayUnits,
    ): CopilotMessage {
        val addr = if (pilotName.isBlank()) "" else "$pilotName, "
        val text = when (newVerdict) {
            Verdict.CAUTION -> "${addr}warunki się zmieniły. " +
                (snap.gust.median?.let { "Porywy ${formatWind(it, units.wind)}. " } ?: "") +
                "Może Pan latać, ale sugeruję ostrożniejszy powrót."
            Verdict.NO_GO -> "${addr}warunki wyszły poza margines bezpieczeństwa. Rekomenduję powolne kończenie ujęć."
            Verdict.GO -> return CopilotMessage(CopilotMessage.Priority.SUCCESS, "Warunki wróciły do GO.")
        }
        return CopilotMessage(CopilotMessage.Priority.WARN, text)
    }

    /** Alert "GO kończy się za X min". */
    fun goEndingSoon(minutesLeft: Int, endHour: Int, endMinute: Int): CopilotMessage {
        val text = "Uwaga. Okno GO kończy się za $minutesLeft minut, o ${"%02d:%02d".format(endHour, endMinute)}. Sugeruję powolne kończenie."
        return CopilotMessage(CopilotMessage.Priority.WARN, text)
    }

    private fun timeGreeting(): String {
        val hour = java.time.LocalTime.now().hour
        return when (hour) {
            in 5..11 -> "Dzień dobry"
            in 12..17 -> "Dobry dzień"
            in 18..22 -> "Dobry wieczór"
            else -> "Dobrej nocy"
        }
    }

    private fun nextGoWindow(outlook: List<HourlyOutlook>): String? {
        val goStart = outlook.indexOfFirst { it.verdict == Verdict.GO }
        if (goStart < 0) return null
        var len = 0
        for (i in goStart until outlook.size) {
            if (outlook[i].verdict == Verdict.GO) len++ else break
        }
        if (len < 2) return null
        val start = outlook[goStart].timeLocal
        val end = outlook[goStart + len - 1].timeLocal
        return "Następne dobre okno pomiędzy ${"%02d:00".format(start.hour)} a ${"%02d:00".format((end.hour + 1) % 24)}."
    }
}
