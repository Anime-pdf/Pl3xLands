package me.animepdf.pl3xLands.util

import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.notExists

object ConfigManager {
    inline fun <reified T : Any> load(
        folder: Path,
        filename: String,
        crossinline defaultFactory: () -> T
    ): T {
        if (folder.notExists()) {
            Files.createDirectories(folder)
        }
        val file = folder.resolve(filename)

        val loader = YamlConfigurationLoader.builder()
            .path(file)
            .nodeStyle(NodeStyle.BLOCK)
            .indent(4)
            .build()

        val node = loader.load()
        val config = node.get(T::class.java) ?: defaultFactory()

        node.set(T::class.java, config)
        loader.save(node)

        return config
    }

    inline fun <reified T : Any> load(folder: Path, filename: String): T {
        return load(folder, filename) { T::class.java.getConstructor().newInstance() }
    }
}