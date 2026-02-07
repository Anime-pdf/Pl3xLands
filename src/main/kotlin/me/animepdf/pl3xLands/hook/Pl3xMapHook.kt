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
import net.pl3x.map.core.world.World
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.PathIterator

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
            val marker = chunksToMultiPolygon(region.id, region.chunks.distinct())

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

    private fun chunksToMultiPolygon(
        regionId: String,
        chunks: Collection<Long>
    ): MultiPolygon {

        val area = chunksToArea(chunks)
        val polygons = areaToPolygons(area, "lands_region_$regionId")

        return MultiPolygon.of(
            "lands_region_$regionId",
            polygons
        )
    }

    private fun areaToPolygons(area: Area, baseId: String): List<Polygon> {
        val it = area.getPathIterator(null)
        val coords = DoubleArray(6)

        val rings = mutableListOf<List<Point>>()
        var currentRing = mutableListOf<Point>()

        while (!it.isDone) {
            when (it.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> {
                    if (currentRing.isNotEmpty()) {
                        rings.add(currentRing)
                        currentRing = mutableListOf()
                    }
                    currentRing.add(Point.of(coords[0], coords[1]))
                }

                PathIterator.SEG_LINETO -> {
                    currentRing.add(Point.of(coords[0], coords[1]))
                }

                PathIterator.SEG_CLOSE -> {
                    if (currentRing.isNotEmpty()) {
                        currentRing.add(currentRing.first())
                        rings.add(currentRing)
                        currentRing = mutableListOf()
                    }
                }
            }
            it.next()
        }

        return rings.mapIndexed { index, points ->
            Polygon.of(
                "${baseId}_$index",
                Polyline.of(
                    "${baseId}_ring_$index",
                    *points.toTypedArray()
                )
            )
        }
    }

    private fun chunksToArea(chunks: Collection<Long>): Area {
        val area = Area()
        for (packed in chunks) {
            area.add(chunkToArea(packed))
        }
        return area
    }

    private fun chunkToArea(packed: Long): Area {
        val minX = ChunkPacker.getX(packed)
        val minZ = ChunkPacker.getZ(packed)

        return Area(Rectangle(minX, minZ, 16, 16))
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