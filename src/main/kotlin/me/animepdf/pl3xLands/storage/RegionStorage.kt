package me.animepdf.pl3xLands.storage

import me.animepdf.pl3xLands.dto.RegionManifest

interface RegionStorage {
    fun save(manifest: RegionManifest)
    fun load(): RegionManifest?
}