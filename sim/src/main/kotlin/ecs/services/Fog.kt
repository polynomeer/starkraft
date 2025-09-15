package starkraft.sim.ecs.services

import kotlin.math.*

class FogGrid(val width: Int, val height: Int, val tileSize: Float) {
    private val visible = Array(height) { BooleanArray(width) }

    fun clear() {
        for (y in 0 until height) java.util.Arrays.fill(visible[y], false)
    }

    fun markVisible(cx: Float, cy: Float, range: Float) {
        val r = ceil(range / tileSize).toInt()
        val cxT = floor(cx / tileSize).toInt()
        val cyT = floor(cy / tileSize).toInt()
        val r2 = r * r
        val minX = max(0, cxT - r);
        val maxX = min(width - 1, cxT + r)
        val minY = max(0, cyT - r);
        val maxY = min(height - 1, cyT + r)
        for (ty in minY..maxY) {
            val dy = ty - cyT
            for (tx in minX..maxX) {
                val dx = tx - cxT
                if (dx * dx + dy * dy <= r2) visible[ty][tx] = true
            }
        }
    }

    fun visibleCount(): Int {
        var c = 0
        for (y in 0 until height) for (x in 0 until width) if (visible[y][x]) c++
        return c
    }
}
