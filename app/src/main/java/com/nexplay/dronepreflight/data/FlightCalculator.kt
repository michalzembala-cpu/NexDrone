package com.nexplay.dronepreflight.data

/** Kalkulator lotu — dystans/wiatr/bateria → czas i marginesy. */
object FlightCalculator {

    data class Result(
        val outboundMin: Double,      // minuty w jedną stronę (pod wiatr)
        val returnMin: Double,        // powrót (z wiatrem)
        val totalMin: Double,
        val batteryUsedPct: Double,   // % baterii zużyty
        val batteryReserve: Double,   // % rezerwa RTH
        val verdict: String,          // "OK" / "MARGINES" / "NIE"
        val reasoning: String,
    )

    /**
     * @param distanceKm — łączny dystans TAM (nie tam-i-nazad)
     * @param windMs — obecny wiatr
     * @param droneMaxSpeedMs — max prędkość drona (np. Mini 4 Pro = 16 m/s, Mavic 3 = 21)
     * @param flightTimeMin — nominalny czas lotu drona przy pełnej baterii
     * @param batteryPct — obecna bateria %
     * @param rthReservePct — rezerwa RTH % (typowo 20-30%)
     */
    fun compute(
        distanceKm: Double,
        windMs: Double,
        droneMaxSpeedMs: Double = 16.0,
        flightTimeMin: Double = 30.0,
        batteryPct: Double = 100.0,
        rthReservePct: Double = 25.0,
    ): Result {
        // Efektywna prędkość — używamy ~50% max (realistyczna dla sensownego filmowania)
        val cruiseSpeed = droneMaxSpeedMs * 0.5

        // Prędkość w jedną i drugą stronę
        val outboundSpeed = (cruiseSpeed - windMs).coerceAtLeast(1.0) // w cel = pod wiatr
        val returnSpeed = cruiseSpeed + windMs                        // z wiatrem

        val outboundSec = (distanceKm * 1000) / outboundSpeed
        val returnSec = (distanceKm * 1000) / returnSpeed

        val outboundMin = outboundSec / 60.0
        val returnMin = returnSec / 60.0
        val totalMin = outboundMin + returnMin

        val batteryPerMin = 100.0 / flightTimeMin  // % na minutę
        val batteryUsed = totalMin * batteryPerMin
        val batteryReserve = batteryPct - batteryUsed

        val (verdict, reasoning) = when {
            batteryReserve < rthReservePct -> {
                val short = rthReservePct - batteryReserve
                "NIE" to "Za mało baterii — zabraknie ok. ${"%.0f".format(short)}% do rezerwy RTH."
            }
            batteryReserve < rthReservePct + 10 -> {
                "MARGINES" to "Zmieścisz się, ale rezerwa wąska (%.0f%%). Zawracaj wcześniej.".format(batteryReserve)
            }
            else -> {
                "OK" to "Zapas ${"%.0f".format(batteryReserve)}% — bezpiecznie."
            }
        }

        return Result(
            outboundMin = outboundMin,
            returnMin = returnMin,
            totalMin = totalMin,
            batteryUsedPct = batteryUsed,
            batteryReserve = batteryReserve,
            verdict = verdict,
            reasoning = reasoning,
        )
    }
}
