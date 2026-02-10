package me.animepdf.pl3xLands.storage

import com.google.gson.Gson
import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.dto.RegionManifest
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class JsonFileStorage(private val file: File, private val gson: Gson) : RegionStorage {
    var manifest: RegionManifest? = null

    override fun save(manifest: RegionManifest?) {
        if (manifest != null) this.manifest = manifest
        if(this.manifest == null) return
        FileWriter(file).use { writer ->
            gson.toJson(this.manifest, writer)
        }
    }

    override fun load(fresh: Boolean): RegionManifest? {
        if (!file.exists()) return null
        if (!fresh && manifest != null) return manifest
        return FileReader(file).use { reader ->
            gson.fromJson(reader, RegionManifest::class.java)
        }
    }

    override fun addRegions(regions: Array<Region>, save: Boolean) {
        if (manifest == null) load()
        manifest?.regions += regions
        if (save && manifest != null)
            save(manifest!!)
    }

    override fun addChunksToRegion(regionId: String, chunks: Array<Long>, save: Boolean) {
        if (manifest == null) load()
        manifest?.regions?.find { it.id == regionId }?.chunks += chunks
        if (save && manifest != null)
            save(manifest!!)
    }
}