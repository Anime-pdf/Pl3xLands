package me.animepdf.pl3xLands.util

import net.pl3x.map.core.util.Colors
import kotlin.math.absoluteValue

object ColorGenerator {
    fun colorForPlayer(nickname: String, alpha: Int = 150): Int {
        val hash = nickname.hashCode().absoluteValue
        val hue = (hash % 360).toFloat()
        val saturation = 0.85f
        val value = 0.95f

        return hsvToArgb(
            alpha = alpha,
            hue = hue,
            saturation = saturation,
            value = value
        )
    }

    fun hsvToArgb(
        alpha: Int,
        hue: Float,
        saturation: Float,
        value: Float
    ): Int {
        val c = value * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60) % 2 - 1))
        val m = value - c

        val (r1, g1, b1) = when {
            hue < 60 -> Triple(c, x, 0f)
            hue < 120 -> Triple(x, c, 0f)
            hue < 180 -> Triple(0f, c, x)
            hue < 240 -> Triple(0f, x, c)
            hue < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255).toInt()
        val g = ((g1 + m) * 255).toInt()
        val b = ((b1 + m) * 255).toInt()

        return Colors.argb(alpha, r, g, b)
    }
}