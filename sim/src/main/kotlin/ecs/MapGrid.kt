package starkraft.sim.ecs

class MapGrid(val width: Int, val height: Int) {
    private val passable = BooleanArray(width * height) { true }
    private val cost = FloatArray(width * height) { 1f }

    fun inBounds(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < width && y < height

    fun isPassable(x: Int, y: Int): Boolean =
        inBounds(x, y) && passable[y * width + x]

    fun setBlocked(x: Int, y: Int, blocked: Boolean) {
        if (!inBounds(x, y)) return
        passable[y * width + x] = !blocked
    }

    fun cost(x: Int, y: Int): Float =
        if (inBounds(x, y)) cost[y * width + x] else Float.POSITIVE_INFINITY

    fun setCost(x: Int, y: Int, value: Float) {
        if (!inBounds(x, y)) return
        cost[y * width + x] = value
    }

    fun isBlocked(x: Int, y: Int): Boolean =
        inBounds(x, y) && !passable[y * width + x]
}
