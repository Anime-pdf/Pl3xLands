package me.animepdf.pl3xLands.storage

import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.dto.RegionManifest

interface RegionStorage {
    fun save(manifest: RegionManifest? = null)
    fun load(fresh: Boolean = false): RegionManifest?
    fun addRegions(regions: Array<Region>, save: Boolean = true)
    fun addChunksToRegion(regionId: String, chunks: Array<Long>, save: Boolean = true)
}