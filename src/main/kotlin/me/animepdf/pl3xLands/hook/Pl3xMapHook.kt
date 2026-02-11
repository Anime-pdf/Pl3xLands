package me.animepdf.pl3xLands.hook

import me.animepdf.pl3xLands.config.ColorScheme
import me.animepdf.pl3xLands.config.RenderingConfig
import me.animepdf.pl3xLands.dto.Region
import me.animepdf.pl3xLands.storage.RegionStorage
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
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.PathIterator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Logger

class Pl3xMapHook(
    val storage: RegionStorage,
    private val logger: Logger,
    private val renderingConfig: RenderingConfig
) : EventListener {

    private val markers = ConcurrentHashMap<String, CopyOnWriteArrayList<Marker<MultiPolygon>>>()
    private val updateLock = ReentrantReadWriteLock()

    init {
        Pl3xMap.api().eventRegistry.register(this)
    }

    fun register(world: World) {
        world.layerRegistry.register(Pl3xMapLayer(world, this, renderingConfig))
    }

    fun unregister(world: World) {
        world.layerRegistry.unregister(Pl3xMapLayer.KEY)
    }

    fun updateMap() {
        updateLock.writeLock().lock()
        try {
            markers.clear()

            val regionsManifest = storage.load() ?: run {
                logger.warning("No regions manifest found")
                return
            }

            var successCount = 0
            var failCount = 0

            for (region in regionsManifest.regions) {
                try {
                    if (region.chunks.isEmpty()) {
                        logger.warning("Region ${region.id} has no chunks, skipping")
                        failCount++
                        continue
                    }

                    val marker = chunksToMultiPolygon(region.id, region.chunks.distinct())

                    val popupContent = buildPopupContent(region)

                    val strokeColor = when (renderingConfig.colorScheme) {
                        ColorScheme.FIXED_COLOR -> Colors.setAlpha(renderingConfig.strokeOpacity, Colors.fromHex(renderingConfig.fixedColor))
                        ColorScheme.PLAYER_HASH -> ColorGenerator.colorForPlayer(region.owner, renderingConfig.strokeOpacity)
                    }
                    val fillColor = when (renderingConfig.colorScheme) {
                        ColorScheme.FIXED_COLOR -> Colors.setAlpha(renderingConfig.fillOpacity, Colors.fromHex(renderingConfig.fixedColor))
                        ColorScheme.PLAYER_HASH -> ColorGenerator.colorForPlayer(region.owner, renderingConfig.fillOpacity)
                    }

                    val options = Options.builder()
                        .strokeWeight(renderingConfig.strokeWidth)
                        .strokeColor(strokeColor)
                        .fillColor(fillColor)
                        .tooltip(if (renderingConfig.enableTooltips) Tooltip(region.name) else null)
                        .tooltipSticky(renderingConfig.tooltipSticky)
                        .popupContent(if (renderingConfig.enableTooltips) popupContent else null)
                        .build()

                    marker.options = options
                    markers.getOrPut(region.world) { CopyOnWriteArrayList() }.add(marker)
                    successCount++

                } catch (e: Exception) {
                    logger.severe("Failed to create marker for region ${region.id}: ${e.message}")
                    e.printStackTrace()
                    failCount++
                }
            }

            logger.info("Map update complete: $successCount regions loaded, $failCount failed")
            markers.forEach { (world, worldMarkers) ->
                logger.info("World '$world': ${worldMarkers.size} regions")
            }
        } catch (e: Exception) {
            logger.severe("Critical error during map update: ${e.message}")
            e.printStackTrace()
        } finally {
            updateLock.writeLock().unlock()
        }
    }

    fun getMarkersForWorld(worldName: String): Collection<Marker<*>> {
        updateLock.readLock().lock()
        try {
            return markers.getOrDefault(worldName, CopyOnWriteArrayList())
        } finally {
            updateLock.readLock().unlock()
        }
    }

    fun getAllMarkers(): Map<String, List<Marker<MultiPolygon>>> {
        updateLock.readLock().lock()
        try {
            // snapshot
            return markers.mapValues { it.value.toList() }
        } finally {
            updateLock.readLock().unlock()
        }
    }

    private fun buildPopupContent(region: Region): String {
        return """
            <div style="text-align:center;">
                <b>${escapeHtml(region.name)}</b><br/>
                <i>${escapeHtml(region.description)}</i><br/>
                <hr/>
                Owner: ${escapeHtml(region.owner)}<br/>
                Contact: ${escapeHtml(region.contact)}
            </div>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
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