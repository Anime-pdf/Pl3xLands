package me.animepdf.pl3xLands.http

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import me.animepdf.pl3xLands.Pl3xLandsPlugin
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

// Passing hook sounds very wrong, and probably is, todo: do it right
class WebServer(private val plugin: Pl3xLandsPlugin, private val port: Int, private val pl3xMapHook: Pl3xMapHook, private val storage: RegionStorage) {

    private var server: HttpServer? = null
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val pl3xUrl
        get() = plugin.config.editor.mapUrl

    private val executor = Executors.newFixedThreadPool(3)

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(plugin.config.editor.timeoutInterval))
        .build()

    fun start() {
        try {
            server = HttpServer.create(InetSocketAddress(port), 0)
            server?.executor = executor

            server?.createContext("/") { exchange ->
                try {
                    handleStaticFile(exchange)
                } catch (e: Exception) {
                    plugin.logger.warning("Can't serve files: ${e.message}")
                    sendResponse(exchange, 501, "Internal Server Error")
                }
            }

            server?.createContext("/api/config") { exchange ->
                if (exchange.requestMethod == "GET") {

                    val settingsRequest = HttpRequest.newBuilder()
                        .uri(URI.create("$pl3xUrl/tiles/settings.json"))
                        .GET()
                        .build()
                    val settingsResponse = client.send(settingsRequest, HttpResponse.BodyHandlers.ofString())

                    if (settingsResponse.statusCode() != 200) {
                        plugin.logger.warning("Error getting map settings")
                        sendResponse(exchange, 501, "Internal Server Error")
                        return@createContext
                    }

                    val settings = JsonObject()
                    settings.add("mapUrl", JsonPrimitive(pl3xUrl))

                    val json = JsonParser.parseString(settingsResponse.body()).asJsonObject
                    settings.add("format", json.get("format"))

                    val worldsSettings = JsonArray()
                    json.getAsJsonArray("worldSettings").forEach { worldsSettings.add(extractWorldSettings(it.asJsonObject)) }
                    settings.add("worlds", worldsSettings)

                    sendResponse(exchange, 200, gson.toJson(settings), isJson = true)
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed")
                }
            }

            server?.createContext("/api/regions") { exchange ->
                if (exchange.requestMethod == "GET") {
                    val outMarkers = JsonObject()
                    pl3xMapHook.markers.forEach { (world, markers) ->
                        val worldMarkers = JsonArray()
                        markers.forEach { worldMarkers.add(it.toJson()) }
                        outMarkers.add(world, worldMarkers)
                    }
                    sendResponse(exchange, 200, gson.toJson(outMarkers), isJson = true)
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed")
                }
            }

//            server?.createContext("/api/save") { exchange ->
//                if (exchange.requestMethod == "POST") {
//
//                } else {
//                    sendResponse(exchange, 405, "Method Not Allowed")
//                }
//            }

            server?.start()
            plugin.logger.info("[WebEditor] Сервер запущен по адресу: http://localhost:$port/")

        } catch (e: IOException) {
            plugin.logger.severe("[WebEditor] Не удалось запустить веб-сервер на порту $port: ${e.message}")
        }
    }

    fun stop() {
        if (server != null) {
            server?.stop(0)
            executor.shutdownNow()
            plugin.logger.info("[WebEditor] Сервер остановлен.")
        }
    }

    private fun handleStaticFile(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET") {
            sendResponse(exchange, 405, "Method Not Allowed")
            return
        }

        var path = exchange.requestURI.path
        if (path == "/" || path == "") path = "/index.html"

        if (path.contains("..")) {
            sendResponse(exchange, 403, "Access Denied")
            return
        }

        val file = File(plugin.dataFolder, "web$path")

        if (file.exists() && !file.isDirectory) {
            val mimeType = when {
                path.endsWith(".html") -> "text/html"
                path.endsWith(".js") -> "application/javascript"
                path.endsWith(".css") -> "text/css"
                path.endsWith(".png") -> "image/png"
                path.endsWith(".json") -> "application/json"
                else -> "application/octet-stream"
            }

            exchange.responseHeaders.add("Content-Type", mimeType)
            exchange.responseHeaders.add("Cache-Control", "max-age=3600")

            val bytes = file.readBytes()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            exchange.close()
        } else {
            sendResponse(exchange, 404, "File Not Found")
        }
    }
    private fun sendResponse(exchange: HttpExchange, code: Int, response: String, isJson: Boolean = false) {
        val bytes = response.toByteArray(StandardCharsets.UTF_8)

        if (isJson) {
            exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
        } else {
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=UTF-8")
        }

        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type,Authorization")

        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.close()
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
            settings.asMap().map { (key, value) ->
                extractTo.add(key, value)
            }
        }

        return settings
    }

    private fun getWorldSettings(world: String, root: JsonObject? = null): JsonObject {
        val settings = JsonObject()

        val worldSettingsRequest = HttpRequest.newBuilder()
            .uri(URI.create("$pl3xUrl/tiles/$world/settings.json"))
            .GET()
            .build()
        val worldSettingsResponse = client.send(worldSettingsRequest, HttpResponse.BodyHandlers.ofString())

        val json = JsonParser.parseString(worldSettingsResponse.body()).asJsonObject
        settings.add("zoom", json.get("zoom"))
        settings.add("spawn", json.get("spawn"))
        settings.add("center", json.get("center"))

        if (root != null) {
            settings.asMap().map { (key, value) ->
                root.add(key, value)
            }
        }

        return settings
    }
}
