package com.nexplay.dronepreflight.data

import org.shredzone.commons.suncalc.SunPosition
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import kotlin.math.ceil
import kotlin.math.max

/**
 * Plan krótkiego filmu (rolka 15–20 s) pod konkretnego drona i dzisiejsze warunki.
 * Dla każdego ujęcia: tryb z DJI Fly (albo "ręcznie"), wysokość, odległość,
 * prędkość, gimbal, kroki i miejsce — z kierunkiem względem słońca i wiatru.
 */
object FilmPlanner {

    data class ShotPlan(
        val num: Int,
        val emoji: String,
        val title: String,
        val purpose: String,
        val mode: String,           // np. "QuickShot › Circle" albo "Brak — ręcznie"
        val auto: Boolean,          // true = dron leci sam
        val height: String,
        val distance: String,
        val speed: String,
        val gimbal: String,
        val clipSec: Int,
        val where: String,
        val steps: List<String>,
        val tips: List<String>,
        val verdict: Verdict,
        val verdictNote: String,
    )

    data class FilmPlan(
        val model: DroneCatalog.Model,
        val overall: Verdict,
        val headline: String,
        val whenText: String,
        val whereText: String,
        val sunDirText: String,
        val windText: String,
        val batteriesText: String,
        val cameraSettings: List<Pair<String, String>>,
        val shots: List<ShotPlan>,
        val rules: List<String>,
        val warnings: List<String>,
    )

    private data class Window(val label: String, val date: LocalDate, val start: LocalTime, val end: LocalTime)

    fun plan(model: DroneCatalog.Model, snap: AggregatedSnapshot, nowMillis: Long = System.currentTimeMillis()): FilmPlan {
        val zone = ZoneId.systemDefault()
        val wind = snap.wind.median ?: 0.0
        val gust = snap.gust.median ?: wind
        val windDir = snap.windDir.median
        val temp = snap.temp.median
        val visibility = snap.visibility.median ?: 10_000.0

        // ── Kiedy: najbliższa złota godzina ──
        val window = pickWindow(snap.latitude, snap.longitude, nowMillis, zone)
        val mid = window?.let { it.start.plusSeconds(java.time.Duration.between(it.start, it.end).seconds / 2) }
        val sunAz: Double? = if (window != null && mid != null) {
            runCatching {
                val at = Date.from(window.date.atTime(mid).atZone(zone).toInstant())
                SunPosition.compute().on(at).at(snap.latitude, snap.longitude).timezone(zone).execute().azimuth
            }.getOrNull()
        } else null
        val sunDir = sunAz?.let { dirPl(it) } ?: "zachód"
        val shadowDir = sunAz?.let { dirPl((it + 180) % 360) } ?: "wschód"

        val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val whenText = window?.let { "${it.label} ${it.start.format(fmt)}–${it.end.format(fmt)} (złota godzina). Bądź na miejscu 20 min wcześniej." }
            ?: "Brak złotej godziny w tej lokalizacji — leć przy niskim słońcu."

        // ── Wiatr: dron i wysokość ──
        val effective = max(wind, gust * 0.85)   // porywy ponad limit = odpuść
        val ratio = effective / model.maxWindMs
        val k = max(model.maxWindMs / 10.7, 0.85)                   // skala: Neo trzyma gorzej niż Mini
        val windyHigh = wind * 1.3                          // na 50+ m wieje zwykle ~30% mocniej
        val windFrom = windDir?.let { dirPl(it) }
        val windText = buildString {
            append("Wiatr %.1f m/s, porywy %.1f m/s".format(wind, gust))
            if (windFrom != null) append(", wieje z kierunku: $windFrom")
            append(". Limit ${model.name}: %.1f m/s.".format(model.maxWindMs))
            if (windFrom != null) append(" Odlatuj pod wiatr (w stronę: $windFrom), wracaj z wiatrem — oszczędzasz baterię na powrót.")
        }

        // ── Baterie ──
        val coldFactor = when {
            temp == null -> 1.0
            temp < 5 -> 0.7
            temp < 10 -> 0.85
            else -> 1.0
        }
        val usableMin = model.flightMin * coldFactor * 0.7   // ląduj przy ~30%
        val needMin = 22.0                                     // 4–5 ujęć × 3 podejścia + dolot
        val batteries = ceil(needMin / usableMin).toInt().coerceAtLeast(1)
        val batteriesText = "Weź $batteries ${plural(batteries, "baterię", "baterie", "baterii")}: plan to ~22 min lotu, " +
            "a jedna bateria da realnie ~%.0f min".format(usableMin) +
            (if (temp != null) " przy %.0f°C".format(temp) else "") + " (lądujesz przy 30%)." +
            (if (coldFactor < 1.0) " Trzymaj je w kieszeni kurtki do startu." else "")

        // ── Ujęcia ──
        val shots = mutableListOf<ShotPlan>()
        val neo = model.id == "neo"
        val noAvoid = !model.hasObstacleAvoidance

        fun gate(shotId: String): Pair<Verdict, String> {
            val base = ShotPlanner.Shots.first { it.id == shotId }
            val maxMs = base.maxWindMs * k
            val idealMs = base.idealWindMs * k
            val notes = mutableListOf<String>()
            var v = Verdict.GO
            if (wind > maxMs) { v = Verdict.NO_GO; notes += "za mocno wieje dla tego ujęcia (> %.1f m/s)".format(maxMs) }
            else if (wind > idealMs) { v = Verdict.CAUTION; notes += "wiatr będzie trochę bujał obrazem" }
            if (base.needsLowGust && gust > idealMs * 1.6 && v != Verdict.NO_GO) { v = Verdict.CAUTION; notes += "porywy %.1f m/s".format(gust) }
            if (base.needsVisibility && visibility < 5_000 && v == Verdict.GO) { v = Verdict.CAUTION; notes += "mgła/słaba widoczność" }
            return v to (if (notes.isEmpty()) "warunki dobre" else notes.joinToString(", "))
        }

        // 1. REVEAL
        run {
            val (v, note) = gate("reveal")
            val endH = if (windyHigh > 6) "20 m" else "25–35 m"
            shots += ShotPlan(
                num = 1, emoji = "🎬", title = "REVEAL — otwarcie",
                purpose = "Zaczynasz za przeszkodą i odsłaniasz widok. Najmocniejszy początek rolki.",
                mode = "Brak — ręcznie", auto = false,
                height = "start 2–3 m → koniec $endH",
                distance = "start 3–5 m za przeszkodą, przelot ~40 m",
                speed = if (neo) "najwolniej, jak się da (tryb Cine z kontrolerem)" else "tryb Cine, ok. 2–3 m/s",
                gimbal = "0° (prosto) → −10° pod koniec",
                clipSec = 5,
                where = "Za linią drzew, budynkiem, murem albo skarpą. Odsłaniaj widok w stronę: $sunDir " +
                    "(pod słońce — sylwetki i flary) albo w stronę: $shadowDir (słońce za dronem — mocne kolory).",
                steps = listOf(
                    "Ustaw drona nisko (2–3 m), tuż za przeszkodą — kamera patrzy prosto w nią.",
                    "Włącz nagrywanie i odczekaj 2 s.",
                    "Jednocześnie lekko do przodu (prawy drążek) i w górę (lewy drążek).",
                    "Gdy widok się odsłoni, puść wznoszenie i leć jeszcze 2 s do przodu.",
                    "Wyhamuj płynnie, odczekaj 2 s, zatrzymaj nagrywanie. Powtórz 3 razy.",
                ),
                tips = buildList {
                    if (noAvoid) add("${model.name} nie widzi przeszkód z przodu — trzymaj min. 3 m od gałęzi.")
                    if (neo) add("Bez kontrolera: nagraj Dronie i odwróć klip w montażu — wyjdzie odwrotny reveal.")
                    if (model.waypoints) add("Udany przelot zapisz jako Waypoints — powtórzysz go identycznie.")
                },
                verdict = v, verdictNote = note,
            )
        }

        // 2. ORBITA
        run {
            val (v, note) = gate("orbit")
            val hasCircle = "Circle" in model.quickShots
            val useManual = !hasCircle || v == Verdict.NO_GO || wind > 6 * k
            shots += ShotPlan(
                num = 2, emoji = "🌀", title = "ORBITA — wokół obiektu",
                purpose = "Krążysz wokół jednego obiektu — pokazuje go z każdej strony.",
                mode = when {
                    !useManual -> "QuickShot › Circle"
                    model.activeTrack != null && !neo -> "${model.activeTrack} › Point of Interest (albo ręcznie)"
                    else -> "Brak — ręcznie"
                },
                auto = !useManual,
                height = if (neo) "4–8 m" else "10–20 m",
                distance = if (neo) "promień ustawia Circle — wybierz mały obiekt (osoba, auto)" else "promień 15–25 m od obiektu",
                speed = if (useManual) "bardzo wolno, ok. 1–2 m/s" else "dron sam — wybierz kierunek obrotu",
                gimbal = "ok. −15°, obiekt na środku kadru",
                clipSec = 4,
                where = "Pojedynczy obiekt z wolnym okręgiem wokół: drzewo na polu, kapliczka, wieża, Ty przy aucie. " +
                    "Najładniej, gdy słońce świeci z boku obiektu (od strony: $sunDir).",
                steps = if (!useManual) listOf(
                    "Ustaw drona na wysokości i odległości z planu, obiekt na środku.",
                    "Wybierz QuickShots › Circle, zaznacz obiekt palcem (ramka).",
                    "Wybierz kierunek obrotu i naciśnij Start — dron nagrywa sam.",
                    "Ręce przy drążkach: ruch drążkiem przerywa manewr, gdyby coś się działo.",
                ) else listOf(
                    "Ustaw obiekt na środku kadru, dron 15–25 m od niego.",
                    "Prawy drążek delikatnie w bok (lot bokiem).",
                    "Lewy drążek w przeciwną stronę (obrót) — tyle, żeby obiekt nie uciekał ze środka.",
                    "Trzymaj stały nacisk na obu drążkach przez całą orbitę. Ćwicz 2–3 razy.",
                ),
                tips = buildList {
                    if (useManual && hasCircle) add("Przy tym wietrze QuickShot szarpie obrazem — ręcznie wyjdzie równiej.")
                    if (noAvoid) add("Przed startem obejrzyj cały okrąg — dron poleci bokiem i do tyłu bez czujników.")
                },
                verdict = v, verdictNote = note,
            )
        }

        // 3. TOP-DOWN
        run {
            val (v, note) = gate("topdown")
            shots += ShotPlan(
                num = 3, emoji = "🔽", title = "TOP-DOWN — z góry",
                purpose = "Kamera pionowo w dół — wzory i cienie, które widać tylko z powietrza.",
                mode = if (model.hyperlapse) "Brak — ręcznie (albo Hyperlapse › Free)" else "Brak — ręcznie",
                auto = false,
                height = if (windyHigh > 6) "30 m (wyżej mocniej wieje)" else "40–60 m",
                distance = "dokładnie nad obiektem",
                speed = "obrót ok. 10°/s albo wznoszenie 1–2 m/s",
                gimbal = "−90° (pionowo w dół)",
                clipSec = 3,
                where = "Linie i wzory: droga z autami, rzeka, tory, rząd drzew, pole z długimi cieniami. " +
                    "O złotej godzinie cienie padają w stronę: $shadowDir — ustaw je po przekątnej kadru.",
                steps = buildList {
                    add("Podleć nad środek obiektu na wysokość z planu.")
                    add(if (neo) "Przeciągnij suwak gimbala na sam dół (−90°)." else "Gimbal w dół do −90° (kółko na kontrolerze).")
                    if (model.trueVertical) add("Do rolki 9:16 obróć kamerę do pionu (True Vertical).")
                    add("Nagrywaj: powoli obracaj (lewy drążek w bok) albo powoli się wznoś.")
                    add("Ruch ma być ledwo widoczny — z góry każde drgnięcie wygląda na szybkie.")
                },
                tips = buildList {
                    if (model.hyperlapse) add("Hyperlapse › Free z gimbalem −90° daje przyspieszone, płynące cienie i auta.")
                    if (gust > 5 * k) add("Porywy będą bujać obrazem z góry — w montażu użyj stabilizacji.")
                },
                verdict = v, verdictNote = note,
            )
        }

        // 4. DRONIE
        run {
            val (v, note) = gate("pullaway")
            val hasDronie = "Dronie" in model.quickShots
            shots += ShotPlan(
                num = 4, emoji = "🚀", title = "DRONIE — zakończenie",
                purpose = "Dron filmuje Ciebie i odlatuje do tyłu i w górę. Klasyczny koniec rolki.",
                mode = if (hasDronie) "QuickShot › Dronie" else "Brak — ręcznie (do tyłu + w górę)",
                auto = hasDronie,
                height = "start na wysokości oczu → koniec ok. 30 m",
                distance = if (neo) "start 3 m przed Tobą, dystans ustaw w aplikacji (dłuższy = bardziej filmowo)"
                    else "start 3–5 m przed Tobą, dystans 40–60 m",
                speed = if (hasDronie) "dron sam" else "Cine, ok. 2 m/s",
                gimbal = "auto — trzyma Cię w kadrze",
                clipSec = 4,
                where = "Otwarte miejsce z min. 60 m wolnej przestrzeni za dronem (bez drzew i linii). " +
                    "Stań twarzą do słońca (w stronę: $sunDir) — światło na twarzy, a dron odleci w stronę słońca.",
                steps = buildList {
                    if (neo) add("Start z dłoni, Neo zawiśnie przed Tobą.")
                    else add("Postaw drona 3–5 m przed sobą, na wysokości oczu.")
                    if (hasDronie) {
                        add("Wybierz QuickShots › Dronie, zaznacz siebie ramką.")
                        add("Ustaw dystans i naciśnij Start.")
                    } else add("Nagrywaj i jednocześnie leć do tyłu i w górę, kamera na Tobie.")
                    add("Stój spokojnie (albo pomachaj na początku) — nie chodź, bo dron zgubi kadr.")
                },
                tips = buildList {
                    if (noAvoid) add("Dron leci tyłem na ślepo — sprawdź wzrokiem, co jest za nim, zanim naciśniesz Start.")
                },
                verdict = v, verdictNote = note,
            )
        }

        // 5. BONUS — co ten dron ma ekstra
        run {
            val (v, note) = gate("orbit")
            val bonus = when {
                model.masterShots -> Triple("MasterShots", "Dron sam nagra serię ujęć wokół obiektu i zmontuje ją do muzyki.", "promień i wysokość dobiera sam")
                "Helix" in model.quickShots -> Triple("QuickShot › Helix", "Spiralnie odlatuje i wznosi się wokół Ciebie — efektowna wersja Dronie.", "dystans 30–50 m")
                "Rocket" in model.quickShots -> Triple("QuickShot › Rocket", "Kamera w dół i pionowy start w górę nad Tobą.", "wysokość 30–40 m")
                else -> null
            }
            if (bonus != null) shots += ShotPlan(
                num = 5, emoji = "⭐", title = "BONUS — ${bonus.first}",
                purpose = bonus.second,
                mode = bonus.first, auto = true,
                height = if (neo) "dron sam (do ok. 30 m)" else "dron sam",
                distance = bonus.third,
                speed = "dron sam",
                gimbal = "auto",
                clipSec = 3,
                where = "To samo otwarte miejsce co Dronie — sprawdź przestrzeń wokół.",
                steps = listOf(
                    "Wybierz ${bonus.first}, zaznacz obiekt albo siebie.",
                    "Start i ręce przy drążkach — ruch drążkiem przerywa manewr.",
                ),
                tips = emptyList(),
                verdict = v, verdictNote = note,
            )
        }

        // ── Ustawienia kamery ──
        val cam = buildList {
            add("Rozdzielczość" to model.video)
            model.slowMo?.let { add("Zwolnione tempo" to "$it — do orbity i top-down") }
            add("Migawka" to "1/60 przy 30 kl./s (z filtrem ND). Bez ND zostaw auto.")
            add("Kolor" to if (model.logProfile) "${model.colorProfile} — kolory wyciągniesz w montażu" else model.colorProfile)
            add("Balans bieli" to "ręcznie 5500 K — kolor nie skacze między klipami")
            add("Ekspozycja" to "EV −0.3, żeby nie przepalić nieba")
            add("Format" to if (model.trueVertical) "rolka: tryb pionowy (True Vertical)" else "kręć poziomo, obiekt na środku — przytniesz do 9:16 w montażu")
        }

        // ── Werdykt ogólny ──
        val warnings = mutableListOf<String>()
        if (temp != null && temp < model.minTempC) warnings += "Za zimno dla ${model.name} (%.0f°C, minimum %.0f°C).".format(temp, model.minTempC)
        if ((snap.precip.median ?: 0.0) > 0.2) warnings += "Pada — DJI Mini/Neo nie są wodoodporne."
        if (snap.fogVotes > 0 && visibility < 2_000) warnings += "Mgła — dron może zniknąć Ci z oczu, a lot musi być w zasięgu wzroku."
        if (model.notes.isNotBlank()) warnings += model.notes

        val overall = when {
            ratio > 1.0 || warnings.any { it.startsWith("Za zimno") || it.startsWith("Pada") } -> Verdict.NO_GO
            ratio > 0.7 || shots.count { it.verdict == Verdict.NO_GO } >= 2 -> Verdict.CAUTION
            else -> Verdict.GO
        }
        val headline = when (overall) {
            Verdict.GO -> "Dobre warunki — leć i kręć cały plan."
            Verdict.CAUTION -> "Da się, ale ostrożnie — ujęcia z czerwoną oceną pomiń albo zrób niżej."
            Verdict.NO_GO -> "Dziś odpuść — warunki są poza możliwościami ${model.name}."
        }

        val rules = buildList {
            add("Maks. 120 m nad ziemią i zawsze w zasięgu wzroku.")
            if (model.weightG < 250) add("${model.name} (${model.weightG} g) to kategoria A1 — możesz przelatywać nad pojedynczymi osobami, ale nie nad zgromadzeniami.")
            add("Numer operatora z rejestracji w ULC musi być na dronie i wpisany w DJI Fly.")
            add("Przed startem sprawdź strefę w aplikacji DroneRadar.")
            add("Nie filmuj ludzi na prywatnych posesjach bez zgody.")
        }

        return FilmPlan(
            model = model,
            overall = overall,
            headline = headline,
            whenText = whenText,
            whereText = "${snap.locationName}. Każde ujęcie ma niżej opisane, jakiego miejsca szukać.",
            sunDirText = if (sunAz != null) "Słońce będzie od strony: $sunDir (azymut %.0f°), cienie padną w stronę: $shadowDir.".format(sunAz)
                else "Brak danych o słońcu.",
            windText = windText,
            batteriesText = batteriesText,
            cameraSettings = cam,
            shots = shots,
            rules = rules,
            warnings = warnings,
        )
    }

    private fun pickWindow(lat: Double, lon: Double, nowMillis: Long, zone: ZoneId): Window? {
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        val t = now.toLocalTime()
        val st = SunLight.compute(lat, lon, nowMillis)
        val mStart = st.goldenHourMorningStart; val mEnd = st.goldenHourMorningEnd
        val eStart = st.goldenHourEveningStart; val eEnd = st.goldenHourEveningEnd
        if (mStart != null && mEnd != null && t < mEnd) return Window(if (t >= mStart) "Teraz! Dziś rano" else "Dziś rano", today, mStart, mEnd)
        if (eStart != null && eEnd != null && t < eEnd) return Window(if (t >= eStart) "Teraz! Dziś wieczorem" else "Dziś wieczorem", today, eStart, eEnd)
        // Po zachodzie: jutro wieczorem (rano zwykle za wcześnie na wyjście z dronem)
        val tomorrow = SunLight.compute(lat, lon, nowMillis + 24L * 3600_000)
        val ts = tomorrow.goldenHourEveningStart; val te = tomorrow.goldenHourEveningEnd
        if (ts != null && te != null) return Window("Jutro wieczorem", today.plusDays(1), ts, te)
        return null
    }

    private fun dirPl(deg: Double): String {
        val names = listOf("północ", "północny wschód", "wschód", "południowy wschód",
            "południe", "południowy zachód", "zachód", "północny zachód")
        val ix = (((deg % 360) + 360) % 360 / 45.0 + 0.5).toInt() % 8
        return names[ix]
    }

    private fun plural(n: Int, one: String, few: String, many: String): String = when {
        n == 1 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
}
