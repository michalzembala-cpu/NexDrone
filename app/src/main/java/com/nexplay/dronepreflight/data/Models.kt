package com.nexplay.dronepreflight.data

data class DroneLimits(
    val maxWindMs: Double = 10.0,
    val minTempC: Double = 0.0,
    val maxTempC: Double = 40.0,
)

enum class Verdict { GO, CAUTION, NO_GO }

data class ConditionCheck(
    val label: String,
    val value: String,
    val verdict: Verdict,
    val note: String? = null,
    val agreement: String? = null,
)

data class FlightAssessment(
    val overall: Verdict,
    val checks: List<ConditionCheck>,
)

fun windDirectionCardinal(deg: Double?): String {
    if (deg == null) return "—"
    val dirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val ix = ((deg / 22.5) + 0.5).toInt().let { ((it % 16) + 16) % 16 }
    return dirs[ix]
}
