package me.animepdf.pl3xLands.util

object ChunkPacker {
    fun pack(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    fun getX(packed: Long): Int = (packed shr 32).toInt()
    fun getZ(packed: Long): Int = packed.toInt()
}