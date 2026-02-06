package me.animepdf.pl3xLands.storage

import com.google.gson.Gson
import me.animepdf.pl3xLands.dto.RegionManifest
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class JsonFileStorage(private val file: File, private val gson: Gson) : RegionStorage {
    override fun save(manifest: RegionManifest) {
        FileWriter(file).use { writer ->
            gson.toJson(manifest, writer)
        }
    }

    override fun load(): RegionManifest? {
        if (!file.exists()) return null
        return FileReader(file).use { reader ->
            gson.fromJson(reader, RegionManifest::class.java)
        }
    }
}