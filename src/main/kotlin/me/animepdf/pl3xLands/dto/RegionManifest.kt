package me.animepdf.pl3xLands.dto

data class RegionManifest(
    val hash: String,
    val timestamp: Long,
    val regions: Array<Region>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegionManifest

        if (timestamp != other.timestamp) return false
        if (hash != other.hash) return false
        if (!regions.contentEquals(other.regions)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + hash.hashCode()
        result = 31 * result + regions.contentHashCode()
        return result
    }
}
