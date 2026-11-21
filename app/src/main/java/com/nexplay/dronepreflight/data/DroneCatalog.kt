package com.nexplay.dronepreflight.data

/**
 * Katalog dronów DJI, które da się wybrać w profilu BSP.
 * Dane z kart katalogowych DJI — tryby to nazwy z aplikacji DJI Fly.
 * Plan filmu ([FilmPlanner]) bierze stąd: jakie tryby inteligentne są dostępne,
 * jak drona trzyma wiatr, jaka rozdzielczość/klatki, czy ma unikanie przeszkód.
 */
object DroneCatalog {

    data class Model(
        val id: String,
        val name: String,
        val weightG: Int,
        val maxWindMs: Double,       // odporność na wiatr wg DJI
        val minTempC: Double,
        val maxTempC: Double,
        val flightMin: Int,          // czas lotu na baterii standardowej (lab DJI)
        val video: String,           // najlepszy tryb do filmu
        val slowMo: String?,         // tryb do zwolnionego tempa (null = brak sensownego)
        val maxFps4k: Int,
        val colorProfile: String,    // co ustawić w Kolor
        val logProfile: Boolean,     // ma D-Log M / D-Cinelike
        val trueVertical: Boolean,   // gimbal obraca się do pionu (9:16 bez przycinania)
        val quickShots: List<String>,
        val masterShots: Boolean,
        val hyperlapse: Boolean,
        val activeTrack: String?,    // nazwa śledzenia (null = brak)
        val waypoints: Boolean,
        val obstacleSensing: String, // opis czujników
        val hasObstacleAvoidance: Boolean,
        val notes: String = "",
    )

    val Models = listOf(
        Model(
            id = "neo", name = "DJI Neo", weightG = 135,
            maxWindMs = 8.0, minTempC = 0.0, maxTempC = 40.0, flightMin = 18,
            video = "4K 30 kl./s", slowMo = "1080p 60 kl./s", maxFps4k = 30,
            colorProfile = "Normalny", logProfile = false, trueVertical = false,
            quickShots = listOf("Follow", "Dronie", "Circle", "Rocket", "Spotlight", "Helix", "Boomerang"),
            masterShots = false, hyperlapse = false,
            activeTrack = "Follow (śledzenie osoby)", waypoints = false,
            obstacleSensing = "brak — tylko czujnik w dół", hasObstacleAvoidance = false,
            notes = "Lekki i wrażliwy na wiatr. Bez kontrolera sterujesz telefonem (krótki zasięg Wi-Fi).",
        ),
        Model(
            id = "mini2", name = "DJI Mini 2", weightG = 249,
            maxWindMs = 10.7, minTempC = 0.0, maxTempC = 40.0, flightMin = 31,
            video = "4K 30 kl./s", slowMo = "1080p 60 kl./s", maxFps4k = 30,
            colorProfile = "Normalny", logProfile = false, trueVertical = false,
            quickShots = listOf("Dronie", "Helix", "Rocket", "Circle", "Boomerang"),
            masterShots = false, hyperlapse = false,
            activeTrack = null, waypoints = false,
            obstacleSensing = "brak — tylko czujnik w dół", hasObstacleAvoidance = false,
        ),
        Model(
            id = "mini2se", name = "DJI Mini 2 SE", weightG = 246,
            maxWindMs = 10.7, minTempC = 0.0, maxTempC = 40.0, flightMin = 31,
            video = "2.7K 30 kl./s", slowMo = "1080p 60 kl./s", maxFps4k = 0,
            colorProfile = "Normalny", logProfile = false, trueVertical = false,
            quickShots = listOf("Dronie", "Rocket", "Circle", "Helix"),
            masterShots = false, hyperlapse = false,
            activeTrack = null, waypoints = false,
            obstacleSensing = "brak — tylko czujnik w dół", hasObstacleAvoidance = false,
        ),
        Model(
            id = "mini4k", name = "DJI Mini 4K", weightG = 249,
            maxWindMs = 10.7, minTempC = 0.0, maxTempC = 40.0, flightMin = 31,
            video = "4K 30 kl./s", slowMo = "1080p 60 kl./s", maxFps4k = 30,
            colorProfile = "Normalny", logProfile = false, trueVertical = false,
            quickShots = listOf("Dronie", "Rocket", "Circle", "Helix"),
            masterShots = false, hyperlapse = false,
            activeTrack = null, waypoints = false,
            obstacleSensing = "brak — tylko czujnik w dół", hasObstacleAvoidance = false,
        ),
        Model(
            id = "mini3", name = "DJI Mini 3", weightG = 248,
            maxWindMs = 10.7, minTempC = -10.0, maxTempC = 40.0, flightMin = 38,
            video = "4K 30 kl./s HDR", slowMo = "1080p 60 kl./s", maxFps4k = 30,
            colorProfile = "Normalny (HDR)", logProfile = false, trueVertical = true,
            quickShots = listOf("Dronie", "Rocket", "Circle", "Helix", "Boomerang", "Asteroid"),
            masterShots = false, hyperlapse = false,
            activeTrack = null, waypoints = false,
            obstacleSensing = "brak — tylko czujnik w dół", hasObstacleAvoidance = false,
        ),
        Model(
            id = "mini3pro", name = "DJI Mini 3 Pro", weightG = 249,
            maxWindMs = 10.7, minTempC = -10.0, maxTempC = 40.0, flightMin = 34,
            video = "4K 30 kl./s HDR", slowMo = "4K 60 / 1080p 120 kl./s", maxFps4k = 60,
            colorProfile = "D-Cinelike", logProfile = true, trueVertical = true,
            quickShots = listOf("Dronie", "Rocket", "Circle", "Helix", "Boomerang", "Asteroid"),
            masterShots = true, hyperlapse = true,
            activeTrack = "FocusTrack (ActiveTrack 4.0)", waypoints = true,
            obstacleSensing = "przód, tył, dół", hasObstacleAvoidance = true,
        ),
        Model(
            id = "mini4pro", name = "DJI Mini 4 Pro", weightG = 249,
            maxWindMs = 10.7, minTempC = -10.0, maxTempC = 40.0, flightMin = 34,
            video = "4K 30 kl./s HDR", slowMo = "4K 100 kl./s", maxFps4k = 100,
            colorProfile = "D-Log M", logProfile = true, trueVertical = true,
            quickShots = listOf("Dronie", "Rocket", "Circle", "Helix", "Boomerang", "Asteroid"),
            masterShots = true, hyperlapse = true,
            activeTrack = "ActiveTrack 360°", waypoints = true,
            obstacleSensing = "dookoła (360°)", hasObstacleAvoidance = true,
        ),
        Model(
            id = "other", name = "Inny / nie wiem", weightG = 249,
            maxWindMs = 8.0, minTempC = 0.0, maxTempC = 40.0, flightMin = 25,
            video = "4K 30 kl./s (albo najwyższe dostępne)", slowMo = null, maxFps4k = 30,
            colorProfile = "Normalny", logProfile = false, trueVertical = false,
            quickShots = listOf("Dronie", "Circle"),
            masterShots = false, hyperlapse = false,
            activeTrack = null, waypoints = false,
            obstacleSensing = "zakładam brak", hasObstacleAvoidance = false,
            notes = "Ostrożne założenia — wybierz dokładny model, żeby plan był trafniejszy.",
        ),
    )

    fun byId(id: String?): Model? = Models.firstOrNull { it.id == id }

    /** Próbuje zgadnąć model z nazwy profilu wpisanej ręcznie (np. „mój Mini 3”). */
    fun guessFromName(name: String): Model? {
        val n = name.lowercase().replace(" ", "")
        return when {
            "mini4pro" in n -> byId("mini4pro")
            "mini4k" in n -> byId("mini4k")
            "mini3pro" in n -> byId("mini3pro")
            "mini3" in n -> byId("mini3")
            "mini2se" in n -> byId("mini2se")
            "mini2" in n -> byId("mini2")
            "neo" in n -> byId("neo")
            else -> null
        }
    }

    /** Model dla profilu: zapisany wprost albo zgadnięty z nazwy. */
    fun forProfile(p: DroneProfile?): Model? =
        p?.let { byId(it.modelId) ?: guessFromName(it.name) }
}
