package me.animepdf.pl3xLands.http

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import me.animepdf.pl3xLands.Pl3xLandsPlugin
import me.animepdf.pl3xLands.editor.EditorApiHandler
import me.animepdf.pl3xLands.editor.SessionManager
import me.animepdf.pl3xLands.hook.Pl3xMapHook
import me.animepdf.pl3xLands.storage.RegionStorage
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebServer(
    private val plugin: Pl3xLandsPlugin,
    private val port: Int,
    private val pl3xMapHook: Pl3xMapHook,
    storage: RegionStorage
) {

    private var server: HttpServer? = null
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    private val pl3xUrl: String
        get() = plugin.config.editor.mapUrl

    private val executor = Executors.newFixedThreadPool(3)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(plugin.config.editor.timeoutInterval))
        .build()

    private val sessionManager = SessionManager(plugin.config.editor.auth, plugin.logger)
    private val apiHandler = EditorApiHandler(storage, pl3xMapHook, sessionManager, gson, plugin.logger)

    fun start() {
        try {
            val bindAddress = InetSocketAddress(plugin.config.editor.bindAddress, port)
            server = HttpServer.create(bindAddress, 0)
            server?.executor = executor

            // Static files (lowest priority)
            server?.createContext("/") { exchange ->
                handleStaticFile(exchange)
            }

            // Public API endpoints (no auth required)
            server?.createContext("/api/config") { exchange ->
                handleApiConfig(exchange)
            }

            server?.createContext("/api/regions") { exchange ->
                handleApiRegions(exchange)
            }

            // Auth endpoints
            server?.createContext("/api/auth") { exchange ->
                apiHandler.handleAuth(exchange, sendResponseFor(exchange))
            }

            server?.createContext("/api/logout") { exchange ->
                apiHandler.handleLogout(exchange, sendResponseFor(exchange))
            }

            // Editor API endpoints (auth required)
            server?.createContext("/api/editor/regions") { exchange ->
                handleEditorRegions(exchange)
            }

            server?.createContext("/api/editor/worlds") { exchange ->
                apiHandler.handleGetWorlds(exchange, sendResponseFor(exchange))
            }

            // Start cleanup task for expired sessions
            executor.submit {
                while (!executor.isShutdown) {
                    try {
                        Thread.sleep(60000) // Every minute
                        sessionManager.cleanupExpiredSessions()
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }

            server?.start()
            plugin.logger.info("[WebEditor] Server started at http://${plugin.config.editor.bindAddress}:$port/")

        } catch (e: IOException) {
            plugin.logger.severe("[WebEditor] Failed to start server on port $port: ${e.message}")
            throw e
        }
    }

    fun stop() {
        try {
            server?.stop(0)

            executor.shutdown()
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    plugin.logger.warning("[WebEditor] Executor did not terminate in time, forcing shutdown")
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                plugin.logger.warning("[WebEditor] Shutdown interrupted")
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }

            plugin.logger.info("[WebEditor] Server stopped")
        } catch (e: Exception) {
            plugin.logger.severe("[WebEditor] Error during shutdown: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handleEditorRegions(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val parts = path.split("/").filter { it.isNotEmpty() }

        when {
            // GET/POST /api/editor/regions
            parts.size == 3 -> {
                when (exchange.requestMethod) {
                    "GET" -> apiHandler.handleGetRegions(exchange, sendResponseFor(exchange))
                    "POST" -> apiHandler.handleCreateRegion(exchange, sendResponseFor(exchange))
                    else -> sendResponse(exchange, 405, """{"error": "Method not allowed"}""", true)
                }
            }
            // GET/PUT/DELETE /api/editor/regions/{id}
            parts.size == 4 -> {
                val regionId = parts[3]
                when (exchange.requestMethod) {
                    "GET" -> apiHandler.handleGetRegion(exchange, regionId, sendResponseFor(exchange))
                    "PUT" -> apiHandler.handleUpdateRegion(exchange, regionId, sendResponseFor(exchange))
                    "DELETE" -> apiHandler.handleDeleteRegion(exchange, regionId, sendResponseFor(exchange))
                    else -> sendResponse(exchange, 405, """{"error": "Method not allowed"}""", true)
                }
            }
            else -> sendResponse(exchange, 404, """{"error": "Not found"}""", true)
        }
    }

    private fun handleApiConfig(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return
            }

            val settingsRequest = HttpRequest.newBuilder()
                .uri(URI.create("$pl3xUrl/tiles/settings.json"))
                .GET()
                .build()

            val settingsResponse = client.send(settingsRequest, HttpResponse.BodyHandlers.ofString())

            if (settingsResponse.statusCode() != 200) {
                plugin.logger.warning("[WebEditor] Error getting map settings: ${settingsResponse.statusCode()}")
                sendResponse(exchange, 502, "Bad Gateway: Cannot reach map server")
                return
            }

            val settings = JsonObject()
            settings.add("mapUrl", JsonPrimitive(pl3xUrl))

            settings.add("sessionTimeout", JsonPrimitive(plugin.config.editor.auth.sessionTimeout))

            val json = JsonParser.parseString(settingsResponse.body()).asJsonObject
            settings.add("format", json.get("format"))

            val worldsSettings = JsonArray()
            json.getAsJsonArray("worldSettings").forEach {
                worldsSettings.add(extractWorldSettings(it.asJsonObject))
            }
            settings.add("worlds", worldsSettings)

            sendResponse(exchange, 200, gson.toJson(settings), isJson = true)

        } catch (e: Exception) {
            plugin.logger.severe("[WebEditor] Error in /api/config: ${e.message}")
            e.printStackTrace()
            sendResponse(exchange, 500, "Internal Server Error")
        }
    }

    private fun handleApiRegions(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return
            }

            val allMarkers = pl3xMapHook.getAllMarkers()

            val outMarkers = JsonObject()
            allMarkers.forEach { (world, markers) ->
                val worldMarkers = JsonArray()
                markers.forEach {
                    val world = it.toJson()
                    world.asJsonObject.add("options", it.options?.toJson())
                    worldMarkers.add(world)
                }
                outMarkers.add(world, worldMarkers)
            }

            sendResponse(exchange, 200, gson.toJson(outMarkers), isJson = true)

        } catch (e: Exception) {
            plugin.logger.severe("[WebEditor] Error in /api/regions: ${e.message}")
            e.printStackTrace()
            sendResponse(exchange, 500, "Internal Server Error")
        }
    }

    private fun handleStaticFile(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET") {
                sendResponse(exchange, 405, "Method Not Allowed")
                return
            }

            var path = exchange.requestURI.path
            if (path == "/" || path == "") path = "/index.html"

            val normalizedPath = File(path).normalize().path
            if (normalizedPath.contains("..")) {
                sendResponse(exchange, 403, "Forbidden")
                return
            }

            val file = File(plugin.dataFolder, "web$normalizedPath")

            val webDir = File(plugin.dataFolder, "web").canonicalFile
            if (!file.canonicalFile.startsWith(webDir)) {
                sendResponse(exchange, 403, "Forbidden")
                return
            }

            if (file.exists() && file.isFile) {
                val mimeType = when {
                    path.endsWith(".html") -> "text/html; charset=UTF-8"
                    path.endsWith(".js") -> "application/javascript; charset=UTF-8"
                    path.endsWith(".css") -> "text/css; charset=UTF-8"
                    path.endsWith(".png") -> "image/png"
                    path.endsWith(".json") -> "application/json; charset=UTF-8"
                    else -> "application/octet-stream"
                }

                exchange.responseHeaders.set("Content-Type", mimeType)
                exchange.responseHeaders.set("Cache-Control", "max-age=3600")

                val bytes = file.readBytes()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.close()
            } else {
                sendResponse(exchange, 404, "Not Found")
            }
        } catch (e: Exception) {
            plugin.logger.warning("[WebEditor] Error serving file: ${e.message}")
            sendResponse(exchange, 500, "Internal Server Error")
        }
    }

    fun sendResponseFor(exchange: HttpExchange): (Int, String, Boolean) -> Unit =
        { code, body, isJson -> sendResponse(exchange, code, body, isJson) }

    private fun sendResponse(exchange: HttpExchange, code: Int, response: String, isJson: Boolean = false) {
        try {
            val bytes = response.toByteArray(StandardCharsets.UTF_8)

            if (isJson) {
                exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
            } else {
                exchange.responseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
            }

            val corsConfig = plugin.config.editor.cors
            if (corsConfig.enabled) {
                val origin = exchange.requestHeaders.getFirst("Origin") ?: "*"

                if (corsConfig.allowedOrigins.contains("*") || corsConfig.allowedOrigins.contains(origin)) {
                    exchange.responseHeaders.set("Access-Control-Allow-Origin", origin)
                }

                exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Authorization")

                if (corsConfig.allowCredentials) {
                    exchange.responseHeaders.set("Access-Control-Allow-Credentials", "true")
                }
            }

            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            exchange.close()
        } catch (e: Exception) {
            plugin.logger.warning("[WebEditor] Error sending response: ${e.message}")
        }
    }

    private fun extractWorldSettings(world: JsonObject, extractTo: JsonObject? = null): JsonObject {
        val settings = JsonObject()

        settings.add("name", world.get("name"))

        val renderers = world.getAsJsonArray("renderers")
        if (renderers.size() > 0) {
            settings.add("renderer", renderers[0].asJsonObject.get("label"))
        } else {
            settings.add("renderer", JsonPrimitive("vintage_story"))
        }

        getWorldSettings(world.get("name").asString, settings)

        if (extractTo != null) {
            settings.asMap().forEach { (key, value) ->
                extractTo.add(key, value)
            }
        }

        return settings
    }

    private fun getWorldSettings(world: String, root: JsonObject? = null): JsonObject {
        val settings = JsonObject()

        try {
            val worldSettingsRequest = HttpRequest.newBuilder()
                .uri(URI.create("$pl3xUrl/tiles/$world/settings.json"))
                .GET()
                .build()

            val worldSettingsResponse = client.send(worldSettingsRequest, HttpResponse.BodyHandlers.ofString())

            if (worldSettingsResponse.statusCode() == 200) {
                val json = JsonParser.parseString(worldSettingsResponse.body()).asJsonObject
                settings.add("zoom", json.get("zoom"))
                settings.add("spawn", json.get("spawn"))
                settings.add("center", json.get("center"))
            } else {
                plugin.logger.warning("[WebEditor] Failed to get settings for world $world: ${worldSettingsResponse.statusCode()}")
            }
        } catch (e: Exception) {
            plugin.logger.warning("[WebEditor] Error getting settings for world $world: ${e.message}")
        }

        if (root != null) {
            settings.asMap().forEach { (key, value) ->
                root.add(key, value)
            }
        }

        return settings
    }
}