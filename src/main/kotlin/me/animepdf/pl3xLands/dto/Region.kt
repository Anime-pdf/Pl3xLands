package me.animepdf.pl3xLands.dto

data class Region(
    val id: String,
    val name: String,
    val description: String,
    val owner: String,
    val contact: String,
    val world: String,
    val chunks: MutableList<Long>
)
