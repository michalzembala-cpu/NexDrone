package com.nexplay.dronepreflight.data

import com.nexplay.dronepreflight.data.sources.BrightSkySource
import com.nexplay.dronepreflight.data.sources.MetNorwaySource
import com.nexplay.dronepreflight.data.sources.OpenMeteoSource
import com.nexplay.dronepreflight.data.sources.SevenTimerSource
import com.nexplay.dronepreflight.data.sources.SourceReading
import com.nexplay.dronepreflight.data.sources.WeatherSource
import com.nexplay.dronepreflight.data.sources.WttrInSource
import com.nexplay.dronepreflight.data.sources.kp.GfzNowcastKp
import com.nexplay.dronepreflight.data.sources.kp.KpReading
import com.nexplay.dronepreflight.data.sources.kp.KpSource
import com.nexplay.dronepreflight.data.sources.kp.NoaaBoulderK
import com.nexplay.dronepreflight.data.sources.kp.NoaaKpForecast
import com.nexplay.dronepreflight.data.sources.kp.NoaaPlanetaryKp
import com.nexplay.dronepreflight.data.sources.kp.NoaaWingKp
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

class WeatherRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }

    private val openMeteo = OpenMeteoSource()

    private val sources: List<WeatherSource> = listOf(
        openMeteo,
        MetNorwaySource(),
        WttrInSource(),
        BrightSkySource(),
        SevenTimerSource(),
    )

    private val kpSources: List<KpSource> = listOf(
        NoaaPlanetaryKp(),
        GfzNowcastKp(),
        NoaaKpForecast(),
        NoaaWingKp(),
        NoaaBoulderK(),
    )

    /** Godzinowa prognoza Open-Meteo (do skanu najlepszej pory). */
    suspend fun fetchHourlyScan(lat: Double, lon: Double): List<Pair<Instant, com.nexplay.dronepreflight.data.sources.SourceReading>> =
        withContext(Dispatchers.IO) {
            runCatching { openMeteo.fetchAllHours(client, lat, lon) }.getOrDefault(emptyList())
        }

    suspend fun fetch(
        lat: Double,
        lon: Double,
        locationName: String,
        target: Instant,
    ): AggregatedSnapshot = withContext(Dispatchers.IO) {
        val readings = mutableListOf<SourceReading>()
        val failures = mutableListOf<SourceFailure>()

        coroutineScope {
            sources.map { source ->
                async {
                    runCatching {
                        withTimeout(10_000) { source.fetch(client, lat, lon, target) }
                    }.onSuccess { readings += it }
                        .onFailure {
                            failures += SourceFailure(
                                source = source.name,
                                reason = it.message?.take(120) ?: it::class.simpleName.orEmpty(),
                            )
                        }
                }
            }.awaitAll()
        }

        val (kpReadings, kpFailures) = fetchKpAll()

        WeatherAggregator.aggregate(
            locationName = locationName,
            lat = lat,
            lon = lon,
            readings = readings,
            failures = failures,
            totalSources = sources.size,
            kpReadings = kpReadings,
            kpFailures = kpFailures,
            kpTotalSources = kpSources.size,
        )
    }

    private suspend fun fetchKpAll(): Pair<List<KpReading>, List<SourceFailure>> = coroutineScope {
        val readings = mutableListOf<KpReading>()
        val failures = mutableListOf<SourceFailure>()
        kpSources.map { src ->
            async {
                runCatching {
                    withTimeout(10_000) { src.fetch(client) }
                }.onSuccess { readings += it }
                    .onFailure {
                        failures += SourceFailure(
                            source = src.name,
                            reason = it.message?.take(120) ?: it::class.simpleName.orEmpty(),
                        )
                    }
            }
        }.awaitAll()
        readings to failures
    }
}
