package com.nexplay.dronepreflight.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmPlannerTest {

    private fun stat(v: Double?) = SourceStat(v, v, v, 0.0, 1)

    private fun snap(wind: Double, gust: Double, temp: Double, dir: Double = 270.0) = AggregatedSnapshot(
        locationName = "Test", latitude = 52.23, longitude = 21.01, fetchedAt = 0L,
        successfulSources = 1, totalSources = 1, readings = emptyList(), failures = emptyList(),
        wind = stat(wind), gust = stat(gust), windDir = stat(dir), temp = stat(temp),
        precip = stat(0.0), cloud = stat(20.0), visibility = stat(20_000.0),
        stormVotes = 0, fogVotes = 0, kpIndex = 2.0, kpSource = null,
    )

    // 21.11.2026 12:00 czasu PL
    private val noon = 1795258800000L

    private fun dump(p: FilmPlanner.FilmPlan) {
        println("=== ${p.model.name}: ${p.overall} — ${p.headline}")
        println("KIEDY: ${p.whenText}\nSŁOŃCE: ${p.sunDirText}\nWIATR: ${p.windText}\nBATERIE: ${p.batteriesText}")
        p.warnings.forEach { println("! $it") }
        p.shots.forEach { println("  ${it.num}. ${it.title} [${it.verdict}] tryb=${it.mode} h=${it.height} — ${it.verdictNote}") }
    }

    @Test fun calmDayNeoUsesQuickShots() {
        val p = FilmPlanner.plan(DroneCatalog.byId("neo")!!, snap(2.0, 3.0, 4.0), noon)
        dump(p)
        assertEquals(Verdict.GO, p.overall)
        assertEquals("QuickShot › Circle", p.shots[1].mode)
        assertTrue(p.whenText.startsWith("Dziś wieczorem"))
        assertTrue(p.batteriesText.contains("baterie") || p.batteriesText.contains("baterii"))
    }

    @Test fun windyDayNeoIsNoGoButMini4ProCaution() {
        val neo = FilmPlanner.plan(DroneCatalog.byId("neo")!!, snap(7.5, 10.0, 8.0), noon)
        val mini = FilmPlanner.plan(DroneCatalog.byId("mini4pro")!!, snap(7.5, 10.0, 8.0), noon)
        dump(neo); dump(mini)
        assertEquals(Verdict.NO_GO, neo.overall)
        assertEquals(Verdict.CAUTION, mini.overall)
        assertTrue(mini.shots.any { it.title.contains("MasterShots") })
    }

    @Test fun afterSunsetPlansTomorrow() {
        val p = FilmPlanner.plan(DroneCatalog.byId("mini3")!!, snap(2.0, 3.0, 4.0), noon + 8 * 3600_000L)
        assertTrue(p.whenText, p.whenText.startsWith("Jutro wieczorem"))
    }
}
