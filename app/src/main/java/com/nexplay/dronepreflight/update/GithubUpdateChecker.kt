package com.nexplay.dronepreflight.update

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Auto-update z GitHub Releases.
 *
 * UŻYCIE:
 * 1. Zmień poniżej OWNER + REPO na Twoje GitHub repo.
 * 2. Zrób release w GitHub z tagiem `v1.2.3` (versionName z app/build.gradle.kts).
 * 3. Wgraj signed APK jako asset release-a.
 * 4. Nazwij APK dowolnie — apka bierze pierwszy .apk z assetów.
 */
object GithubUpdateChecker {

    private const val OWNER = "michalzembala-cpu"
    private const val REPO = "NexDrone"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class Release(
        val tag_name: String? = null,
        val name: String? = null,
        val body: String? = null,
        val html_url: String? = null,
        val assets: List<Asset> = emptyList(),
        val prerelease: Boolean = false,
        val draft: Boolean = false,
    )

    @Serializable
    private data class Asset(
        val name: String? = null,
        val browser_download_url: String? = null,
        val size: Long = 0,
    )

    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val hasUpdate: Boolean,
        val downloadUrl: String?,
        val sizeBytes: Long,
        val releaseNotes: String,
        val releaseUrl: String,
    )

    suspend fun check(context: Context): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (OWNER == "OWNER" || REPO == "REPO") {
                error("Auto-update niekonfigurowany. Wpisz swoje GitHub OWNER/REPO w GithubUpdateChecker.kt")
            }
            val client = HttpClient(Android) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 10_000
                    connectTimeoutMillis = 5_000
                }
            }
            val url = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
            val raw = client.get(url) {
                header("Accept", "application/vnd.github.v3+json")
                header("User-Agent", "NexDrone")
            }.bodyAsText()
            val release = json.decodeFromString(Release.serializer(), raw)

            val latestTag = release.tag_name?.removePrefix("v")?.trim() ?: error("Brak tag_name w release")
            val currentVer = currentVersionName(context)
            val apk = release.assets.firstOrNull { it.name?.endsWith(".apk") == true }

            UpdateInfo(
                latestVersion = latestTag,
                currentVersion = currentVer,
                hasUpdate = compareVersions(latestTag, currentVer) > 0 && !release.draft,
                downloadUrl = apk?.browser_download_url,
                sizeBytes = apk?.size ?: 0,
                releaseNotes = release.body?.take(500) ?: "",
                releaseUrl = release.html_url ?: "",
            )
        }
    }

    private fun currentVersionName(context: Context): String {
        val pm = context.packageManager
        val pkg = context.packageName
        return runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName ?: "0.0.0"
        }.getOrElse { "0.0.0" }
    }

    /** Semver compare — zwraca >0 gdy a > b, <0 gdy a < b, 0 gdy równe. */
    private fun compareVersions(a: String, b: String): Int {
        val ap = a.split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val bp = b.split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(ap.size, bp.size)) {
            val av = ap.getOrElse(i) { 0 }
            val bv = bp.getOrElse(i) { 0 }
            if (av != bv) return av - bv
        }
        return 0
    }
}
