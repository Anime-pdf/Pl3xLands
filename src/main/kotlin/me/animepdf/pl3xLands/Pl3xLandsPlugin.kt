package me.animepdf.pl3xLands

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.animepdf.pl3xLands.config.GeneralConfig
import me.animepdf.pl3xLands.config.StorageType
import me.animepdf.pl3xLands.hook.Pl3xMapHook
import me.animepdf.pl3xLands.http.RegionSyncManager
import me.animepdf.pl3xLands.storage.JsonFileStorage
import me.animepdf.pl3xLands.storage.RegionStorage
import me.animepdf.pl3xLands.storage.SQLiteStorage
import me.animepdf.pl3xLands.util.ConfigManager
import net.pl3x.map.core.Pl3xMap
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class Pl3xLandsPlugin : JavaPlugin() {

    private var gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    private lateinit var config: GeneralConfig
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

        syncManager = RegionSyncManager(config.api, storage, pl3xMapHook)
        syncManager.start()

        logger.info("Pl3xLands enabled successfully!")
    }

    override fun onDisable() {
        syncManager.shutdown()

        Pl3xMap.api().worldRegistry.forEach(pl3xMapHook::unregister);

        logger.info("Pl3xLands disabled!")
    }

    private fun loadConfig() {
        try {
            config = ConfigManager.load<GeneralConfig>(dataFolder.toPath(), "config.yaml")
            logger.info("Config loaded successfully!")
        } catch (e: Exception) {
            logger.severe("Failed to load config! Disabling plugin")
            e.printStackTrace()
            server.pluginManager.disablePlugin(this)
            return
        }
    }
}
