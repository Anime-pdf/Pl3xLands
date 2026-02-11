package me.animepdf.pl3xLands

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.animepdf.pl3xLands.config.GeneralConfig
import me.animepdf.pl3xLands.config.StorageType
import me.animepdf.pl3xLands.hook.Pl3xMapHook
import me.animepdf.pl3xLands.http.RegionSyncManager
import me.animepdf.pl3xLands.http.WebServer
import me.animepdf.pl3xLands.storage.BinaryFileStorage
import me.animepdf.pl3xLands.storage.JsonFileStorage
import me.animepdf.pl3xLands.storage.RegionStorage
import me.animepdf.pl3xLands.util.ConfigManager
import me.animepdf.pl3xLands.validation.RegionValidator
import net.pl3x.map.core.Pl3xMap
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Pl3xLandsPlugin : JavaPlugin() {
    lateinit var config: GeneralConfig
        private set

    private var gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private var webEditor: WebServer? = null
    private var syncManager: RegionSyncManager? = null
    private var pl3xMapHook: Pl3xMapHook? = null
    private var storage: RegionStorage? = null

    override fun onEnable() {
        try {
            loadConfig()
            initializeStorage()
            initializePl3xMap()
            initializeApiSync()
            initializeWebEditor()

            logger.info("Pl3xLands enabled successfully")
        } catch (e: Exception) {
            logger.severe("Failed to enable plugin: ${e.message}")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        try {
            shutdownWebEditor()
            shutdownApiSync()
            shutdownPl3xMap()
            saveStorage()

            logger.info("Pl3xLands disabled successfully")
        } catch (e: Exception) {
            logger.severe("Error during plugin disable: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadConfig() {
        try {
            config = ConfigManager.load(dataFolder.toPath(), "config.yaml")
            RegionValidator.setConstraints(config.validation)
            logger.info("Config loaded successfully")
        } catch (e: Exception) {
            logger.severe("Failed to load configuration")
            throw e
        }
    }

    private fun initializeStorage() {
        try {
            storage = when (config.storage.type) {
                StorageType.JSON -> {
                    logger.info("Using JSON file storage")
                    JsonFileStorage(
                        file = File(dataFolder, config.storage.filename),
                        gson = gson,
                        logger = logger,
                        enableValidation = config.validation.enabled
                    )
                }
                StorageType.BINARY -> {
                    logger.info("Using BINARY file storage")
                    BinaryFileStorage(
                        file = File(dataFolder, config.storage.filename),
                        logger = logger,
                        enableValidation = config.validation.enabled
                    )
                }
            }

            if (config.storage.autoSave) {
                server.scheduler.runTaskTimerAsynchronously(
                    this,
                    Runnable {
                        try {
                            storage?.save()
                        } catch (e: Exception) {
                            logger.warning("Auto-save failed: ${e.message}")
                        }
                    },
                    config.storage.autoSaveInterval * 20L,
                    config.storage.autoSaveInterval * 20L
                )
                logger.info("Auto-save enabled (interval: ${config.storage.autoSaveInterval}s)")
            }
        } catch (e: Exception) {
            logger.severe("Failed to initialize storage")
            throw e
        }
    }

    private fun initializePl3xMap() {
        try {
            val storageInstance = storage ?: throw IllegalStateException("Storage not initialized")

            pl3xMapHook = Pl3xMapHook(storageInstance, logger, config.rendering)

            val manifest = storageInstance.load()
            if (manifest != null) {
                pl3xMapHook?.updateMap()
                logger.info("Loaded ${manifest.regions.size} regions from storage")
            } else {
                logger.info("No existing regions found")
            }

            Pl3xMap.api().worldRegistry.forEach { world ->
                pl3xMapHook?.register(world)
            }

            logger.info("Pl3xMap integration initialized")
        } catch (e: Exception) {
            logger.severe("Failed to initialize Pl3xMap integration")
            throw e
        }
    }

    private fun initializeApiSync() {
        if (!config.api.enable) {
            logger.info("API sync disabled in configuration")
            return
        }

        try {
            val storageInstance = storage ?: throw IllegalStateException("Storage not initialized")
            val hookInstance = pl3xMapHook ?: throw IllegalStateException("Pl3xMap hook not initialized")

            syncManager = RegionSyncManager(config.api, storageInstance, hookInstance, logger)
            syncManager?.start()

            logger.info("API sync manager enabled (polling every ${config.api.refreshInterval}s)")
        } catch (e: Exception) {
            logger.severe("Failed to initialize API sync manager")
            throw e
        }
    }

    private fun initializeWebEditor() {
        if (!config.editor.enable) {
            logger.info("Web editor disabled in configuration")
            return
        }

        try {
            val webDir = File(dataFolder, "web")
            if (!webDir.exists()) {
                webDir.mkdirs()
                saveResource("web/index.html", false)
                saveResource("web/style.css", false)
                saveResource("web/script.js", false)
                logger.info("Web editor files extracted")
            }

            val storageInstance = storage ?: throw IllegalStateException("Storage not initialized")
            val hookInstance = pl3xMapHook ?: throw IllegalStateException("Pl3xMap hook not initialized")

            webEditor = WebServer(this, config.editor.port, hookInstance, storageInstance)
            webEditor?.start()

            logger.info("Web editor enabled on port ${config.editor.port}")
        } catch (e: Exception) {
            logger.severe("Failed to initialize web editor")
            throw e
        }
    }

    private fun shutdownWebEditor() {
        if (config.editor.enable && webEditor != null) {
            try {
                webEditor?.stop()
                logger.info("Web editor shut down")
            } catch (e: Exception) {
                logger.warning("Error shutting down web editor: ${e.message}")
            }
        }
    }

    private fun shutdownApiSync() {
        if (config.api.enable && syncManager != null) {
            try {
                syncManager?.shutdown()
                logger.info("API sync manager shut down")
            } catch (e: Exception) {
                logger.warning("Error shutting down sync manager: ${e.message}")
            }
        }
    }

    private fun shutdownPl3xMap() {
        try {
            Pl3xMap.api().worldRegistry.forEach { world ->
                pl3xMapHook?.unregister(world)
            }
            logger.info("Pl3xMap integration shut down")
        } catch (e: Exception) {
            logger.warning("Error shutting down Pl3xMap integration: ${e.message}")
        }
    }

    private fun saveStorage() {
        try {
            storage?.save()
            logger.info("Storage saved successfully")
        } catch (e: Exception) {
            logger.severe("Failed to save storage: ${e.message}")
            e.printStackTrace()
        }
    }
}
