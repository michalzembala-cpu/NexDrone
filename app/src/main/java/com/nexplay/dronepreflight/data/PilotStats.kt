package com.nexplay.dronepreflight.data

import java.util.Calendar

/** Poziom pilota + XP + odznaki liczone z FlightLog. */
object PilotStats {

    data class Stats(
        val totalFlights: Int,
        val totalMinutes: Int,
        val perfectFlights: Int,      // score >= 90
        val level: Int,
        val xp: Int,                   // XP w obecnym levelu (0-100)
        val xpNext: Int,               // XP do następnego (100)
        val uniqueLocations: Int,
        val avgScore: Int,
        val badges: List<Badge>,
    )

    fun compute(entries: List<FlightLogEntry>): Stats {
        val total = entries.size
        val totalMin = entries.mapNotNull { it.durationMinutes }.sum()
        val perfect = entries.count { (it.score ?: 0) >= 90 }
        val unique = entries.map { it.locationName }.distinct().size
        val avg = entries.mapNotNull { it.score }.let {
            if (it.isEmpty()) 0 else it.average().toInt()
        }

        // XP = sum(duration + score/2 + 10 per flight)
        val totalXp = entries.sumOf {
            (it.durationMinutes ?: 5) + ((it.score ?: 50) / 2) + 10
        }
        // Progresja: level N wymaga 100*N XP (level 1 = 100 XP, 2 = 300, 3 = 600...)
        // Suma XP do levelu N = 50 * N * (N+1)
        var level = 1
        while (50 * level * (level + 1) <= totalXp) level++
        level = (level - 1).coerceAtLeast(1)

        val xpForCurrentLevel = 50 * (level - 1) * level
        val xpForNextLevel = 50 * level * (level + 1)
        val xpInLevel = totalXp - xpForCurrentLevel
        val xpNeeded = xpForNextLevel - xpForCurrentLevel

        val earnedBadges = Badges.evaluate(entries, total, totalMin, perfect, unique)

        return Stats(
            totalFlights = total,
            totalMinutes = totalMin,
            perfectFlights = perfect,
            level = level,
            xp = xpInLevel,
            xpNext = xpNeeded,
            uniqueLocations = unique,
            avgScore = avg,
            badges = earnedBadges,
        )
    }
}

data class Badge(
    val id: String,
    val icon: String,
    val name: String,
    val description: String,
    val earned: Boolean,
)

object Badges {
    fun evaluate(
        entries: List<FlightLogEntry>,
        total: Int,
        totalMin: Int,
        perfect: Int,
        unique: Int,
    ): List<Badge> {
        val hasNight = entries.any {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hour in 20..23 || hour in 0..5
        }
        val hasEarly = entries.any {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            cal.get(Calendar.HOUR_OF_DAY) in 4..7
        }
        val hasStorm = entries.any { it.verdict == "CAUTION" || it.verdict == "NO_GO" }
        val hasLong = entries.any { (it.durationMinutes ?: 0) >= 30 }

        return listOf(
            Badge("first", "🎉", "Pierwszy lot", "1 lot w historii", total >= 1),
            Badge("veteran", "🎖", "Weteran", "10 lotów", total >= 10),
            Badge("century", "💯", "Setka", "100 lotów", total >= 100),
            Badge("hour", "⏱", "Godzina w powietrzu", "60 minut łącznie", totalMin >= 60),
            Badge("day", "📅", "Dzień w powietrzu", "24h łącznie", totalMin >= 1440),
            Badge("perfect", "⭐", "Perfekcyjny", "Score ≥ 90", perfect >= 1),
            Badge("virtuoso", "🏆", "Wirtuoz", "10 perfekcyjnych lotów", perfect >= 10),
            Badge("night", "🌙", "Nocny pilot", "Lot 20:00-6:00", hasNight),
            Badge("dawn", "🌅", "Świt", "Lot przed 8:00", hasEarly),
            Badge("storm", "⛈", "Storm chaser", "Lot przy CAUTION/NO-GO", hasStorm),
            Badge("marathon", "🏃", "Maraton", "Lot >30 min", hasLong),
            Badge("explorer", "🗺", "Podróżnik", "Loty w 5+ lokacjach", unique >= 5),
        )
    }
}
