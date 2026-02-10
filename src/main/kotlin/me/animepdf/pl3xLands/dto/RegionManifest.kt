package me.animepdf.pl3xLands.dto

data class RegionManifest(
    val hash: String,
    val timestamp: Long,
    val regions: MutableList<Region>
)
