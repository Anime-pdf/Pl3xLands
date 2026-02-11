package me.animepdf.pl3xLands.http

import com.google.gson.GsonBuilder
import me.animepdf.pl3xLands.config.ApiAuthType
import me.animepdf.pl3xLands.config.ApiConfig
import me.animepdf.pl3xLands.dto.RegionManifest
import me.animepdf.pl3xLands.hook.Pl3xMapHook
import me.animepdf.pl3xLands.storage.RegionStorage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.io.encoding.Base64

class RegionSyncManager(
    private val apiConfig: ApiConfig,
    private val storage: RegionStorage,
    private val pl3xMapHook: Pl3xMapHook,
    private val logger: Logger
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(apiConfig.timeoutInterval))
        .build()

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        scheduler.scheduleAtFixedRate({
            try {
                sync()
            } catch (e: Exception) {
                logger.warning("Sync failed: ${e.message}")
                e.printStackTrace()
            }
        }, 0, apiConfig.refreshInterval, TimeUnit.SECONDS)
    }

    fun shutdown() {
        scheduler.shutdown()
    }

    private fun sync() {
        logger.info("Starting Sync...")

        val localManifest = storage.load()
        val localHash = localManifest?.hash ?: ""

        var authHeader = "Authorization"
        var authValue: String

        val auth = apiConfig.auth

        when (auth.type) {
            ApiAuthType.BEARER -> {
                authValue = auth.bearer.token
            }

            ApiAuthType.BASIC -> {
                authValue =
                    Base64.encode("${auth.basic.username}:${auth.basic.password}".toByteArray())
            }

            ApiAuthType.KEY -> {
                authHeader = auth.key.header
                authValue = auth.key.value
            }
        }

        val statusRequest = HttpRequest.newBuilder()
            .uri(URI.create("${apiConfig.url}/status"))
            .header(authHeader, authValue)
            .GET()
            .build()

        val statusResponse = client.send(statusRequest, HttpResponse.BodyHandlers.ofString())

        val remoteStatus = gson.fromJson(statusResponse.body(), Map::class.java)
        val remoteHash = remoteStatus["hash"] as String

        if (localHash == remoteHash) {
            logger.info("Local data is up to date. Skipping download.")
            return
        }

        logger.info("Update detected (Local: $localHash != Remote: $remoteHash). Downloading...")

        val dataRequest = HttpRequest.newBuilder()
            .uri(URI.create("${apiConfig.url}/regions"))
            .header(authHeader, authValue)
            .GET()
            .build()

        val dataResponse = client.send(dataRequest, HttpResponse.BodyHandlers.ofString())

        if (dataResponse.statusCode() == 200) {
            val newManifest = gson.fromJson(dataResponse.body(), RegionManifest::class.java)
            storage.save(newManifest)
            logger.info("Regions updated successfully. Loaded ${newManifest.regions.size} regions.")

            pl3xMapHook.updateMap()
        } else {
            logger.warning("Failed to download data: HTTP ${dataResponse.statusCode()}")
        }
    }
}