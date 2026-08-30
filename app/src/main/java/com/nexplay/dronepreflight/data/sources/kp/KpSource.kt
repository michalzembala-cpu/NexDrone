package com.nexplay.dronepreflight.data.sources.kp

import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable

@Serializable
data class KpReading(val source: String, val value: Double)

interface KpSource {
    val name: String
    suspend fun fetch(client: HttpClient): KpReading
}
