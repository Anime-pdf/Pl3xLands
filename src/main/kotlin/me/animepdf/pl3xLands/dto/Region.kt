package me.animepdf.pl3xLands.dto

data class Region(
    val id: String,
    val name: String,
    val description: String,
    val owner: String,
    val contact: String,
    val world: String,
    val chunks: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Region

        if (id != other.id) return false
        if (name != other.name) return false
        if (description != other.description) return false
        if (owner != other.owner) return false
        if (contact != other.contact) return false
        if (world != other.world) return false
        if (!chunks.contentEquals(other.chunks)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + contact.hashCode()
        result = 31 * result + world.hashCode()
        result = 31 * result + chunks.contentHashCode()
        return result
    }
}
