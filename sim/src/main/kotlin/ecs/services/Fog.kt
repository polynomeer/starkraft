package starkraft.sim.ecs.services

import kotlin.math.*

class FogGrid(val width: Int, val height: Int, val tileSize: Float) {
    private val stamp = IntArray(width * height)
    private var current = 1
    private var visibleCount = 0

    fun clear() {
        current++
        visibleCount = 0
        if (current == Int.MAX_VALUE) {
            java.util.Arrays.fill(stamp, 0)
            current = 1
        }
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
                if (dx * dx + dy * dy <= r2) {
                    val idx = ty * width + tx
                    if (stamp[idx] != current) {
                        stamp[idx] = current
                        visibleCount++
                    }
                }
            }
        }
    }

    fun visibleCount(): Int {
        return visibleCount
    }
}
