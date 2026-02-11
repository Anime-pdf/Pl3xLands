package me.animepdf.pl3xLands.hook

import me.animepdf.pl3xLands.config.RenderingConfig
import net.pl3x.map.core.markers.layer.WorldLayer
import net.pl3x.map.core.markers.marker.Marker
import net.pl3x.map.core.world.World

class Pl3xMapLayer(
    world: World,
    val hook: Pl3xMapHook,
    renderingConfig: RenderingConfig
) : WorldLayer(KEY, world, { renderingConfig.layerName }) {

    init {
        setShowControls(renderingConfig.layerShowControls)
        setDefaultHidden(renderingConfig.layerDefaultHidden)
        setUpdateInterval(renderingConfig.layerUpdateInterval)
        setPriority(renderingConfig.layerPriority)
        setZIndex(renderingConfig.layerZIndex)
    }

    companion object {
        const val KEY = "pl3xmaplands"
    }

    override fun getMarkers(): Collection<Marker<*>> {
        return hook.getMarkersForWorld(world.name)
    }
}