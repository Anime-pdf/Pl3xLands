package me.animepdf.pl3xLands

import me.animepdf.pl3xLands.config.GeneralConfig
import me.animepdf.pl3xLands.util.ConfigManager
import org.bukkit.plugin.java.JavaPlugin

class Pl3xLandsPlugin : JavaPlugin() {

    private lateinit var config: GeneralConfig

    override fun onEnable() {
        loadConfig()

        server.worlds.first().loadedChunks.first().chunkKey

        logger.info("Pl3xLands enabled successfully!")
    }

    override fun onDisable() {
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
