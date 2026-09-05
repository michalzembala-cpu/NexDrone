package com.nexplay.dronepreflight.assistant

import com.nexplay.dronepreflight.data.Verdict
import com.nexplay.dronepreflight.data.formatTemp
import com.nexplay.dronepreflight.data.formatWind
import com.nexplay.dronepreflight.data.windDirectionCardinal
import com.nexplay.dronepreflight.ui.PreflightChecklist
import com.nexplay.dronepreflight.ui.PreflightViewModel

enum class ToolKind {
    /** Odpowiada na pytanie, niczego nie zmienia. */
    READ,

    /** Rusza UI albo odświeża dane — cofalne jednym tapnięciem. */
    ACTION,

    /** Zapisuje coś na stałe — zawsze po głosowym potwierdzeniu. */
    WRITE,
}

data class ToolParam(
    val name: String,
    val description: String,
    /** Gdy niepuste, wartość musi być z tej listy — inaczej wraca [default]. */
    val allowed: List<String> = emptyList(),
    val required: Boolean = false,
    val default: String = "",
)

data class AssistantTool(
    val name: String,
    val description: String,
    val kind: ToolKind = ToolKind.READ,
    val params: List<ToolParam> = emptyList(),
    val run: (Map<String, String>) -> String,
    /** Tylko dla WRITE: pytanie zadawane, zanim cokolwiek zostanie zapisane. */
    val preview: ((Map<String, String>) -> String)? = null,
)

/**
 * Wszystko, co asystent może zrobić — jedno miejsce dla obu mózgów. Offline dopasowuje frazę,
 * model wybiera narzędzie po nazwie; żaden z nich nie sięga do stanu aplikacji inaczej niż tędy.
 *
 * Klasa bierze ViewModel, a nie surowe repozytoria, celowo: pytanie „czy mogę lecieć” ma dostać
 * dokładnie tę ocenę, którą user widzi na ekranie, a nie drugą, policzoną równolegle i mogącą
 * się z nią rozjechać.
 */
class AssistantTools(private val vm: PreflightViewModel) {

    private fun verdictWord(v: Verdict): String = when (v) {
        Verdict.GO -> "można lecieć"
        Verdict.CAUTION -> "ostrożnie"
        Verdict.NO_GO -> "nie lataj"
    }

    private fun noData() = "Nie mam jeszcze danych pogodowych. Powiedz „odśwież”, żeby je pobrać."

    fun find(name: String): AssistantTool? =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * Ani przesłyszenie, ani model nie są zaufane co do wartości parametru: cokolwiek wypada
     * poza dozwolony zbiór, wraca do wartości domyślnej, zanim narzędzie w ogóle ruszy.
     */
    fun sanitize(tool: AssistantTool, args: Map<String, String>): Map<String, String> {
        val out = args.toMutableMap()
        for (p in tool.params) {
            if (p.allowed.isEmpty()) continue
            val v = out[p.name]
            if (v == null || v !in p.allowed) {
                out[p.name] = p.default.ifEmpty { p.allowed.first() }
            }
        }
        return out
    }

    val all: List<AssistantTool> = listOf(

        // ---------- odczyty ----------
        AssistantTool(
            name = "get_flyability",
            description = "Czy w wybranej godzinie można latać, i co ewentualnie stoi na przeszkodzie.",
            kind = ToolKind.READ,
            run = {
                val a = vm.state.value.assessment
                if (a == null) noData()
                else {
                    val blockers = a.checks
                        .filter { it.verdict != Verdict.GO }
                        .joinToString(", ") { "${it.label.lowercase()} ${it.value}" }
                    if (blockers.isEmpty()) "Warunki na GO — ${verdictWord(a.overall)}. Wszystkie parametry w normie."
                    else "Ocena: ${verdictWord(a.overall)}. Uwaga na: $blockers."
                }
            },
        ),

        AssistantTool(
            name = "get_conditions",
            description = "Skrót warunków: wiatr, porywy, temperatura, opady.",
            kind = ToolKind.READ,
            run = {
                val s = vm.state.value
                val snap = s.snapshot
                if (snap == null) noData()
                else {
                    val u = s.units
                    val wind = formatWind(snap.wind.median, u.wind)
                    val gust = formatWind(snap.gust.median, u.wind)
                    val temp = formatTemp(snap.temp.median, u.temp)
                    "W ${snap.locationName}: wiatr $wind, porywy $gust, temperatura $temp."
                }
            },
        ),

        AssistantTool(
            name = "get_wind",
            description = "Sam wiatr: średnia, porywy i kierunek.",
            kind = ToolKind.READ,
            run = {
                val s = vm.state.value
                val snap = s.snapshot ?: return@AssistantTool noData()
                val u = s.units
                val dir = snap.windDir.median?.let { " z kierunku ${windDirectionCardinal(it)}" } ?: ""
                val limit = s.limits.maxWindMs
                val over = (snap.wind.median ?: 0.0) > limit
                val tail = if (over) " To powyżej limitu Twojego drona." else ""
                "Wiatr ${formatWind(snap.wind.median, u.wind)}, w porywach ${formatWind(snap.gust.median, u.wind)}$dir.$tail"
            },
        ),

        AssistantTool(
            name = "get_kp",
            description = "Indeks Kp — aktywność geomagnetyczna, wpływa na kompas i GPS.",
            kind = ToolKind.READ,
            run = {
                val snap = vm.state.value.snapshot ?: return@AssistantTool noData()
                val kp = snap.kpIndex
                if (kp == null) "Nie mam odczytu Kp."
                else {
                    val note = when {
                        kp >= 5 -> " To dużo — spodziewaj się problemów z kompasem."
                        kp >= 4 -> " Podwyższone, miej oko na kompas."
                        else -> " Spokojnie."
                    }
                    "Kp wynosi %.1f.".format(kp) + note
                }
            },
        ),

        AssistantTool(
            name = "get_best_window",
            description = "Najlepsze okno na lot w wybranym dniu.",
            kind = ToolKind.READ,
            run = {
                val w = vm.state.value.bestWindow
                if (w == null) "Nie widzę dziś dobrego okna na lot."
                else "Najlepsze okno: od ${w.startLocal.hour}:00 do ${w.endLocal.hour}:00, czyli ${w.hours} godzin."
            },
        ),

        AssistantTool(
            name = "get_checklist",
            description = "Ile pozycji checklisty przedlotowej zostało do odhaczenia.",
            kind = ToolKind.READ,
            run = {
                val done = vm.state.value.checked
                val total = PreflightChecklist.sumOf { it.items.size }
                val left = total - done.size
                if (left <= 0) "Checklista kompletna — wszystkie $total pozycji odhaczone."
                else {
                    val next = PreflightChecklist.flatMap { it.items }.firstOrNull { it.id !in done }
                    val hint = next?.let { " Następna: ${it.title}." } ?: ""
                    "Zostało $left z $total pozycji.$hint"
                }
            },
        ),

        AssistantTool(
            name = "get_last_flight",
            description = "Ostatni lot zapisany w dzienniku.",
            kind = ToolKind.READ,
            run = {
                val f = vm.state.value.flightLog.maxByOrNull { it.timestamp }
                if (f == null) "Dziennik lotów jest pusty."
                else {
                    val mins = f.durationMinutes?.let { ", $it minut" } ?: ""
                    val note = if (f.note.isBlank()) "" else " Notatka: ${f.note}."
                    "Ostatni lot: ${f.locationName}, ocena ${f.verdict}$mins.$note"
                }
            },
        ),

        // ---------- akcje ----------
        AssistantTool(
            name = "refresh",
            description = "Pobiera świeżą pogodę ze wszystkich źródeł.",
            kind = ToolKind.ACTION,
            run = {
                vm.refresh()
                "Odświeżam pogodę."
            },
        ),

        AssistantTool(
            name = "set_hour",
            description = "Ustawia godzinę, dla której liczona jest ocena lotu.",
            kind = ToolKind.ACTION,
            params = listOf(
                ToolParam("hour", "godzina 0-23", required = true, default = "12"),
            ),
            run = { args ->
                val h = args["hour"]?.toIntOrNull()?.coerceIn(0, 23)
                if (h == null) "Nie zrozumiałem godziny."
                else {
                    vm.setSelectedHour(h)
                    "Ustawiam $h:00."
                }
            },
        ),

        // ---------- zapisy (zawsze po potwierdzeniu) ----------
        AssistantTool(
            name = "log_flight",
            description = "Zapisuje bieżące warunki jako lot w dzienniku.",
            kind = ToolKind.WRITE,
            params = listOf(
                ToolParam("note", "notatka do lotu, własnymi słowami"),
                ToolParam("minutes", "czas lotu w minutach"),
            ),
            preview = { args ->
                val note = args["note"].orEmpty()
                val mins = args["minutes"]?.toIntOrNull()
                val part = listOfNotNull(
                    mins?.let { "$it minut" },
                    note.ifBlank { null },
                ).joinToString(", ")
                if (part.isBlank()) "Zapisać bieżące warunki jako lot?"
                else "Zapisać lot ($part)?"
            },
            run = { args ->
                vm.saveCurrentFlight(args["note"].orEmpty(), args["minutes"]?.toIntOrNull())
                "Zapisane w dzienniku."
            },
        ),

        AssistantTool(
            name = "check_item",
            description = "Odhacza pozycję checklisty przedlotowej. Dopasowuje po fragmencie nazwy.",
            kind = ToolKind.WRITE,
            params = listOf(
                ToolParam("text", "fragment nazwy pozycji", required = true),
            ),
            preview = { args ->
                val hit = matchItem(args["text"].orEmpty())
                if (hit == null) "Nie znalazłem takiej pozycji na checkliście."
                else "Odhaczyć: ${hit.title}?"
            },
            run = { args ->
                val hit = matchItem(args["text"].orEmpty())
                if (hit == null) "Nie znalazłem takiej pozycji na checkliście."
                else {
                    vm.toggleChecklistItem(hit.id, true)
                    "Odhaczone: ${hit.title}."
                }
            },
        ),

        AssistantTool(
            name = "reset_checklist",
            description = "Czyści całą checklistę przedlotową.",
            kind = ToolKind.WRITE,
            preview = { "Wyczyścić całą checklistę? Wszystkie odhaczenia znikną." },
            run = {
                vm.resetChecklist()
                "Checklista wyczyszczona."
            },
        ),
    )

    private fun matchItem(needle: String) =
        needle.trim().takeIf { it.isNotEmpty() }?.let { n ->
            PreflightChecklist.flatMap { it.items }
                .firstOrNull { it.title.contains(n, ignoreCase = true) || it.id.equals(n, ignoreCase = true) }
        }
}
