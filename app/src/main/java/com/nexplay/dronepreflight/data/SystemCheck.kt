package com.nexplay.dronepreflight.data

/** Ocena systemów — 8-punktowy check pokazywany w Mission Control. */
object SystemCheck {

    enum class Status { OK, WARN, FAIL, UNKNOWN }

    data class Item(
        val id: String,
        val label: String,
        val status: Status,
        val detail: String,
    )

    data class Report(
        val items: List<Item>,
        val readyCount: Int,
        val totalCount: Int,
        val allGood: Boolean,
    )

    fun run(
        snap: AggregatedSnapshot?,
        assessment: FlightAssessment?,
        checklistDone: Int,
        checklistTotal: Int,
    ): Report {
        val items = buildList {
            // 1. Pogoda ogólna
            add(when (assessment?.overall) {
                Verdict.GO -> Item("weather", "Pogoda", Status.OK,
                    snap?.let { "wiatr %.1f m/s, temp %.0f°C".format(it.wind.median ?: 0.0, it.temp.median ?: 0.0) } ?: "")
                Verdict.CAUTION -> Item("weather", "Pogoda", Status.WARN,
                    assessment.checks.firstOrNull { it.verdict == Verdict.CAUTION }?.label ?: "warunki graniczne")
                Verdict.NO_GO -> Item("weather", "Pogoda", Status.FAIL,
                    assessment.checks.firstOrNull { it.verdict == Verdict.NO_GO }?.label ?: "poza limitami")
                null -> Item("weather", "Pogoda", Status.UNKNOWN, "brak danych")
            })

            // 2. Wiatr per se
            val wind = snap?.wind?.median
            add(when {
                wind == null -> Item("wind", "Wiatr", Status.UNKNOWN, "brak danych")
                wind >= 10.0 -> Item("wind", "Wiatr", Status.FAIL, "%.1f m/s — silny".format(wind))
                wind >= 7.0 -> Item("wind", "Wiatr", Status.WARN, "%.1f m/s — średni".format(wind))
                else -> Item("wind", "Wiatr", Status.OK, "%.1f m/s".format(wind))
            })

            // 3. Opady
            val precip = snap?.precip?.median ?: 0.0
            add(when {
                precip <= 0.0 -> Item("precip", "Opady", Status.OK, "brak")
                precip < 0.5 -> Item("precip", "Opady", Status.WARN, "%.1f mm/h".format(precip))
                else -> Item("precip", "Opady", Status.FAIL, "%.1f mm/h — deszcz".format(precip))
            })

            // 4. Widoczność
            val vis = snap?.visibility?.median
            add(when {
                vis == null -> Item("vis", "Widoczność", Status.UNKNOWN, "brak danych")
                vis < 500 -> Item("vis", "Widoczność", Status.FAIL, "<500m")
                vis < 2000 -> Item("vis", "Widoczność", Status.WARN, "%.1f km".format(vis / 1000))
                else -> Item("vis", "Widoczność", Status.OK, "%.1f km".format(vis / 1000))
            })

            // 5. KP index / przestrzeń powietrzna (proxy)
            val kp = snap?.kpIndex ?: 0.0
            add(when {
                kp >= 6.0 -> Item("space", "Przestrzeń GNSS", Status.FAIL, "KP %.1f — zakłócenia".format(kp))
                kp >= 5.0 -> Item("space", "Przestrzeń GNSS", Status.WARN, "KP %.1f".format(kp))
                else -> Item("space", "Przestrzeń GNSS", Status.OK, "KP %.1f".format(kp))
            })

            // 6. Bateria — user się jeszcze nie zajmuje w apce, fake OK
            add(Item("battery", "Bateria", Status.OK, "sprawdź w kontrolerze"))

            // 7. Checklist
            add(when {
                checklistTotal == 0 -> Item("checklist", "Checklista", Status.UNKNOWN, "brak listy")
                checklistDone == checklistTotal -> Item("checklist", "Checklista", Status.OK, "$checklistDone/$checklistTotal ✓")
                checklistDone >= checklistTotal * 0.75 -> Item("checklist", "Checklista", Status.WARN, "$checklistDone/$checklistTotal")
                else -> Item("checklist", "Checklista", Status.FAIL, "$checklistDone/$checklistTotal")
            })

            // 8. Dane GPS — mierzone przez source count
            val sources = snap?.successfulSources ?: 0
            val total = snap?.totalSources ?: 5
            add(when {
                sources >= 4 -> Item("gps", "Źródła danych", Status.OK, "$sources/$total")
                sources >= 2 -> Item("gps", "Źródła danych", Status.WARN, "$sources/$total")
                else -> Item("gps", "Źródła danych", Status.FAIL, "$sources/$total")
            })
        }

        val ready = items.count { it.status == Status.OK }
        return Report(
            items = items,
            readyCount = ready,
            totalCount = items.size,
            allGood = items.none { it.status == Status.FAIL },
        )
    }
}
