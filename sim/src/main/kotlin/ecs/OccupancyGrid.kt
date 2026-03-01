package starkraft.sim.ecs

class OccupancyGrid(val width: Int, val height: Int) {
    private val dynamicCounts = IntArray(width * height)
    private val staticCounts = IntArray(width * height)

    fun clearDynamic() {
        java.util.Arrays.fill(dynamicCounts, 0)
    }

    fun addDynamic(x: Int, y: Int) {
        if (!inBounds(x, y)) return
        dynamicCounts[y * width + x]++
    }

    fun addStatic(x: Int, y: Int) {
        if (!inBounds(x, y)) return
        staticCounts[y * width + x]++
    }

    fun removeStatic(x: Int, y: Int) {
        if (!inBounds(x, y)) return
        val idx = y * width + x
        if (staticCounts[idx] > 0) staticCounts[idx]--
    }

    fun isStaticBlocked(x: Int, y: Int): Boolean {
        if (!inBounds(x, y)) return true
        return staticCounts[y * width + x] > 0
    }

    fun isBlocked(x: Int, y: Int): Boolean {
        if (!inBounds(x, y)) return true
        val idx = y * width + x
        return staticCounts[idx] + dynamicCounts[idx] > 0
    }

    fun isBlockedAllowing(x: Int, y: Int, allowX: Int, allowY: Int): Boolean {
        if (!inBounds(x, y)) return true
        val idx = y * width + x
        if (x == allowX && y == allowY) return staticCounts[idx] > 0
        return staticCounts[idx] + dynamicCounts[idx] > 0
    }

    private fun inBounds(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < width && y < height
}
