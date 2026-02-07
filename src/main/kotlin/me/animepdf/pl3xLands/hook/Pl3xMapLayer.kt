package me.animepdf.pl3xLands.hook

import net.pl3x.map.core.markers.layer.WorldLayer
import net.pl3x.map.core.markers.marker.Marker
import net.pl3x.map.core.world.World

class Pl3xMapLayer(
    world: World,
    val hook: Pl3xMapHook,
    label: String
) : WorldLayer(KEY, world, { label }) {

    init {
        setShowControls(true)
        setDefaultHidden(false)
        setUpdateInterval(30)
        setPriority(10)
        setZIndex(10)
    }

    companion object {
        const val KEY = "pl3xmaplands"
    }

    override fun getMarkers(): Collection<Marker<*>> {
        return hook.markers.getOrElse(world.name) { ArrayList() }
    }
}