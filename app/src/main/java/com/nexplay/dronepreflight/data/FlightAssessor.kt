package com.nexplay.dronepreflight.data

object FlightAssessor {

    fun assess(snap: AggregatedSnapshot, limits: DroneLimits): FlightAssessment {
        val checks = mutableListOf<ConditionCheck>()

        // Wiatr — mediana z X źródeł
        val windMedian = snap.wind.median
        val gustMedian = snap.gust.median
        val windCount = snap.wind.count
        val windValue = buildString {
            append(windMedian?.let { "%.1f m/s".format(it) } ?: "—")
            if (gustMedian != null) append(" (porywy %.1f m/s)".format(gustMedian))
            append("  ${windDirectionCardinal(snap.windDir.median)}")
        }
        val windVerdict = when {
            windMedian == null -> Verdict.CAUTION
            windMedian >= limits.maxWindMs -> Verdict.NO_GO
            windMedian >= limits.maxWindMs * 0.75 -> Verdict.CAUTION
            gustMedian != null && gustMedian >= limits.maxWindMs -> Verdict.NO_GO
            snap.wind.stddev != null && snap.wind.stddev > 3.0 -> Verdict.CAUTION
            else -> Verdict.GO
        }
        checks += ConditionCheck(
            label = "Wiatr",
            value = windValue,
            verdict = windVerdict,
            note = "Limit BSP: %.1f m/s".format(limits.maxWindMs),
            agreement = agreementRange("m/s", snap.wind, windCount),
        )

        // Temperatura
        val tempMedian = snap.temp.median
        val tempVerdict = when {
            tempMedian == null -> Verdict.CAUTION
            tempMedian < limits.minTempC || tempMedian > limits.maxTempC -> Verdict.NO_GO
            tempMedian < limits.minTempC + 3 || tempMedian > limits.maxTempC - 3 -> Verdict.CAUTION
            else -> Verdict.GO
        }
        checks += ConditionCheck(
            label = "Temperatura",
            value = tempMedian?.let { "%.1f °C".format(it) } ?: "—",
            verdict = tempVerdict,
            note = "Zakres BSP: %.0f°C – %.0f°C".format(limits.minTempC, limits.maxTempC),
            agreement = agreementRange("°C", snap.temp, snap.temp.count),
        )

        // Opady
        val precMedian = snap.precip.median ?: 0.0
        val precVerdict = when {
            precMedian <= 0.0 -> Verdict.GO
            precMedian < 0.5 -> Verdict.CAUTION
            else -> Verdict.NO_GO
        }
        checks += ConditionCheck(
            label = "Opady",
            value = if (precMedian > 0) "%.1f mm/h".format(precMedian) else "brak",
            verdict = precVerdict,
            note = "Zwilgocenie wpływa na silniki i widoczność",
            agreement = agreementRange("mm/h", snap.precip, snap.precip.count),
        )

        // Widzialność (nie wszystkie źródła podają)
        val visMedian = snap.visibility.median
        val visVerdict = when {
            visMedian == null -> Verdict.CAUTION
            visMedian < 500 -> Verdict.NO_GO
            visMedian < 2000 -> Verdict.CAUTION
            else -> Verdict.GO
        }
        checks += ConditionCheck(
            label = "Widzialność",
            value = visMedian?.let { "%.0f m".format(it) } ?: "brak danych",
            verdict = visVerdict,
            note = "VLOS: musisz widzieć BSP gołym okiem",
            agreement = if (snap.visibility.count > 0)
                "%d/%d źródeł podało widzialność".format(snap.visibility.count, snap.successfulSources)
            else null,
        )

        // Burza — głosowanie (ile źródeł wykryło)
        val stormVotes = snap.stormVotes
        val fogVotes = snap.fogVotes
        val phenomenonLabel = when {
            stormVotes > 0 -> "Burza (${stormVotes}/${snap.successfulSources} źródeł)"
            fogVotes > 0 -> "Mgła (${fogVotes}/${snap.successfulSources} źródeł)"
            else -> "Bez zagrożeń"
        }
        val phenomenonVerdict = when {
            stormVotes >= 2 -> Verdict.NO_GO
            stormVotes == 1 -> Verdict.CAUTION
            fogVotes >= 2 -> Verdict.CAUTION
            else -> Verdict.GO
        }
        checks += ConditionCheck(
            label = "Warunki atmosferyczne",
            value = phenomenonLabel,
            verdict = phenomenonVerdict,
            note = "≥2 głosy = wysokie ryzyko",
        )

        // KP index — mediana z (max) 5 źródeł, z zakresem min–max
        val kp = snap.kpIndex
        val kpVerdict = when {
            kp == null -> Verdict.CAUTION
            kp >= 6 -> Verdict.NO_GO
            kp >= 5 -> Verdict.CAUTION
            else -> Verdict.GO
        }
        val kpValues = snap.kpReadings.map { it.value }
        val kpAgreement = when {
            kpValues.isEmpty() -> "0/${snap.kpTotalSources} źródeł"
            kpValues.size == 1 -> "${snap.kpTotalSources}: ${"%.1f".format(kpValues.first())}"
            else -> "${kpValues.size}/${snap.kpTotalSources} źródeł: %.1f – %.1f".format(
                kpValues.min(), kpValues.max()
            )
        }
        checks += ConditionCheck(
            label = "KP index",
            value = kp?.let { "%.1f".format(it) } ?: "—",
            verdict = kpVerdict,
            note = "≥5 – możliwe zakłócenia GNSS/łączności",
            agreement = kpAgreement,
        )

        // Zachmurzenie (informacyjnie)
        checks += ConditionCheck(
            label = "Zachmurzenie",
            value = snap.cloud.median?.let { "%.0f%%".format(it) } ?: "—",
            verdict = Verdict.GO,
            agreement = agreementRange("%", snap.cloud, snap.cloud.count),
        )

        // Ocena łączna — dodatkowy warning gdy mało źródeł
        val baseVerdict = when {
            checks.any { it.verdict == Verdict.NO_GO } -> Verdict.NO_GO
            checks.any { it.verdict == Verdict.CAUTION } -> Verdict.CAUTION
            else -> Verdict.GO
        }
        val overall = when {
            snap.successfulSources == 0 -> Verdict.NO_GO
            snap.successfulSources <= 2 && baseVerdict == Verdict.GO -> Verdict.CAUTION
            else -> baseVerdict
        }
        return FlightAssessment(overall, checks)
    }

    private fun agreementRange(unit: String, stat: SourceStat, count: Int): String? {
        if (count == 0) return null
        val min = stat.min ?: return "$count źródeł"
        val max = stat.max ?: return "$count źródeł"
        // Uwaga: format() na String template z $unit załamuje się, gdy unit zawiera '%'.
        // Formatujemy liczby osobno, potem interpolujemy jako zwykły tekst.
        val minStr = "%.1f".format(min)
        val maxStr = "%.1f".format(max)
        return if (min == max) "$count źródeł: $minStr $unit"
        else "$count źródeł: $minStr – $maxStr $unit"
    }
}
