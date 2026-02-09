package me.animepdf.pl3xLands

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.animepdf.pl3xLands.config.GeneralConfig
import me.animepdf.pl3xLands.config.StorageType
import me.animepdf.pl3xLands.hook.Pl3xMapHook
import me.animepdf.pl3xLands.http.RegionSyncManager
import me.animepdf.pl3xLands.http.WebServer
import me.animepdf.pl3xLands.storage.JsonFileStorage
import me.animepdf.pl3xLands.storage.RegionStorage
import me.animepdf.pl3xLands.storage.SQLiteStorage
import me.animepdf.pl3xLands.util.ConfigManager
import net.pl3x.map.core.Pl3xMap
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Pl3xLandsPlugin : JavaPlugin() {
    lateinit var config: GeneralConfig

    private var gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private lateinit var webEditor: WebServer
    private lateinit var syncManager: RegionSyncManager
    private lateinit var pl3xMapHook: Pl3xMapHook

    override fun onEnable() {
        loadConfig()

        val storage: RegionStorage = when (config.storage.type) {
            StorageType.JSON -> JsonFileStorage(File(dataFolder, config.storage.filename), gson)
            StorageType.SQLITE -> SQLiteStorage(File(dataFolder, config.storage.filename).absolutePath, gson)
        }

        pl3xMapHook = Pl3xMapHook()
        val manifest = storage.load()
        if (manifest != null) {
            pl3xMapHook.updateMap(manifest.regions)
        }

        Pl3xMap.api().worldRegistry.forEach(pl3xMapHook::register);

        if (config.api.enable) {
            syncManager = RegionSyncManager(config.api, storage, pl3xMapHook)
            syncManager.start()
            logger.info("API Sync Manager enabled")
        }

        if (config.editor.enable) {
            val webDir = File(dataFolder, "web")
            if (!webDir.exists()) {
                webDir.mkdir()
                saveResource("web/index.html", false)
                saveResource("web/style.css", false)
                saveResource("web/script.js", false)
                logger.info("Web Editor files unpacked")
            }

            webEditor = WebServer(this, config.editor.port, pl3xMapHook, storage)
            webEditor.start()
            logger.info("Web Editor enabled")
        }

        logger.info("Pl3xLands enabled")
    }

    override fun onDisable() {
        if (config.api.enable) {
            syncManager.shutdown()
            logger.info("API Sync Manager disabled")
        }

        if (config.editor.enable) {
            webEditor.stop()
            logger.info("Web Editor disabled")
        }

        Pl3xMap.api().worldRegistry.forEach(pl3xMapHook::unregister);

        logger.info("Pl3xLands disabled")
    }

    private fun loadConfig() {
        try {
            config = ConfigManager.load<GeneralConfig>(dataFolder.toPath(), "config.yaml")
            logger.info("Config loaded successfully")
        } catch (e: Exception) {
            logger.severe("Failed to load config. Disabling plugin")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
            return
        }
    }
}
