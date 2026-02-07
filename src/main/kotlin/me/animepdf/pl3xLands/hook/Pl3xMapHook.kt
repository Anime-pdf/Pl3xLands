package me.animepdf.pl3xLands.hook

import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.util.ChunkPacker
import me.animepdf.pl3xLands.util.ColorGenerator
import net.pl3x.map.core.Pl3xMap
import net.pl3x.map.core.event.EventHandler
import net.pl3x.map.core.event.EventListener
import net.pl3x.map.core.event.world.WorldLoadedEvent
import net.pl3x.map.core.event.world.WorldUnloadedEvent
import net.pl3x.map.core.markers.Point
import net.pl3x.map.core.markers.marker.Marker
import net.pl3x.map.core.markers.marker.MultiPolygon
import net.pl3x.map.core.markers.marker.Polygon
import net.pl3x.map.core.markers.marker.Polyline
import net.pl3x.map.core.markers.option.Options
import net.pl3x.map.core.markers.option.Tooltip
import net.pl3x.map.core.util.Colors
import net.pl3x.map.core.world.World
import kotlin.math.absoluteValue

class Pl3xMapHook : EventListener {

    val markers = HashMap<String, ArrayList<Marker<MultiPolygon>>>()

    init {
        Pl3xMap.api().eventRegistry.register(this)
    }

    fun register(world: World) {
        world.layerRegistry.register(Pl3xMapLayer(world, this, "lands"))
    }

    fun unregister(world: World) {
        world.layerRegistry.unregister(Pl3xMapLayer.KEY)
    }

    fun updateMap(regions: Array<Region>) {
        markers.clear()

        for (region in regions) {
            val chunkPolygons = region.chunks.distinct().map { packedChunk ->
                chunkToPolygon(packedChunk)
            }

            val regionKey = "lands_region_${region.id}"
            val marker = MultiPolygon.of(regionKey, chunkPolygons)

            val popupContent = """
                <div style="text-align:center;">
                    <b>${region.name}</b><br/>
                    <i>${region.description}</i><br/>
                    <hr/>
                    Владелец: ${region.owner}<br/>
                    Контакты: ${region.contact}
                </div>
            """.trimIndent()

            val options = Options.builder()
                .strokeColor(ColorGenerator.colorForPlayer(region.owner, 255))
                .fillColor(ColorGenerator.colorForPlayer(region.owner))
                .tooltip(Tooltip(region.name))
                .tooltipSticky(true)
                .popupContent(popupContent)
                .build()

            marker.options = options

            markers.getOrPut(region.world) { ArrayList() }.add(marker)
        }

        markers.forEach { (world, markers) ->  println("Added ${markers.size} markers for $world") }
    }

    private fun chunkToPolygon(packed: Long): Polygon {
        val minX = ChunkPacker.getX(packed)
        val minZ = ChunkPacker.getZ(packed)
        val maxX = minX + 16
        val maxZ = minZ + 16

        return Polygon.of(
            "lands_polygon_$packed",
            Polyline.of(
                "lands_polyline_$packed",
                Point.of(minX, minZ),
                Point.of(maxX, minZ),
                Point.of(maxX, maxZ),
                Point.of(minX, maxZ),
                Point.of(minX, minZ)
            )
        )
    }

    @EventHandler
    fun onWorldLoaded(event: WorldLoadedEvent) {
        register(event.world)
    }

    @EventHandler
    fun onWorldUnloaded(event: WorldUnloadedEvent) {
        unregister(event.world)
    }
}