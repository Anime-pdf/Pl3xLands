package me.animepdf.pl3xLands.editor

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import libs.org.wildfly.common.Assert
import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.hook.Pl3xMapHook
import me.animepdf.pl3xLands.storage.RegionStorage
import me.animepdf.pl3xLands.storage.StorageResult
import me.animepdf.pl3xLands.validation.RegionValidator
import me.animepdf.pl3xLands.validation.ValidationResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

class EditorApiHandler(
    private val storage: RegionStorage,
    private val hook: Pl3xMapHook,
    private val sessionManager: SessionManager,
    private val gson: Gson,
    private val logger: Logger
) {

    /**
     * POST /api/auth
     */
    fun handleAuth(exchange: HttpExchange, sendResponse: (Int, String, Boolean) -> Unit) {
        if (exchange.requestMethod != "POST") {
            sendResponse(405, """{"error": "Method not allowed"}""", true)
            return
        }

        try {
            val body = readRequestBody(exchange)
            val credentials = HashMap<String, String>()

            val credentialsRaw = JsonParser.parseString(body).asJsonObject
            credentialsRaw.keySet().forEach { key ->
                if (!credentialsRaw.get(key).isJsonPrimitive || !credentialsRaw.getAsJsonPrimitive(key).isString) return
                credentials.putIfAbsent(key, credentialsRaw.getAsJsonPrimitive(key).asString)
            }

            val token = sessionManager.authenticate(credentials)

            if (token != null) {
                val response = JsonObject()
                response.addProperty("success", true)
                response.addProperty("token", token)
                sendResponse(200, gson.toJson(response), true)
            } else {
                val response = JsonObject()
                response.addProperty("success", false)
                response.addProperty("error", "Invalid credentials")
                sendResponse(401, gson.toJson(response), true)
            }
        } catch (e: Exception) {
            logger.severe("Error in /api/auth: ${e.message}")
            sendResponse(500, """{"error": "Internal server error"}""", true)
        }
    }

    /**
     * POST /api/logout
     */
    fun handleLogout(exchange: HttpExchange, sendResponse: (Int, String, Boolean) -> Unit) {
        if (exchange.requestMethod != "POST") {
            sendResponse(405, """{"error": "Method not allowed"}""", true)
            return
        }

        val token = extractSessionToken(exchange)
        if (token != null) {
            sessionManager.invalidateSession(token)
        }

        sendResponse(200, """{"success": true}""", true)
    }

    /**
     * GET /api/editor/regions
     */
    fun handleGetRegions(exchange: HttpExchange, sendResponse: (Int, String, Boolean) -> Unit) {
        if (!requireAuth(exchange, sendResponse)) return

        if (exchange.requestMethod != "GET") {
            sendResponse(405, """{"error": "Method not allowed"}""", true)
            return
        }

        try {
            val regions = storage.getAllRegions()
            val response = JsonObject()
            response.addProperty("success", true)
            response.add("regions", gson.toJsonTree(regions))

            sendResponse(200, gson.toJson(response), true)
        } catch (e: Exception) {
            logger.severe("Error in GET /api/editor/regions: ${e.message}")
            sendResponse(500, """{"error": "Internal server error"}""", true)
        }
    }

    /**
     * GET /api/editor/regions/{id}
     */
    fun handleGetRegion(exchange: HttpExchange, regionId: String, sendResponse: (Int, String, Boolean) -> Unit) {
        if (!requireAuth(exchange, sendResponse)) return

        if (exchange.requestMethod != "GET") {
            sendResponse(405, """{"error": "Method not allowed"}""", true)
            return
        }

        try {
            val region = storage.getRegion(regionId)

            if (region != null) {
                val response = JsonObject()
                response.addProperty("success", true)
                response.add("region", gson.toJsonTree(region))
                sendResponse(200, gson.toJson(response), true)
            } else {
                sendResponse(404, """{"error": "Region not found"}""", true)
            }
        } catch (e: Exception) {
            logger.severe("Error in GET /api/editor/regions/$regionId: ${e.message}")
            sendResponse(500, """{"error": "Internal server error"}""", true)
        }
    }

    /**
     * POST /api/editor/regions
     */
    fun handleCreateRegion(exchange: HttpExchange, sendResponse: (Int, String, Boolean) -> Unit) {
        if (!requireAuth(exchange, sendResponse)) return

        if (exchange.requestMethod != "POST") {
            sendResponse(405, """{"error": "Method not allowed"}""", true)
            return
        }

        try {
            val body = readRequestBody(exchange)
            val region = gson.fromJson(body, Region::class.java)

            when (val validationResult = RegionValidator.validate(region)) {
                is ValidationResult.Success -> {
                    when (val storageResult = storage.addRegion(region)) {
                        is StorageResult.Success -> {
                            storage.save()
                            hook.updateMap()

                            val response = JsonObject()
                            response.addProperty("success", true)
                            response.add("region", gson.toJsonTree(region))

                            logger.info("Created region '${region.id}'")
                            sendResponse(201, gson.toJson(response), true)
                        }
                        is StorageResult.Failure -> {
                            val response = JsonObject()
                            response.addProperty("success", false)
                            response.addProperty("error", storageResult.error)
                            sendResponse(400, gson.toJson(response), true)
                        }
                    }
                }
                is ValidationResult.Failure -> {
                    val response = JsonObject()
                    response.addProperty("success", false)
                    response.addProperty("error", "Validation failed")
                    response.add("errors", gson.toJsonTree(validationResult.errors))
                    sendResponse(400, gson.toJson(response), true)
                }
            }
        } catch (e: Exception) {
            logger.severe("Error in POST /api/editor/regions: ${e.message}")
            e.printStackTrace()
            sendResponse(500, """{"error": "Internal server error"}""", true)
        }
    }

    /**
     * PUT /api/editor/regions/{id}
     */
    fun handleUpdateRegion(exchange: HttpExchange, regionId: String, sendResponse: (Int, String, Boolean) -> Unit) {
        if (!requireAuth(exchange, sendResponse)) return

        if (exchange.requestMethod != "PUT") {
            sendResponse(405, """{"error": "Method not allowed"}""", true)
            return
        }

        try {
            val body = readRequestBody(exchange)
            val region = gson.fromJson(body, Region::class.java)

            if (region.id != regionId) {
                sendResponse(400, """{"error": "Region ID mismatch"}""", true)
                return
            }

            when (val validationResult = RegionValidator.validate(region)) {
                is ValidationResult.Success -> {
                    when (val storageResult = storage.updateRegion(region)) {
                        is StorageResult.Success -> {
                            storage.save()
                            hook.updateMap()

                            val response = JsonObject()
                            response.addProperty("success", true)
                            response.add("region", gson.toJsonTree(region))

                            logger.info("Updated region '${region.id}'")
                            sendResponse(200, gson.toJson(response), true)
                        }
                        is StorageResult.Failure -> {
                            val response = JsonObject()
                            response.addProperty("success", false)
                            response.addProperty("error", storageResult.error)
                            sendResponse(400, gson.toJson(response), true)
                        }
                    }
                }
                is ValidationResult.Failure -> {
                    val response = JsonObject()
                    response.addProperty("success", false)
                    response.addProperty("error", "Validation failed")
                    response.add("errors", gson.toJsonTree(validationResult.errors))
                    sendResponse(400, gson.toJson(response), true)
                }
            }
        } catch (e: Exception) {
            logger.severe("Error in PUT /api/editor/regions/$regionId: ${e.message}")
            e.printStackTrace()
            sendResponse(500, """{"error": "Internal server error"}""", true)
        }
    }

    /**
     * DELETE /api/editor/regions/{id}
     */
    fun handleDeleteRegion(exchange: HttpExchange, regionId: String, sendResponse: (Int, String, Boolean) -> Unit) {
        if (!requireAuth(exchange, sendResponse)) return

        if (exchange.requestMethod != "DELETE") {
            sendResponse(405, """{"error": "Method not allowed"}""", true)
            return
        }

        try {
            when (val result = storage.deleteRegion(regionId)) {
                is StorageResult.Success -> {
                    storage.save()
                    hook.updateMap()

                    logger.info("Deleted region '$regionId'")
                    sendResponse(200, """{"success": true}""", true)
                }
                is StorageResult.Failure -> {
                    val response = JsonObject()
                    response.addProperty("success", false)
                    response.addProperty("error", result.error)
                    sendResponse(404, gson.toJson(response), true)
                }
            }
        } catch (e: Exception) {
            logger.severe("Error in DELETE /api/editor/regions/$regionId: ${e.message}")
            sendResponse(500, """{"error": "Internal server error"}""", true)
        }
    }

    /**
     * GET /api/editor/worlds
     */
    fun handleGetWorlds(exchange: HttpExchange, sendResponse: (Int, String, Boolean) -> Unit) {
        if (!requireAuth(exchange, sendResponse)) return

        if (exchange.requestMethod != "GET") {
            sendResponse(405, """{"error": "Method not allowed"}""", true)
            return
        }

        try {
            val worlds = org.bukkit.Bukkit.getWorlds().map { it.name }
            val response = JsonObject()
            response.addProperty("success", true)
            response.add("worlds", gson.toJsonTree(worlds))

            sendResponse(200, gson.toJson(response), true)
        } catch (e: Exception) {
            logger.severe("Error in GET /api/editor/worlds: ${e.message}")
            sendResponse(500, """{"error": "Internal server error"}""", true)
        }
    }

    /**
     * Require authentication for a request.
     * Returns true if authenticated, false otherwise.
     */
    private fun requireAuth(exchange: HttpExchange, sendResponse: (Int, String, Boolean) -> Unit): Boolean {
        val token = extractSessionToken(exchange)

        if (token == null || !sessionManager.validateSession(token)) {
            sendResponse(401, """{"error": "Unauthorized"}""", true)
            return false
        }

        return true
    }

    /**
     * Extract session token from request.
     */
    private fun extractSessionToken(exchange: HttpExchange): String? {
        val authHeader = exchange.requestHeaders.getFirst("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        val cookieHeader = exchange.requestHeaders.getFirst("Cookie")
        if (cookieHeader != null) {
            val cookies = cookieHeader.split(";").map { it.trim() }
            for (cookie in cookies) {
                if (cookie.startsWith("session=")) {
                    return cookie.substring(8)
                }
            }
        }

        return null
    }

    /**
     * Read request body as string.
     */
    private fun readRequestBody(exchange: HttpExchange): String {
        return BufferedReader(
            InputStreamReader(exchange.requestBody, StandardCharsets.UTF_8)
        ).use { it.readText() }
    }
}